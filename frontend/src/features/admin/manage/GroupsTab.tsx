import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { FormField } from "@/components/common/FormField";
import { EmptyState, ErrorState, LoadingState } from "@/components/common/states";
import {
  assignTeamsToGroup,
  createGroup,
  getGroupTeams,
} from "@/features/tournaments/api/tournaments";
import type { Group, Match, Team, Tournament } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { matchesInGroup } from "@/features/tournaments/format";
import { GroupStandings } from "@/features/tournaments/components/GroupStandings";
import { GenerateFixtureDialog } from "./GenerateFixtureDialog";
import { SelectableTeamChip } from "./TeamChip";
import { isConfigurable, toggleId } from "./status";

function CreateGroupDialog({
  tournamentId,
  nextOrder,
  onSaved,
}: {
  tournamentId: string;
  nextOrder: number;
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!name.trim()) {
      setError("Ponele un nombre al grupo, por ejemplo “Grupo A”.");
      return;
    }

    setSubmitting(true);
    try {
      const group = await createGroup(tournamentId, {
        name: name.trim(),
        displayOrder: nextOrder,
      });
      setOpen(false);
      setName("");
      toast.success("Grupo creado", {
        description: `Ya podés sumarle equipos a ${group.name}.`,
      });
      onSaved();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "No se pudo crear el grupo. Volvé a intentarlo."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="secondary">
          <Plus aria-hidden="true" />
          Crear grupo
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Crear grupo</DialogTitle>
          <DialogDescription>
            Después de crearlo vas a poder asignarle equipos del torneo.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <FormField label="Nombre del grupo" required error={error ?? undefined}>
            {(field) => (
              <Input
                {...field}
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Grupo A"
              />
            )}
          </FormField>
          <Button type="submit" disabled={submitting}>
            {submitting ? "Creando…" : "Crear grupo"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function AssignTeamsDialog({
  group,
  availableTeams,
  onSaved,
}: {
  group: Group;
  availableTeams: Team[];
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (selectedIds.length === 0) return;

    setSubmitting(true);
    try {
      await assignTeamsToGroup(group.id, selectedIds);
      setOpen(false);
      toast.success("Equipos asignados", {
        description: `Sumaste ${selectedIds.length} ${selectedIds.length === 1 ? "equipo" : "equipos"} a ${group.name}.`,
      });
      setSelectedIds([]);
      onSaved();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "No se pudieron asignar los equipos."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">
          <Plus aria-hidden="true" />
          Agregar equipos
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Agregar equipos a {group.name}</DialogTitle>
          <DialogDescription>
            Solo aparecen los equipos del torneo que todavía no están en ningún grupo.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-wrap gap-2">
            {availableTeams.map((team) => (
              <SelectableTeamChip
                key={team.id}
                team={team}
                selected={selectedIds.includes(team.id)}
                onToggle={() => setSelectedIds((ids) => toggleId(ids, team.id))}
              />
            ))}
          </div>

          {error && (
            <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" disabled={submitting || selectedIds.length === 0}>
            {submitting ? "Asignando…" : "Asignar equipos"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function GroupsTab({
  tournament,
  groups,
  matches,
  tournamentTeams,
  onChanged,
}: {
  tournament: Tournament;
  groups: Group[];
  matches: Match[];
  tournamentTeams: Team[];
  onChanged: () => void;
}) {
  const [teamsByGroup, setTeamsByGroup] = useState<Record<string, Team[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadGroupTeams = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all(groups.map((group) => getGroupTeams(group.id).then((teams) => [group.id, teams] as const)))
      .then((entries) => setTeamsByGroup(Object.fromEntries(entries)))
      .catch((requestError) =>
        setError(apiErrorMessage(requestError, "No pudimos cargar los equipos de cada grupo.")),
      )
      .finally(() => setLoading(false));
  }, [groups]);

  useEffect(() => {
    loadGroupTeams();
  }, [loadGroupTeams]);

  const canEdit = isConfigurable(tournament.status);
  // A team belongs to at most one group (ALREADY_IN_GROUP), so offer only free ones.
  const groupedIds = new Set(Object.values(teamsByGroup).flat().map((team) => team.id));
  const freeTeams = tournamentTeams.filter((team) => !groupedIds.has(team.id));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Grupos</CardTitle>
        <CardDescription>
          Cada equipo puede estar en un solo grupo. La tabla suma los partidos finalizados.
        </CardDescription>
        {canEdit && (
          <CardAction>
            <CreateGroupDialog
              tournamentId={tournament.id}
              nextOrder={groups.length + 1}
              onSaved={onChanged}
            />
          </CardAction>
        )}
      </CardHeader>
      <CardContent>
        {groups.length === 0 ? (
          <EmptyState
            title="Todavía no hay grupos"
            description="Creá grupos para organizar los equipos del torneo."
          />
        ) : loading ? (
          <LoadingState label="Cargando grupos…" />
        ) : error ? (
          <ErrorState title="No pudimos cargar los grupos" description={error} onRetry={loadGroupTeams} />
        ) : (
          <div className="grid gap-3 xl:grid-cols-2">
            {groups.map((group) => {
              const teams = teamsByGroup[group.id] ?? [];
              const groupMatches = matchesInGroup(matches, group.id);
              return (
                <div key={group.id} className="rounded-lg border border-border bg-muted p-4">
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                    <h4 className="text-base font-bold">
                      {group.name}
                      <span className="ml-2 text-sm font-semibold text-muted-foreground">
                        {teams.length} {teams.length === 1 ? "equipo" : "equipos"}
                      </span>
                    </h4>
                    <div className="flex flex-wrap gap-1">
                      {canEdit && teams.length >= 2 && (
                        <GenerateFixtureDialog
                          tournamentId={tournament.id}
                          group={group}
                          teams={teams}
                          groupMatches={groupMatches}
                          onCreated={onChanged}
                        />
                      )}
                      {canEdit && freeTeams.length > 0 && (
                        <AssignTeamsDialog
                          group={group}
                          availableTeams={freeTeams}
                          onSaved={() => {
                            loadGroupTeams();
                            onChanged();
                          }}
                        />
                      )}
                    </div>
                  </div>
                  {teams.length === 0 ? (
                    <p className="text-sm text-muted-foreground">Sin equipos asignados.</p>
                  ) : (
                    <div className="rounded-md border border-border bg-card">
                      <GroupStandings
                        groupName={group.name}
                        teams={teams}
                        matches={groupMatches}
                      />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
