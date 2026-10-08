import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { FormField } from "@/components/common/FormField";
import type { MatchStatus } from "@/components/common/StatusBadge";
import { createMatch, getGroupTeams, updateMatch } from "@/features/tournaments/api/tournaments";
import type { Group, Match, Phase, Team } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { phaseLabel, teamCode } from "@/features/tournaments/format";

/** One select for "where is this match played": a group or a phase, never both. */
type Location = `group:${string}` | `phase:${string}` | "";

function toLocation(match?: Match): Location {
  if (match?.groupId) return `group:${match.groupId}`;
  if (match?.phaseId) return `phase:${match.phaseId}`;
  return "";
}

function toDateTimeLocal(iso: string): string {
  const date = new Date(iso);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function teamOption(team: Team): string {
  const code = teamCode(team);
  return code ? `${team.name} (${code})` : team.name;
}

const EDITABLE_STATUSES: { value: MatchStatus; label: string }[] = [
  { value: "SCHEDULED", label: "Programado" },
  { value: "IN_PROGRESS", label: "En juego" },
  { value: "POSTPONED", label: "Pospuesto" },
  { value: "CANCELLED", label: "Cancelado" },
];

/**
 * Create a match, or edit one when `match` is given. Every field is a choice of
 * existing entities (group or phase, teams); nobody types an id.
 */
export function MatchFormDialog({
  tournamentId,
  match,
  groups,
  phases,
  tournamentTeams,
  trigger,
  onSaved,
}: {
  tournamentId: string;
  match?: Match;
  groups: Group[];
  phases: Phase[];
  tournamentTeams: Team[];
  trigger: ReactNode;
  onSaved: () => void;
}) {
  const editing = Boolean(match);
  const [open, setOpen] = useState(false);
  const [location, setLocation] = useState<Location>(toLocation(match));
  const [homeTeamId, setHomeTeamId] = useState(match?.homeTeam.id ?? "");
  const [awayTeamId, setAwayTeamId] = useState(match?.awayTeam.id ?? "");
  const [startTime, setStartTime] = useState(match ? toDateTimeLocal(match.startTime) : "");
  const [status, setStatus] = useState<MatchStatus>(match?.status ?? "SCHEDULED");
  const [groupTeams, setGroupTeams] = useState<Team[] | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);

  function handleOpenChange(next: boolean) {
    if (next) {
      setLocation(toLocation(match));
      setHomeTeamId(match?.homeTeam.id ?? "");
      setAwayTeamId(match?.awayTeam.id ?? "");
      setStartTime(match ? toDateTimeLocal(match.startTime) : "");
      setStatus(match?.status ?? "SCHEDULED");
      setError(null);
    }
    setOpen(next);
  }

  // A group match must be between teams of that group (NOT_IN_GROUP), so only
  // offer those once a group is chosen.
  const groupId = location.startsWith("group:") ? location.slice("group:".length) : null;
  useEffect(() => {
    if (!open || !groupId) {
      setGroupTeams(null);
      return;
    }
    let cancelled = false;
    getGroupTeams(groupId)
      .then((teams) => {
        if (!cancelled) setGroupTeams(teams);
      })
      .catch(() => {
        if (!cancelled) setGroupTeams(null);
      });
    return () => {
      cancelled = true;
    };
  }, [open, groupId]);

  const teamOptions = groupTeams ?? tournamentTeams;

  async function save() {
    const [kind, id] = location.split(":") as ["group" | "phase", string];
    const where = kind === "group" ? { groupId: id } : { phaseId: id };
    const base = {
      homeTeamId,
      awayTeamId,
      startTime: new Date(startTime).toISOString(),
    };

    setSubmitting(true);
    try {
      if (match) {
        await updateMatch(match.id, { ...where, ...base, status });
      } else {
        await createMatch(tournamentId, { ...where, ...base });
      }
      const home = teamOptions.find((team) => team.id === homeTeamId)?.name ?? "Local";
      const away = teamOptions.find((team) => team.id === awayTeamId)?.name ?? "Visitante";
      toast.success(editing ? "Cambios guardados" : "Partido creado", {
        description: `${home} – ${away}`,
      });
      setOpen(false);
      onSaved();
    } catch (requestError) {
      setError(
        apiErrorMessage(
          requestError,
          editing ? "No se pudo guardar el partido." : "No se pudo crear el partido.",
        ),
      );
    } finally {
      setSubmitting(false);
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!location) {
      setError("Elegí en qué grupo o fase se juega el partido.");
      return;
    }
    if (!homeTeamId || !awayTeamId || !startTime) {
      setError("Elegí los dos equipos y la fecha y hora del partido.");
      return;
    }
    if (homeTeamId === awayTeamId) {
      setError("El local y el visitante tienen que ser equipos distintos.");
      return;
    }
    if (match && status === "CANCELLED" && match.status !== "CANCELLED") {
      setConfirmCancel(true);
      return;
    }
    void save();
  }

  return (
    <>
      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogTrigger asChild>{trigger}</DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? "Editar partido" : "Crear partido"}</DialogTitle>
            <DialogDescription>
              {editing
                ? "Cambiá el grupo o la fase, los equipos, el horario o el estado."
                : "Los participantes pueden pronosticarlo hasta el inicio del partido."}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
            <FormField label="Grupo o fase" required>
              {(field) => (
                <Select
                  value={location}
                  onValueChange={(value) => setLocation(value as Location)}
                >
                  <SelectTrigger id={field.id} className="h-11 w-full">
                    <SelectValue placeholder="Elegí un grupo o una fase" />
                  </SelectTrigger>
                  <SelectContent>
                    {groups.length > 0 && (
                      <SelectGroup>
                        <SelectLabel>Grupos</SelectLabel>
                        {groups.map((group) => (
                          <SelectItem key={group.id} value={`group:${group.id}`}>
                            {group.name}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    )}
                    {phases.length > 0 && (
                      <SelectGroup>
                        <SelectLabel>Fases</SelectLabel>
                        {phases.map((phase) => (
                          <SelectItem key={phase.id} value={`phase:${phase.id}`}>
                            {phaseLabel(phase)}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    )}
                  </SelectContent>
                </Select>
              )}
            </FormField>

            <div className="grid grid-cols-2 gap-4">
              <FormField label="Local" required>
                {(field) => (
                  <Select value={homeTeamId} onValueChange={setHomeTeamId}>
                    <SelectTrigger id={field.id} className="h-11 w-full">
                      <SelectValue placeholder="Elegí un equipo" />
                    </SelectTrigger>
                    <SelectContent>
                      {teamOptions.map((team) => (
                        <SelectItem key={team.id} value={team.id} disabled={team.id === awayTeamId}>
                          {teamOption(team)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </FormField>

              <FormField label="Visitante" required>
                {(field) => (
                  <Select value={awayTeamId} onValueChange={setAwayTeamId}>
                    <SelectTrigger id={field.id} className="h-11 w-full">
                      <SelectValue placeholder="Elegí un equipo" />
                    </SelectTrigger>
                    <SelectContent>
                      {teamOptions.map((team) => (
                        <SelectItem key={team.id} value={team.id} disabled={team.id === homeTeamId}>
                          {teamOption(team)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </FormField>
            </div>
            {groupTeams !== null && groupTeams.length < 2 && (
              <p className="text-xs text-muted-foreground">
                Este grupo tiene menos de dos equipos. Sumale equipos desde la pestaña Grupos.
              </p>
            )}

            <FormField label="Fecha y hora" required>
              {(field) => (
                <Input
                  {...field}
                  type="datetime-local"
                  value={startTime}
                  onChange={(event) => setStartTime(event.target.value)}
                />
              )}
            </FormField>

            {editing && (
              <FormField label="Estado" required>
                {(field) => (
                  <Select value={status} onValueChange={(value) => setStatus(value as MatchStatus)}>
                    <SelectTrigger id={field.id} className="h-11 w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {EDITABLE_STATUSES.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </FormField>
            )}

            {error && (
              <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
                {error}
              </p>
            )}

            <Button type="submit" disabled={submitting}>
              {submitting ? "Guardando…" : editing ? "Guardar cambios" : "Crear partido"}
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      {match && (
        <ConfirmDialog
          open={confirmCancel}
          onOpenChange={setConfirmCancel}
          title="¿Cancelar este partido?"
          description={
            <>
              <p>
                {match.homeTeam.name} – {match.awayTeam.name} pasa a Cancelado y se cierran sus
                pronósticos.
              </p>
              <p>Un partido cancelado ya no se puede editar ni recibir resultado.</p>
            </>
          }
          confirmLabel="Cancelar partido"
          pendingLabel="Cancelando…"
          destructive
          onConfirm={save}
        />
      )}
    </>
  );
}
