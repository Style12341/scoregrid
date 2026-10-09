import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { usePageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { MatchStatusBadge, PredictionLockBadge } from "@/components/common/StatusBadge";
import { LoadingState, EmptyState, ErrorState } from "@/components/common/states";
import { getMatch, getTournament } from "@/features/tournaments/api/tournaments";
import type { Match, Tournament } from "@/features/tournaments/types/tournament";
import { formatKickoff, hasResult } from "@/features/tournaments/format";
import { getMyPredictionPoints, getMyPredictions, type Prediction } from "./api";

type PredictionRow = {
  prediction: Prediction;
  match: Match | null;
};

const REFRESH_INTERVAL_MS = 5000;

export function MyPredictionsPage() {
  usePageHeader("Mis pronósticos", "Lo que pronosticaste en cada torneo.");

  const [predictions, setPredictions] = useState<PredictionRow[]>([]);
  const [pointsByPrediction, setPointsByPrediction] = useState<Map<string, number> | null>(null);
  const [tournaments, setTournaments] = useState<Record<string, Tournament>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  // Tournament names do not change while polling: fetch each one once.
  const requestedTournaments = useRef(new Set<string>());

  useEffect(() => {
    let disposed = false;

    async function loadTournamentNames(ids: string[]) {
      const missing = ids.filter((id) => !requestedTournaments.current.has(id));
      missing.forEach((id) => requestedTournaments.current.add(id));
      const loaded = await Promise.all(missing.map((id) => getTournament(id).catch(() => null)));
      if (disposed) return;
      const found = loaded.filter((t): t is Tournament => t !== null);
      if (found.length > 0) {
        setTournaments((current) => ({
          ...current,
          ...Object.fromEntries(found.map((t) => [t.id, t])),
        }));
      }
    }

    async function refresh() {
      try {
        const nextPredictions = await getMyPredictions();
        const [nextRows, nextScores] = await Promise.all([
          Promise.all(
            nextPredictions.map(async (prediction) => {
              try {
                return { prediction, match: await getMatch(prediction.matchId) };
              } catch {
                return { prediction, match: null };
              }
            }),
          ),
          getMyPredictionPoints().catch(() => null),
        ]);

        if (!disposed) {
          setPredictions(nextRows);
          setPointsByPrediction(nextScores === null
            ? null
            : new Map(nextScores.map((score) => [score.predictionId, score.points])));
          setError(null);
          setLoading(false);
          void loadTournamentNames([...new Set(nextPredictions.map((p) => p.tournamentId))]);
        }
      } catch {
        if (!disposed) {
          setError("Puede que el servicio de pronósticos no esté disponible. Volvé a intentarlo en unos segundos.");
          setLoading(false);
        }
      }
    }

    void refresh();
    const interval = window.setInterval(() => void refresh(), REFRESH_INTERVAL_MS);

    return () => {
      disposed = true;
      window.clearInterval(interval);
    };
  }, [retry]);

  if (loading) return <LoadingState label="Cargando tus pronósticos…" />;
  if (error && predictions.length === 0) {
    return (
      <ErrorState
        title="No pudimos cargar tus pronósticos"
        description={error}
        onRetry={() => {
          setLoading(true);
          setRetry((r) => r + 1);
        }}
      />
    );
  }
  if (predictions.length === 0) {
    return (
      <EmptyState
        title="Todavía no hiciste ningún pronóstico"
        description="Entrá a un torneo activo, inscribite y elegí un partido del fixture."
        action={
          <Button asChild>
            <Link to="/tournaments">Ver torneos</Link>
          </Button>
        }
      />
    );
  }

  return (
    <Card className="px-0 py-0">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="pl-5">Partido</TableHead>
            <TableHead>Torneo</TableHead>
            <TableHead>Tu pronóstico</TableHead>
            <TableHead>Resultado</TableHead>
            <TableHead className="text-right">Puntos</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead className="pr-5 text-right">
              <span className="sr-only">Acciones</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {predictions.map(({ prediction, match }) => {
            const tournament = tournaments[prediction.tournamentId];
            const points = pointsByPrediction?.get(prediction.id);
            const matchName = match
              ? `${match.homeTeam.name} – ${match.awayTeam.name}`
              : "Partido no disponible";
            return (
              <TableRow key={prediction.id}>
                <TableCell className="pl-5">
                  <div className="flex flex-col">
                    <span className="font-bold">{matchName}</span>
                    {match && (
                      <span className="text-xs text-muted-foreground">
                        {formatKickoff(match.startTime)}
                      </span>
                    )}
                  </div>
                </TableCell>
                <TableCell className="text-sm">
                  {tournament ? (
                    <Link
                      to={`/tournaments/${tournament.id}`}
                      className="rounded-sm hover:text-primary hover:underline"
                    >
                      {tournament.name}
                    </Link>
                  ) : (
                    <span className="text-muted-foreground">…</span>
                  )}
                </TableCell>
                <TableCell className="text-base font-extrabold tabular-nums">
                  {prediction.homeScore} – {prediction.awayScore}
                </TableCell>
                <TableCell className="text-sm tabular-nums text-muted-foreground">
                  {match && hasResult(match) ? (
                    <span className="font-bold text-foreground">
                      {match.homeScore} – {match.awayScore}
                    </span>
                  ) : (
                    "Pendiente"
                  )}
                </TableCell>
                <TableCell className="text-right text-sm font-bold tabular-nums">
                  {pointsByPrediction === null ? (
                    <span className="text-muted-foreground">No disponible</span>
                  ) : points === undefined ? (
                    <span className="text-muted-foreground">Pendiente</span>
                  ) : (
                    points
                  )}
                </TableCell>
                <TableCell>
                  {match ? (
                    match.status === "SCHEDULED" ? (
                      <PredictionLockBadge open={match.predictionsOpen} />
                    ) : (
                      <MatchStatusBadge status={match.status} />
                    )
                  ) : (
                    <span className="text-sm text-muted-foreground">Sin datos</span>
                  )}
                </TableCell>
                <TableCell className="pr-5 text-right">
                  {match && (
                    <Button asChild size="sm" variant={match.predictionsOpen ? "outline" : "ghost"}>
                      <Link
                        to={`/matches/${match.id}/predict`}
                        aria-label={`${match.predictionsOpen ? "Editar" : "Ver"} pronóstico de ${matchName}`}
                      >
                        {match.predictionsOpen ? "Editar" : "Ver"}
                      </Link>
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </Card>
  );
}
