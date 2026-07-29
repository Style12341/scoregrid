import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Calendar } from "lucide-react";
import { usePageHeader } from "@/components/layout/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { PageTitle } from "@/components/common/PageTitle";
import { TournamentStatusBadge } from "@/components/common/StatusBadge";
import { LoadingState, EmptyState, ErrorState } from "@/components/common/states";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { listTournaments } from "../api/tournaments";
import type { Tournament, TournamentStatus } from "../types/tournament";
import { TOURNAMENT_STATUS } from "../types/tournament";

const STATUS_FILTERS: { label: string; value: string | null }[] = [
  { label: "Todos", value: null },
  { label: "Activos", value: TOURNAMENT_STATUS.ACTIVE },
  { label: "Abiertos", value: TOURNAMENT_STATUS.DRAFT },
  { label: "Finalizados", value: TOURNAMENT_STATUS.FINISHED },
];

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("es-AR", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

function TournamentCard({ tournament }: { tournament: Tournament }) {
  return (
    <Card className="gap-3">
      <CardContent className="flex flex-col gap-2">
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-base font-bold">{tournament.name}</h3>
          <TournamentStatusBadge status={tournament.status as TournamentStatus} />
        </div>

        {tournament.description && (
          <p className="text-sm text-muted-foreground line-clamp-2">
            {tournament.description}
          </p>
        )}

        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Calendar className="size-3.5" aria-hidden="true" />
          <span>
            {formatDate(tournament.startDate)} – {formatDate(tournament.endDate)}
          </span>
        </div>

        <Button asChild variant="secondary" size="sm" className="mt-2 self-start">
          <Link to={`/tournaments/${tournament.id}`}>Ver torneo</Link>
        </Button>
      </CardContent>
    </Card>
  );
}

export function TournamentListPage() {
  usePageHeader("Torneos");

  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);

  const load = (status?: string | null) => {
    setLoading(true);
    setError(null);
    listTournaments(status ?? undefined, 0, 50)
      .then((page) => setTournaments(page.content))
      .catch(() => setError("No se pudieron cargar los torneos."))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(statusFilter);
  }, [statusFilter]);

  if (error) {
    return (
      <ErrorState title="Error" description={error} onRetry={() => load(statusFilter)} />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <PageTitle
          title="Torneos"
          action={
            <Tabs
              value={statusFilter ?? "all"}
              onValueChange={(v) => setStatusFilter(v === "all" ? null : v)}
            >
              <TabsList variant="pill">
                {STATUS_FILTERS.map((f) => (
                  <TabsTrigger key={f.value ?? "all"} value={f.value ?? "all"}>
                    {f.label}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
          }
        />
      </div>

      {loading ? (
        <LoadingState label="Cargando torneos…" />
      ) : tournaments.length === 0 ? (
        <EmptyState
          title="No hay torneos"
          description={
            statusFilter
              ? "No se encontraron torneos con ese estado."
              : "Todavía no se creó ningún torneo. Un administrador puede dar de alta el primero."
          }
          action={
            statusFilter ? (
              <Button variant="secondary" onClick={() => setStatusFilter(null)}>
                Mostrar todos
              </Button>
            ) : undefined
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {tournaments.map((t) => (
            <TournamentCard key={t.id} tournament={t} />
          ))}
        </div>
      )}
    </div>
  );
}
