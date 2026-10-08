import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { CheckCircle2, Lock } from "lucide-react";
import { toast } from "sonner";
import { usePageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { PredictionLockBadge, MatchStatusBadge } from "@/components/common/StatusBadge";
import { LoadingState, ErrorState } from "@/components/common/states";
import { ScoreInput } from "@/components/common/ScoreInput";
import { parseScore } from "@/components/common/score";
import { toApiError } from "@/lib/api";
import { getMatch, getTournament, listMatches } from "@/features/tournaments/api/tournaments";
import type { Match, TeamRef, Tournament } from "@/features/tournaments/types/tournament";
import { apiErrorMessage, isNotFoundError } from "@/features/tournaments/errors";
import { byKickoff, formatKickoff, hasResult, teamCode } from "@/features/tournaments/format";
import {
  createPrediction,
  getMyPredictionForMatch,
  getMyPredictions,
  updatePrediction,
  type Prediction,
} from "./api";

/** The earliest open match in this tournament the user has not predicted yet. */
function nextUnpredicted(
  matches: Match[],
  predictedIds: Set<string>,
  currentId: string,
): Match | null {
  return (
    [...matches]
      .sort(byKickoff)
      .find((m) => m.id !== currentId && m.predictionsOpen && !predictedIds.has(m.id)) ?? null
  );
}

function ScoreColumn({
  id,
  team,
  value,
  onChange,
  disabled,
}: {
  id: string;
  team: TeamRef;
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}) {
  const code = teamCode(team);
  return (
    <div className="flex flex-1 flex-col items-center gap-3 text-center">
      <Label htmlFor={id} className="flex flex-col items-center gap-0.5">
        <span className="text-lg font-bold">{team.name}</span>
        {code && <span className="text-xs font-semibold text-muted-foreground">{code}</span>}
        <span className="sr-only">, goles</span>
      </Label>
      <ScoreInput id={id} value={value} onChange={onChange} teamName={team.name} disabled={disabled} />
    </div>
  );
}

export function PredictionPage() {
  const { matchId } = useParams<{ matchId: string }>();

  const [match, setMatch] = useState<Match | null>(null);
  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [existing, setExisting] = useState<Prediction | null>(null);
  const [siblings, setSiblings] = useState<Match[]>([]);
  const [predictedIds, setPredictedIds] = useState<Set<string>>(new Set());
  const [homeScore, setHomeScore] = useState("");
  const [awayScore, setAwayScore] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (!matchId) return;
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    setSaved(false);
    setError(null);
    setExisting(null);
    setHomeScore("");
    setAwayScore("");

    Promise.all([getMatch(matchId), getMyPredictionForMatch(matchId)])
      .then(async ([m, p]) => {
        if (cancelled) return;
        setMatch(m);
        if (p) {
          setExisting(p);
          setHomeScore(String(p.homeScore));
          setAwayScore(String(p.awayScore));
        }
        // Context for the breadcrumb and the "next match" shortcut. Not
        // essential: if it fails, the form still works.
        const [t, ms, ps] = await Promise.all([
          getTournament(m.tournamentId).catch(() => null),
          listMatches(m.tournamentId).catch(() => [] as Match[]),
          getMyPredictions(m.tournamentId, 0, 200).catch(() => [] as Prediction[]),
        ]);
        if (cancelled) return;
        setTournament(t);
        setSiblings(ms);
        setPredictedIds(new Set(ps.map((prediction) => prediction.matchId)));
      })
      .catch((requestError) => {
        if (cancelled) return;
        setLoadError({
          message: apiErrorMessage(requestError, "No pudimos cargar el partido. Volvé a intentarlo."),
          notFound: isNotFoundError(requestError),
        });
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [matchId, retry]);

  const tournamentName = tournament?.name ?? "Torneo";
  const fixturePath = match ? `/tournaments/${match.tournamentId}` : "/tournaments";
  usePageHeader({
    title: "Pronosticar",
    subtitle: tournament?.name,
    breadcrumbs: [
      { label: "Torneos", to: "/tournaments" },
      { label: tournamentName, to: fixturePath },
      { label: "Pronosticar" },
    ],
  });

  if (loading) return <LoadingState label="Cargando partido…" />;
  if (loadError) {
    return loadError.notFound ? (
      <ErrorState
        title="No encontramos este partido"
        description="Volvé al fixture del torneo y elegí otro partido."
      />
    ) : (
      <ErrorState
        title="No pudimos cargar el partido"
        description={loadError.message}
        onRetry={() => setRetry((r) => r + 1)}
      />
    );
  }
  if (!match || !matchId) return null;

  const isLocked = !match.predictionsOpen;
  const next = nextUnpredicted(siblings, predictedIds, match.id);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const hs = parseScore(homeScore);
    const as = parseScore(awayScore);
    if (hs === null || as === null || !match) {
      setError("Ingresá los goles de los dos equipos, entre 0 y 99.");
      return;
    }

    setSubmitting(true);
    try {
      const prediction = existing
        ? await updatePrediction(existing.id, { homeScore: hs, awayScore: as })
        : await createPrediction({ matchId: match.id, homeScore: hs, awayScore: as });
      toast.success(existing ? "Pronóstico actualizado" : "Pronóstico enviado", {
        description: `${match.homeTeam.name} ${hs} – ${as} ${match.awayTeam.name}`,
      });
      setExisting(prediction);
      setPredictedIds((ids) => new Set(ids).add(match.id));
      setSaved(true);
    } catch (e) {
      const message = apiErrorMessage(
        e,
        toApiError(e)?.message ?? "No pudimos guardar tu pronóstico. Volvé a intentarlo.",
      );
      setError(message);
      toast.error("No se pudo guardar el pronóstico", { description: message });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-4">
      <Card className="px-5">
        <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
          <div className="flex items-center gap-2">
            <MatchStatusBadge status={match.status} />
            <PredictionLockBadge open={match.predictionsOpen} />
          </div>
          <span>{formatKickoff(match.startTime)}</span>
        </div>

        {isLocked ? (
          <div className="flex flex-col items-center gap-4 py-4 text-center">
            <div className="flex w-full items-center justify-center gap-6">
              <span className="flex-1 text-right text-lg font-bold">{match.homeTeam.name}</span>
              <span className="text-3xl font-extrabold tabular-nums">
                {hasResult(match) ? `${match.homeScore} – ${match.awayScore}` : "vs"}
              </span>
              <span className="flex-1 text-left text-lg font-bold">{match.awayTeam.name}</span>
            </div>
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Lock className="size-4" aria-hidden="true" />
              {existing
                ? `Los pronósticos de este partido están cerrados. Tu pronóstico quedó ${existing.homeScore}–${existing.awayScore}.`
                : "Los pronósticos de este partido están cerrados: empezó o ya terminó."}
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-6 pt-2" noValidate>
            <fieldset className="flex items-start justify-center gap-4 sm:gap-8">
              <legend className="sr-only">
                Tu pronóstico para {match.homeTeam.name} contra {match.awayTeam.name}
              </legend>
              <ScoreColumn
                id="home-score"
                team={match.homeTeam}
                value={homeScore}
                onChange={(value) => {
                  setHomeScore(value);
                  setSaved(false);
                }}
                disabled={submitting}
              />
              <span className="mt-17 text-2xl font-bold text-muted-foreground" aria-hidden="true">
                –
              </span>
              <ScoreColumn
                id="away-score"
                team={match.awayTeam}
                value={awayScore}
                onChange={(value) => {
                  setAwayScore(value);
                  setSaved(false);
                }}
                disabled={submitting}
              />
            </fieldset>

            {error && (
              <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
                {error}
              </p>
            )}

            <Button type="submit" size="block" disabled={submitting}>
              {submitting ? "Guardando…" : existing ? "Actualizar pronóstico" : "Enviar pronóstico"}
            </Button>
            <p className="-mt-3 text-center text-xs text-muted-foreground">
              Podés cambiarlo hasta que empiece el partido.
            </p>
          </form>
        )}
      </Card>

      {saved && (
        <Card className="px-5" role="status">
          <CardContent className="flex flex-col gap-3 px-0">
            <p className="flex items-center gap-2 text-sm font-bold text-success">
              <CheckCircle2 className="size-4" aria-hidden="true" />
              Tu pronóstico quedó guardado. ¿Qué querés hacer ahora?
            </p>
            <div className="flex flex-wrap gap-2">
              {next ? (
                <Button asChild>
                  <Link to={`/matches/${next.id}/predict`}>
                    Siguiente partido sin pronosticar: {next.homeTeam.name} – {next.awayTeam.name}
                  </Link>
                </Button>
              ) : (
                <p className="self-center text-sm text-muted-foreground">
                  No te quedan partidos abiertos por pronosticar en este torneo.
                </p>
              )}
              <Button asChild variant="secondary">
                <Link to={fixturePath}>Volver al fixture</Link>
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {!saved && (
        <Button asChild variant="ghost" className="self-start">
          <Link to={fixturePath}>Volver al fixture</Link>
        </Button>
      )}
    </div>
  );
}
