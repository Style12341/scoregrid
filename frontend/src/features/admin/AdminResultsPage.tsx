import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Settings } from "lucide-react";
import { usePageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { MatchStatusBadge } from "@/components/common/StatusBadge";
import { EmptyState, ErrorState, LoadingState } from "@/components/common/states";
import { listMatches, listTournaments } from "@/features/tournaments/api/tournaments";
import type { Match, Tournament } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { byKickoff, formatKickoff, teamCode } from "@/features/tournaments/format";
import { MatchResultForm } from "./components/MatchResultForm";

function TeamsLabel({ match }: { match: Match }) {
  const home = teamCode(match.homeTeam);
  const away = teamCode(match.awayTeam);
  return (
    <div className="flex min-w-0 flex-col">
      <span className="font-bold">
        {match.homeTeam.name} – {match.awayTeam.name}
      </span>
      <span className="text-xs text-muted-foreground">
        {home && away ? `${home} – ${away} · ` : ""}
        {formatKickoff(match.startTime)}
      </span>
    </div>
  );
}

function MatchRow({ match, onSaved }: { match: Match; onSaved: () => void }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-muted px-4 py-3">
      <div className="flex min-w-0 items-center gap-3">
        <MatchStatusBadge status={match.status} />
        <TeamsLabel match={match} />
      </div>
      <MatchResultForm
        key={`${match.id}-${match.status}-${match.homeScore}-${match.awayScore}`}
        match={match}
        onSaved={onSaved}
      />
    </li>
  );
}

function TournamentMatches({ tournament }: { tournament: Tournament }) {
  const [matches, setMatches] = useState<Match[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    (silent = false) => {
      if (!silent) setLoading(true);
      setError(null);
      listMatches(tournament.id)
        .then((next) => setMatches([...next].sort(byKickoff)))
        .catch((requestError) =>
          setError(apiErrorMessage(requestError, "No pudimos cargar los partidos del torneo.")),
        )
        .finally(() => setLoading(false));
    },
    [tournament.id],
  );

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <LoadingState label="Cargando partidos…" />;
  if (error) {
    return <ErrorState title="No pudimos cargar los partidos" description={error} onRetry={() => load()} />;
  }

  const pending = matches.filter((m) => m.status === "SCHEDULED" || m.status === "IN_PROGRESS");
  const finished = matches.filter((m) => m.status === "FINISHED");
  const refresh = () => load(true);

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Por cargar</CardTitle>
          <CardDescription>
            Al cargar un resultado, el partido pasa a Finalizado y se puntúan todos sus pronósticos.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {pending.length === 0 ? (
            <EmptyState
              title="No hay resultados por cargar"
              description="Todos los partidos programados de este torneo ya tienen resultado."
            />
          ) : (
            <ul className="flex flex-col gap-2">
              {pending.map((match) => (
                <MatchRow key={match.id} match={match} onSaved={refresh} />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {finished.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Finalizados</CardTitle>
            <CardDescription>
              Si un resultado quedó mal, corregilo: los puntajes se recalculan solos.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-2">
              {finished.map((match) => (
                <MatchRow key={match.id} match={match} onSaved={refresh} />
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

/**
 * Pick an active tournament, then load or correct results match by match.
 * The selected tournament lives in the URL (?tournament=) so the page can be
 * linked and survives a reload.
 */
export function AdminResultsPage() {
  usePageHeader("Cargar resultados", "Elegí un torneo activo y cargá el resultado de cada partido.");

  const [searchParams, setSearchParams] = useSearchParams();
  const selectedId = searchParams.get("tournament") ?? "";

  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listTournaments("ACTIVE", 0, 100)
      .then((page) => setTournaments(page.content))
      .catch((requestError) =>
        setError(apiErrorMessage(requestError, "No pudimos cargar los torneos activos.")),
      )
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const select = useCallback(
    (id: string) => setSearchParams({ tournament: id }, { replace: true }),
    [setSearchParams],
  );

  // With a single active tournament there is nothing to choose.
  useEffect(() => {
    if (!selectedId && tournaments.length === 1) select(tournaments[0].id);
  }, [selectedId, tournaments, select]);

  if (loading) return <LoadingState label="Cargando torneos activos…" />;
  if (error) return <ErrorState title="No pudimos cargar los torneos" description={error} onRetry={load} />;
  if (tournaments.length === 0) {
    return (
      <EmptyState
        title="No hay torneos activos"
        description="Solo se cargan resultados en torneos activos. Activá uno desde el Panel admin."
        action={
          <Button asChild variant="secondary">
            <Link to="/admin">Ir al Panel admin</Link>
          </Button>
        }
      />
    );
  }

  const selected = tournaments.find((t) => t.id === selectedId) ?? null;

  return (
    <div className="flex flex-col gap-6">
      <Card className="px-5">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="flex w-full max-w-sm flex-col gap-[7px]">
            <Label htmlFor="results-tournament" className="text-[13px] font-bold text-muted-foreground">
              Torneo
            </Label>
            <Select value={selected?.id ?? ""} onValueChange={select}>
              <SelectTrigger id="results-tournament" className="h-11 w-full bg-card">
                <SelectValue placeholder="Elegí un torneo activo" />
              </SelectTrigger>
              <SelectContent>
                {tournaments.map((t) => (
                  <SelectItem key={t.id} value={t.id}>
                    {t.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {selected && (
            <Button asChild variant="secondary" size="sm">
              <Link to={`/admin/tournaments/${selected.id}`}>
                <Settings aria-hidden="true" />
                Administrar torneo
              </Link>
            </Button>
          )}
        </div>
      </Card>

      {selected ? (
        <TournamentMatches key={selected.id} tournament={selected} />
      ) : (
        <EmptyState
          title="Elegí un torneo"
          description="Vas a ver sus partidos pendientes y los ya finalizados."
        />
      )}
    </div>
  );
}
