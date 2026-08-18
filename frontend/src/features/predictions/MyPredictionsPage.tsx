import { useEffect, useState } from "react";
import { usePageHeader } from "@/components/layout/page-header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { LoadingState, EmptyState, ErrorState } from "@/components/common/states";
import { getMatch } from "@/features/tournaments/api/tournaments";
import type { Match } from "@/features/tournaments/types/tournament";
import { getMyPredictions, type Prediction } from "./api";

type PredictionRow = {
  prediction: Prediction;
  match: Match | null;
};

const REFRESH_INTERVAL_MS = 5000;

export function MyPredictionsPage() {
  usePageHeader("Mis pronósticos");

  const [predictions, setPredictions] = useState<PredictionRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let disposed = false;

    async function refresh() {
      try {
        const nextPredictions = await getMyPredictions();
        const nextRows = await Promise.all(
          nextPredictions.map(async (prediction) => {
            try {
              return { prediction, match: await getMatch(prediction.matchId) };
            } catch {
              return { prediction, match: null };
            }
          }),
        );

        if (!disposed) {
          setPredictions(nextRows);
          setError(null);
          setLoading(false);
        }
      } catch {
        if (!disposed) {
          setError("No se pudieron cargar los pronósticos.");
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
  }, []);

  if (loading) return <LoadingState />;
  if (error) return <ErrorState title="Error" description={error} />;
  if (predictions.length === 0) {
    return (
      <EmptyState
        title="Sin pronósticos"
        description="Todavía no hiciste ningún pronóstico. Buscá un torneo activo y empezá a jugar."
      />
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Mis pronósticos</CardTitle>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Partido</TableHead>
              <TableHead>Pronóstico</TableHead>
              <TableHead>Resultado</TableHead>
              <TableHead>Estado</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {predictions.map(({ prediction, match }) => {
              const matchLabel = match
                ? `${match.homeTeam.shortName || match.homeTeam.name} vs ${match.awayTeam.shortName || match.awayTeam.name}`
                : prediction.matchId;
              const resultLabel = match !== null && match.homeScore !== null && match.awayScore !== null
                ? `${match.homeScore} – ${match.awayScore}`
                : "Pendiente";

              return (
                <TableRow key={prediction.id}>
                  <TableCell className="text-sm">{matchLabel}</TableCell>
                  <TableCell className="text-sm font-bold">
                    {prediction.homeScore} – {prediction.awayScore}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {resultLabel}
                  </TableCell>
                  <TableCell className="text-sm">
                    {match ? (match.predictionsOpen ? "Abierto" : "Cerrado") : "Sin datos"}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
