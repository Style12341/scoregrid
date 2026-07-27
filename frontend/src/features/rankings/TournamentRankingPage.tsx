import { useCallback } from "react";
import { useParams } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";
import { EmptyState, ErrorState, LoadingState } from "@/components/common/states";
import { MetricCard } from "@/components/common/MetricCard";
import { Card } from "@/components/ui/card";
import { usePageHeader } from "@/components/layout/page-header";
import { fetchTournamentRanking } from "./api";
import type { TournamentRankingEntry } from "./api";
import { formatAccuracy, formatCount } from "./format";
import { RankingTable } from "./RankingTable";
import type { RankingColumn } from "./RankingTable";
import { useRanking } from "./useRanking";

const COLUMNS: RankingColumn<TournamentRankingEntry>[] = [
  { key: "points", header: "Puntos", render: (e) => formatCount(e.points) },
  { key: "exactHits", header: "Exactos", render: (e) => formatCount(e.exactHits) },
  { key: "hits", header: "Aciertos", render: (e) => formatCount(e.hits) },
  {
    key: "predictionsScored",
    header: "Pronósticos",
    render: (e) => formatCount(e.predictionsScored),
  },
  { key: "accuracy", header: "Efectividad", render: (e) => formatAccuracy(e.accuracy) },
];

export function TournamentRankingPage() {
  usePageHeader("Ranking del torneo", "Posiciones según los partidos ya finalizados.");

  const { tournamentId } = useParams<{ tournamentId: string }>();
  const { user } = useAuth();

  // Keyed on tournamentId so navigating between tournaments refetches, and the
  // cancellation flag in useRanking discards whichever response loses the race.
  const load = useCallback(
    () => fetchTournamentRanking(tournamentId ?? ""),
    [tournamentId],
  );
  const { entries, status, retry } = useRanking<TournamentRankingEntry>(load);

  const me = entries.find((entry) => entry.userId === user?.id);

  if (!tournamentId) {
    return (
      <ErrorState
        title="Falta el torneo"
        description="La dirección no incluye un torneo válido."
      />
    );
  }

  if (status === "loading") {
    return <LoadingState label="Cargando el ranking del torneo…" />;
  }

  if (status === "error") {
    return (
      <ErrorState
        title="No pudimos cargar el ranking"
        description="El servicio de puntajes no respondió. Volvé a intentarlo en unos segundos."
        onRetry={retry}
      />
    );
  }

  if (entries.length === 0) {
    return (
      <EmptyState
        title="Todavía no hay posiciones"
        description="Las posiciones aparecen cuando se carga el resultado del primer partido del torneo."
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      {me && (
        <div className="grid gap-4 sm:grid-cols-3">
          <MetricCard
            label="Tu posición"
            value={`#${me.position}`}
            note={`De ${formatCount(entries.length)} participantes`}
            tone="muted"
          />
          <MetricCard
            label="Tus puntos"
            value={formatCount(me.points)}
            note={`${formatCount(me.exactHits)} exactos`}
          />
          <MetricCard
            label="Tu efectividad"
            value={formatAccuracy(me.accuracy)}
            note={`${formatCount(me.hits)} de ${formatCount(me.predictionsScored)} pronósticos`}
            tone="muted"
          />
        </div>
      )}

      <Card className="px-0 py-0">
        <RankingTable
          entries={entries}
          columns={COLUMNS}
          currentUserId={user?.id}
          caption="Ranking del torneo, ordenado por puntos."
        />
      </Card>
    </div>
  );
}
