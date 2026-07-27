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
import { getMyPredictions, type Prediction } from "./api";

export function MyPredictionsPage() {
  usePageHeader("Mis pronósticos");

  const [predictions, setPredictions] = useState<Prediction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getMyPredictions()
      .then(setPredictions)
      .catch(() => setError("No se pudieron cargar los pronósticos."))
      .finally(() => setLoading(false));
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
            {predictions.map((p) => (
              <TableRow key={p.id}>
                <TableCell className="text-sm">{p.matchId}</TableCell>
                <TableCell className="text-sm font-bold">
                  {p.homeScore} – {p.awayScore}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">—</TableCell>
                <TableCell className="text-sm">
                  {p.locked ? "Cerrado" : "Abierto"}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
