import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { ErrorState, LoadingState } from "@/components/common/states";
import { assignTeamsToTournament, listTeams } from "@/features/tournaments/api/tournaments";
import type { Team, Tournament } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { SelectableTeamChip, TeamChip } from "./TeamChip";
import { isConfigurable, toggleId } from "./status";

export function TeamsTab({
  tournament,
  tournamentTeams,
  onChanged,
}: {
  tournament: Tournament;
  tournamentTeams: Team[];
  onChanged: () => void;
}) {
  const [catalog, setCatalog] = useState<Team[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loadCatalog = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    listTeams()
      .then(setCatalog)
      .catch((error) =>
        setLoadError(apiErrorMessage(error, "No pudimos cargar el catálogo de equipos.")),
      )
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadCatalog();
  }, [loadCatalog]);

  async function handleAssign() {
    if (selectedIds.length === 0) return;
    setSubmitting(true);
    try {
      await assignTeamsToTournament(tournament.id, selectedIds);
      toast.success("Equipos asignados", {
        description: `Sumaste ${selectedIds.length} ${selectedIds.length === 1 ? "equipo" : "equipos"} a ${tournament.name}.`,
      });
      setSelectedIds([]);
      onChanged();
    } catch (error) {
      toast.error("No se pudieron asignar los equipos", {
        description: apiErrorMessage(error, "Volvé a intentarlo en unos segundos."),
      });
    } finally {
      setSubmitting(false);
    }
  }

  const assignedIds = new Set(tournamentTeams.map((team) => team.id));
  const unassigned = catalog.filter((team) => !assignedIds.has(team.id));
  const canAssign = isConfigurable(tournament.status);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Equipos del torneo</CardTitle>
        <CardDescription>
          Solo los equipos asignados acá pueden formar parte de grupos y partidos.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {tournamentTeams.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {tournamentTeams.map((team) => (
              <TeamChip key={team.id} team={team} />
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            Todavía no hay equipos asignados a este torneo.
          </p>
        )}

        {canAssign && (
          <>
            <Separator />
            {loading ? (
              <LoadingState label="Cargando el catálogo de equipos…" />
            ) : loadError ? (
              <ErrorState
                title="No pudimos cargar el catálogo"
                description={loadError}
                onRetry={loadCatalog}
              />
            ) : unassigned.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                Todos los equipos del catálogo ya están en este torneo. Creá más desde el
                Panel admin.
              </p>
            ) : (
              <div className="flex flex-col gap-3">
                <p className="text-sm font-bold text-muted-foreground">
                  Elegí los equipos que querés sumar
                </p>
                <div className="flex flex-wrap gap-2">
                  {unassigned.map((team) => (
                    <SelectableTeamChip
                      key={team.id}
                      team={team}
                      selected={selectedIds.includes(team.id)}
                      onToggle={() => setSelectedIds((ids) => toggleId(ids, team.id))}
                    />
                  ))}
                </div>
                <Button
                  size="sm"
                  className="self-start"
                  onClick={handleAssign}
                  disabled={submitting || selectedIds.length === 0}
                >
                  {submitting
                    ? "Asignando…"
                    : selectedIds.length > 0
                      ? `Asignar ${selectedIds.length} ${selectedIds.length === 1 ? "equipo" : "equipos"}`
                      : "Asignar equipos"}
                </Button>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
