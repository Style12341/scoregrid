import { useCallback } from "react";
import { useAuth } from "@/auth/AuthContext";
import { EmptyState, ErrorState, LoadingState } from "@/components/common/states";
import { MetricCard } from "@/components/common/MetricCard";
import { Card } from "@/components/ui/card";
import { usePageHeader } from "@/components/layout/page-header";
import { fetchGlobalRanking } from "./api";
import type { GlobalRankingEntry } from "./api";
import { formatAccuracy, formatAverage, formatCount } from "./format";
import { RankingTable } from "./RankingTable";
import type { RankingColumn } from "./RankingTable";
import { useRanking } from "./useRanking";

const COLUMNS: RankingColumn<GlobalRankingEntry>[] = [
  { key: "totalPoints", header: "Puntos", render: (e) => formatCount(e.totalPoints) },
  {
    key: "tournamentsPlayed",
    header: "Torneos",
    render: (e) => formatCount(e.tournamentsPlayed),
  },
  { key: "exactHits", header: "Exactos", render: (e) => formatCount(e.exactHits) },
  { key: "totalHits", header: "Aciertos", render: (e) => formatCount(e.totalHits) },
  {
    key: "predictionsScored",
    header: "Pronósticos",
    render: (e) => formatCount(e.predictionsScored),
  },
  { key: "accuracy", header: "Efectividad", render: (e) => formatAccuracy(e.accuracy) },
  {
    key: "averagePointsPerTournament",
    header: "Prom./torneo",
    render: (e) => formatAverage(e.averagePointsPerTournament),
  },
];

export function GlobalRankingPage() {
  usePageHeader("Ranking global", "Posiciones acumuladas de todos los torneos.");

  const { user } = useAuth();
  const load = useCallback(() => fetchGlobalRanking(), []);
  const { entries, status, retry } = useRanking<GlobalRankingEntry>(load);

  const me = entries.find((entry) => entry.userId === user?.id);

  if (status === "loading") {
    return <LoadingState label="Cargando el ranking global…" />;
  }

  if (status === "error") {
    return (
      <ErrorState
        title="No pudimos cargar el ranking global"
        description="El servicio de puntajes no respondió. Volvé a intentarlo en unos segundos."
        onRetry={retry}
      />
    );
  }

  if (entries.length === 0) {
    return (
      <EmptyState
        title="Todavía no hay posiciones"
        description="El ranking global se arma con los partidos ya finalizados. En cuanto se cargue el primer resultado, vas a verlo acá."
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      {me && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard
            label="Tu posición"
            value={`#${me.position}`}
            note="Ranking global"
            tone="muted"
          />
          <MetricCard
            label="Tus puntos"
            value={formatCount(me.totalPoints)}
            note={`${formatCount(me.exactHits)} exactos`}
          />
          <MetricCard
            label="Tu efectividad"
            value={formatAccuracy(me.accuracy)}
            note={`${formatCount(me.totalHits)} de ${formatCount(me.predictionsScored)} pronósticos`}
            tone="muted"
          />
          <MetricCard
            label="Promedio por torneo"
            value={formatAverage(me.averagePointsPerTournament)}
            note={`${formatCount(me.tournamentsPlayed)} torneos jugados`}
            tone="muted"
          />
        </div>
      )}

      <Card className="px-0 py-0">
        <RankingTable
          entries={entries}
          columns={COLUMNS}
          currentUserId={user?.id}
          caption="Ranking global de participantes, ordenado por puntos totales."
        />
      </Card>
    </div>
  );
}
