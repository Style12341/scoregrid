import { useEffect, useState, useCallback, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { Plus } from "lucide-react";
import { usePageHeader } from "@/components/layout/page-header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  MatchStatusBadge,
  TournamentStatusBadge,
  type MatchStatus,
} from "@/components/common/StatusBadge";
import { FormField } from "@/components/common/FormField";
import { LoadingState, EmptyState, ErrorState } from "@/components/common/states";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import {
  getTournament,
  listGroups,
  createGroup,
  assignTeamsToGroup,
  getGroupTeams,
  listPhases,
  createPhase,
  listMatches,
  createMatch,
  updateMatch,
  loadResult,
  listTeams,
  assignTeamsToTournament,
  getTournamentTeams,
} from "@/features/tournaments/api/tournaments";
import type {
  Tournament,
  Group,
  Phase,
  Match,
  Team,
  CreateMatchInput,
  UpdateMatchInput,
  PhaseType,
  TournamentStatus,
} from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";

// ── Helpers ───────────────────────────────────────────────────────────────

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("es-AR", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function isConfigurable(status: string): boolean {
  return status === "DRAFT" || status === "ACTIVE";
}

function teamLabel(team: Team): string {
  return team.shortName || team.name;
}

function toDateTimeLocal(iso: string): string {
  const date = new Date(iso);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

// ── Team Assignment Section ───────────────────────────────────────────────

function TeamAssignmentSection({
  tournamentId,
  tournamentStatus,
  onChanged,
}: {
  tournamentId: string;
  tournamentStatus: string;
  onChanged?: () => void;
}) {
  const [allTeams, setAllTeams] = useState<Team[]>([]);
  const [assignedTeams, setAssignedTeams] = useState<Team[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all([listTeams(), getTournamentTeams(tournamentId)])
      .then(([all, assigned]) => {
        setAllTeams(all);
        setAssignedTeams(assigned);
        setSelectedIds([]);
      })
      .catch(() => setError("No se pudieron cargar los equipos."))
      .finally(() => setLoading(false));
  }, [tournamentId]);

  useEffect(() => {
    load();
  }, [load]);

  const toggleTeam = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  };

  async function handleAssign() {
    if (selectedIds.length === 0) return;
    setSubmitting(true);
    setError(null);
    try {
      await assignTeamsToTournament(tournamentId, selectedIds);
      load();
      onChanged?.();
    } catch (e) {
      setError(apiErrorMessage(e, "No se pudieron asignar los equipos."));
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <LoadingState label="Cargando equipos…" />;
  if (error) return <ErrorState title="Error" description={error} onRetry={load} />;

  const unassigned = allTeams.filter((t) => !assignedTeams.find((a) => a.id === t.id));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Equipos del torneo</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {assignedTeams.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {assignedTeams.map((t) => (
              <span
                key={t.id}
                className="rounded-full border border-border bg-muted px-3 py-1 text-sm font-medium"
              >
                {teamLabel(t)}
              </span>
            ))}
          </div>
        )}

        {assignedTeams.length === 0 && (
          <p className="text-sm text-muted-foreground">
            Todavía no hay equipos asignados a este torneo.
          </p>
        )}

        {isConfigurable(tournamentStatus) && unassigned.length > 0 && (
          <>
            <Separator />
            <div>
              <p className="mb-2 text-sm font-bold text-muted-foreground">
                Asignar más equipos:
              </p>
              <div className="flex flex-wrap gap-2">
                {unassigned.map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => toggleTeam(t.id)}
                    className={`rounded-full border px-3 py-1 text-sm font-medium transition-colors ${
                      selectedIds.includes(t.id)
                        ? "border-primary bg-primary text-primary-foreground"
                        : "border-border bg-muted hover:border-primary/50"
                    }`}
                  >
                    {teamLabel(t)}
                  </button>
                ))}
              </div>
            </div>

            <Button
              size="sm"
              onClick={handleAssign}
              disabled={submitting || selectedIds.length === 0}
            >
              {submitting ? "Asignando…" : "Asignar seleccionados"}
            </Button>

            {error && <p className="text-sm font-bold text-destructive">{error}</p>}
          </>
        )}
      </CardContent>
    </Card>
  );
}

// ── Groups Section ────────────────────────────────────────────────────────

function GroupsSection({
  tournamentId,
  tournamentStatus,
  tournamentTeams,
  onChanged,
}: {
  tournamentId: string;
  tournamentStatus: string;
  tournamentTeams: Team[];
  onChanged?: () => void;
}) {
  const [groups, setGroups] = useState<Group[]>([]);
  const [teamsByGroup, setTeamsByGroup] = useState<Record<string, Team[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listGroups(tournamentId)
      .then(async (g) => {
        setGroups(g);
        const teamMap: Record<string, Team[]> = {};
        await Promise.all(
          g.map((group) =>
            getGroupTeams(group.id)
              .then((teams) => {
                teamMap[group.id] = teams;
              }),
          ),
        );
        setTeamsByGroup(teamMap);
      })
      .catch((error) => setError(apiErrorMessage(error, "No se pudieron cargar los grupos.")))
      .finally(() => setLoading(false));
  }, [tournamentId]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Grupos</CardTitle>
          {isConfigurable(tournamentStatus) && (
            <CreateGroupDialog
              tournamentId={tournamentId}
              onSaved={() => {
                load();
                onChanged?.();
              }}
            />
          )}
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {loading ? (
          <LoadingState label="Cargando grupos…" />
        ) : error ? (
          <ErrorState title="Error" description={error} onRetry={load} />
        ) : groups.length === 0 ? (
          <EmptyState
            title="Sin grupos"
            description="Creá grupos para organizar los equipos del torneo."
          />
        ) : (
          groups.map((group) => {
            const teams = teamsByGroup[group.id] ?? [];
            const assignedIds = teams.map((t) => t.id);
            const availableTeams = tournamentTeams.filter(
              (t) => !assignedIds.includes(t.id),
            );

            return (
              <div key={group.id} className="rounded-lg border border-border p-4">
                <div className="mb-3 flex items-center justify-between">
                  <h4 className="text-sm font-bold">{group.name}</h4>
                  {isConfigurable(tournamentStatus) && availableTeams.length > 0 && (
                    <AssignTeamsDialog
                      groupId={group.id}
                      groupName={group.name}
                      availableTeams={availableTeams}
                      onSaved={() => {
                        load();
                        onChanged?.();
                      }}
                    />
                  )}
                </div>

                {teams.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    Sin equipos asignados.
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {teams.map((team) => (
                      <span
                        key={team.id}
                        className="rounded-full border border-border bg-muted px-3 py-1 text-sm font-medium"
                      >
                        {teamLabel(team)}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            );
          })
        )}
      </CardContent>
    </Card>
  );
}

// ── Create Group Dialog ───────────────────────────────────────────────────

function CreateGroupDialog({
  tournamentId,
  onSaved,
}: {
  tournamentId: string;
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim()) return;

    setSubmitting(true);
    try {
      await createGroup(tournamentId, { name: name.trim(), displayOrder: 0 });
      setOpen(false);
      setName("");
      onSaved();
    } catch (e) {
      setError(apiErrorMessage(e, "No se pudo crear el grupo."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="secondary">
          <Plus className="size-4" aria-hidden="true" />
          Crear grupo
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Crear grupo</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <FormField label="Nombre del grupo" required>
            {(field) => (
              <Input
                {...field}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Grupo A"
              />
            )}
          </FormField>
          {error && (
            <p className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" disabled={submitting}>
            {submitting ? "Creando…" : "Crear grupo"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── Assign Teams to Group Dialog ──────────────────────────────────────────

function AssignTeamsDialog({
  groupId,
  groupName,
  availableTeams,
  onSaved,
}: {
  groupId: string;
  groupName: string;
  availableTeams: Team[];
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  };

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (selectedIds.length === 0) return;

    setSubmitting(true);
    try {
      await assignTeamsToGroup(groupId, selectedIds);
      setOpen(false);
      setSelectedIds([]);
      onSaved();
    } catch (e) {
      setError(apiErrorMessage(e, "No se pudieron asignar los equipos."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="xs" variant="ghost">
          + Agregar equipos
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Asignar equipos a {groupName}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-wrap gap-2">
            {availableTeams.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => toggle(t.id)}
                className={`rounded-full border px-3 py-1 text-sm font-medium transition-colors ${
                  selectedIds.includes(t.id)
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-muted hover:border-primary/50"
                }`}
              >
                {t.shortName}
              </button>
            ))}
          </div>

          {error && (
            <p className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
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

// ── Phases Section ────────────────────────────────────────────────────────

const PHASE_TYPES: { value: PhaseType; label: string }[] = [
  { value: "GROUP_STAGE", label: "Fase de grupos" },
  { value: "ROUND_OF_16", label: "Octavos de final" },
  { value: "QUARTER_FINAL", label: "Cuartos de final" },
  { value: "SEMI_FINAL", label: "Semifinal" },
  { value: "THIRD_PLACE", label: "Tercer puesto" },
  { value: "FINAL", label: "Final" },
];

function PhasesSection({
  tournamentId,
  tournamentStatus,
  onChanged,
}: {
  tournamentId: string;
  tournamentStatus: string;
  onChanged?: () => void;
}) {
  const [phases, setPhases] = useState<Phase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listPhases(tournamentId)
      .then(setPhases)
      .catch((error) => setError(apiErrorMessage(error, "No se pudieron cargar las fases.")))
      .finally(() => setLoading(false));
  }, [tournamentId]);

  useEffect(() => {
    load();
  }, [load]);

  const [open, setOpen] = useState(false);
  const [phaseType, setPhaseType] = useState<PhaseType>("FINAL");
  const [phaseName, setPhaseName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    try {
      await createPhase(tournamentId, {
        type: phaseType,
        name: phaseName.trim() || undefined,
        displayOrder: 0,
      });
      setOpen(false);
      setPhaseType("FINAL");
      setPhaseName("");
      load();
      onChanged?.();
    } catch (e) {
      setFormError(apiErrorMessage(e, "No se pudo crear la fase."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Fases</CardTitle>
          {isConfigurable(tournamentStatus) && (
            <Dialog open={open} onOpenChange={setOpen}>
              <DialogTrigger asChild>
                <Button size="sm" variant="secondary">
                  <Plus className="size-4" aria-hidden="true" />
                  Crear fase
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Crear fase</DialogTitle>
                </DialogHeader>
                <form onSubmit={handleCreate} className="flex flex-col gap-4" noValidate>
                  <FormField label="Tipo de fase" required>
                    {() => (
                      <Select
                        value={phaseType}
                        onValueChange={(v) => setPhaseType(v as PhaseType)}
                      >
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {PHASE_TYPES.map((pt) => (
                            <SelectItem key={pt.value} value={pt.value}>
                              {pt.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  </FormField>

                  <FormField label="Nombre (opcional)">
                    {(field) => (
                      <Input
                        {...field}
                        value={phaseName}
                        onChange={(e) => setPhaseName(e.target.value)}
                        placeholder="Semifinal Ida"
                      />
                    )}
                  </FormField>

                  {formError && (
                    <p className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
                      {formError}
                    </p>
                  )}

                  <Button type="submit" disabled={submitting}>
                    {submitting ? "Creando…" : "Crear fase"}
                  </Button>
                </form>
              </DialogContent>
            </Dialog>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {loading ? (
          <LoadingState label="Cargando fases…" />
        ) : error ? (
          <ErrorState title="Error" description={error} onRetry={load} />
        ) : phases.length === 0 ? (
          <EmptyState
            title="Sin fases"
            description="Creá fases para organizar la etapa eliminatoria."
          />
        ) : (
          <div className="flex flex-col gap-2">
            {phases.map((p) => (
              <div
                key={p.id}
                className="flex items-center justify-between rounded-lg border border-border px-4 py-2"
              >
                <div>
                  <p className="text-sm font-bold">
                    {p.name ?? PHASE_TYPES.find((pt) => pt.value === p.type)?.label ?? p.type}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {PHASE_TYPES.find((pt) => pt.value === p.type)?.label ?? p.type}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ── Matches Section ───────────────────────────────────────────────────────

function MatchesSection({
  tournamentId,
  tournamentStatus,
  groups,
  phases,
  tournamentTeams,
  onChanged,
}: {
  tournamentId: string;
  tournamentStatus: string;
  groups: Group[];
  phases: Phase[];
  tournamentTeams: Team[];
  onChanged?: () => void;
}) {
  const [matches, setMatches] = useState<Match[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listMatches(tournamentId)
      .then(setMatches)
      .catch((error) => setError(apiErrorMessage(error, "No se pudieron cargar los partidos.")))
      .finally(() => setLoading(false));
  }, [tournamentId]);

  useEffect(() => {
    load();
  }, [load]);

  // Create match form
  const [createOpen, setCreateOpen] = useState(false);
  const [matchGroupId, setMatchGroupId] = useState("");
  const [matchPhaseId, setMatchPhaseId] = useState("");
  const [homeTeamId, setHomeTeamId] = useState("");
  const [awayTeamId, setAwayTeamId] = useState("");
  const [startTime, setStartTime] = useState("");
  const [submittingCreate, setSubmittingCreate] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  async function handleCreateMatch(e: FormEvent) {
    e.preventDefault();
    setCreateError(null);

    if (!homeTeamId || !awayTeamId || !startTime) {
      setCreateError("Completá todos los campos requeridos.");
      return;
    }
    if (homeTeamId === awayTeamId) {
      setCreateError("Los equipos deben ser distintos.");
      return;
    }
    if ((matchGroupId && matchPhaseId) || (!matchGroupId && !matchPhaseId)) {
      setCreateError("Elegí exactamente un grupo o una fase.");
      return;
    }

    const location = matchGroupId
      ? { groupId: matchGroupId }
      : { phaseId: matchPhaseId };
    const input: CreateMatchInput = {
      ...location,
      homeTeamId,
      awayTeamId,
      startTime: new Date(startTime).toISOString(),
    };

    setSubmittingCreate(true);
    try {
      await createMatch(tournamentId, input);
      setCreateOpen(false);
      setMatchGroupId("");
      setMatchPhaseId("");
      setHomeTeamId("");
      setAwayTeamId("");
      setStartTime("");
      load();
      onChanged?.();
    } catch (e) {
      setCreateError(apiErrorMessage(e, "No se pudo crear el partido."));
    } finally {
      setSubmittingCreate(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Partidos</CardTitle>
          {isConfigurable(tournamentStatus) && (
            <Dialog open={createOpen} onOpenChange={setCreateOpen}>
              <DialogTrigger asChild>
                <Button size="sm" variant="secondary">
                  <Plus className="size-4" aria-hidden="true" />
                  Crear partido
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Crear partido</DialogTitle>
                </DialogHeader>
                <form
                  onSubmit={handleCreateMatch}
                  className="flex flex-col gap-4"
                  noValidate
                >
                  <div className="grid grid-cols-2 gap-4">
                    {groups.length > 0 && (
                      <FormField label="Grupo">
                        {() => (
                          <Select
                            value={matchGroupId}
                            onValueChange={(v) => {
                              setMatchGroupId(v);
                              setMatchPhaseId("");
                            }}
                          >
                            <SelectTrigger>
                              <SelectValue placeholder="Ninguno" />
                            </SelectTrigger>
                            <SelectContent>
                              {groups.map((g) => (
                                <SelectItem key={g.id} value={g.id}>
                                  {g.name}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        )}
                      </FormField>
                    )}

                    {phases.length > 0 && (
                      <FormField label="Fase">
                        {() => (
                          <Select
                            value={matchPhaseId}
                            onValueChange={(v) => {
                              setMatchPhaseId(v);
                              setMatchGroupId("");
                            }}
                          >
                            <SelectTrigger>
                              <SelectValue placeholder="Ninguno" />
                            </SelectTrigger>
                            <SelectContent>
                              {phases.map((p) => (
                                <SelectItem key={p.id} value={p.id}>
                                  {p.name ?? p.type}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        )}
                      </FormField>
                    )}
                  </div>

                  <FormField label="Equipo local" required>
                    {() => (
                      <Select value={homeTeamId} onValueChange={setHomeTeamId}>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Seleccionar equipo" />
                        </SelectTrigger>
                        <SelectContent>
                          {tournamentTeams.map((t) => (
                            <SelectItem key={t.id} value={t.id}>
                              {t.shortName} — {t.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  </FormField>

                  <FormField label="Equipo visitante" required>
                    {() => (
                      <Select value={awayTeamId} onValueChange={setAwayTeamId}>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Seleccionar equipo" />
                        </SelectTrigger>
                        <SelectContent>
                          {tournamentTeams.map((t) => (
                            <SelectItem key={t.id} value={t.id}>
                              {t.shortName} — {t.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  </FormField>

                  <FormField label="Fecha y hora" required>
                    {(field) => (
                      <Input
                        {...field}
                        type="datetime-local"
                        value={startTime}
                        onChange={(e) => setStartTime(e.target.value)}
                      />
                    )}
                  </FormField>

                  {createError && (
                    <p className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
                      {createError}
                    </p>
                  )}

                  <Button type="submit" disabled={submittingCreate}>
                    {submittingCreate ? "Creando…" : "Crear partido"}
                  </Button>
                </form>
              </DialogContent>
            </Dialog>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {loading ? (
          <LoadingState label="Cargando partidos…" />
        ) : error ? (
          <ErrorState title="Error" description={error} onRetry={load} />
        ) : matches.length === 0 ? (
          <EmptyState
            title="Sin partidos"
            description="Creá partidos para armar el fixture del torneo."
          />
        ) : (
          <div className="flex flex-col gap-3">
            {matches.map((match) => (
              <div
                key={match.id}
                className="rounded-lg border border-border p-4"
              >
                <div className="mb-2 flex items-center justify-between text-sm">
                  <MatchStatusBadge status={match.status} />
                  <span className="text-xs text-muted-foreground">
                    {formatDate(match.startTime)}
                  </span>
                </div>

                <div className="mb-2 text-center text-base font-bold">
                  {match.homeTeam.shortName || match.homeTeam.name}{" "}
                  <span className="text-muted-foreground">
                    {match.homeScore !== null && match.awayScore !== null
                      ? `${match.homeScore} – ${match.awayScore}`
                      : "vs"}
                  </span>{" "}
                  {match.awayTeam.shortName || match.awayTeam.name}
                </div>

                <p className="text-xs text-muted-foreground">
                  {match.homeTeam.name} vs {match.awayTeam.name}
                </p>

                {match.status !== "CANCELLED" && match.status !== "FINISHED" && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    <EditMatchDialog
                      match={match}
                      groups={groups}
                      phases={phases}
                      tournamentTeams={tournamentTeams}
                      onSaved={() => {
                        load();
                        onChanged?.();
                      }}
                    />
                  </div>
                )}

                {(match.status === "SCHEDULED" ||
                  match.status === "IN_PROGRESS" ||
                  match.status === "FINISHED") && (
                  <div className="mt-3">
                    <LoadResultInline match={match} onSaved={load} />
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function EditMatchDialog({
  match,
  groups,
  phases,
  tournamentTeams,
  onSaved,
}: {
  match: Match;
  groups: Group[];
  phases: Phase[];
  tournamentTeams: Team[];
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [groupId, setGroupId] = useState(match.groupId ?? "");
  const [phaseId, setPhaseId] = useState(match.phaseId ?? "");
  const [homeTeamId, setHomeTeamId] = useState(match.homeTeam.id);
  const [awayTeamId, setAwayTeamId] = useState(match.awayTeam.id);
  const [startTime, setStartTime] = useState(toDateTimeLocal(match.startTime));
  const [status, setStatus] = useState<MatchStatus>(match.status);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setGroupId(match.groupId ?? "");
    setPhaseId(match.phaseId ?? "");
    setHomeTeamId(match.homeTeam.id);
    setAwayTeamId(match.awayTeam.id);
    setStartTime(toDateTimeLocal(match.startTime));
    setStatus(match.status);
    setError(null);
  }, [open, match]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!homeTeamId || !awayTeamId || !startTime) {
      setError("Completá todos los campos requeridos.");
      return;
    }
    if (homeTeamId === awayTeamId) {
      setError("Los equipos deben ser distintos.");
      return;
    }
    if ((groupId && phaseId) || (!groupId && !phaseId)) {
      setError("Elegí exactamente un grupo o una fase.");
      return;
    }

    const location = groupId ? { groupId } : { phaseId };
    const input: UpdateMatchInput = {
      ...location,
      homeTeamId,
      awayTeamId,
      startTime: new Date(startTime).toISOString(),
      status,
    };

    setSubmitting(true);
    try {
      await updateMatch(match.id, input);
      setOpen(false);
      onSaved();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "No se pudo actualizar el partido."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="xs" variant="outline">Editar partido</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar partido</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <div className="grid grid-cols-2 gap-4">
            <FormField label="Grupo">
              {() => (
                <Select
                  value={groupId}
                  onValueChange={(value) => {
                    setGroupId(value);
                    setPhaseId("");
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Seleccionar grupo" />
                  </SelectTrigger>
                  <SelectContent>
                    {groups.map((group) => (
                      <SelectItem key={group.id} value={group.id}>{group.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </FormField>
            <FormField label="Fase">
              {() => (
                <Select
                  value={phaseId}
                  onValueChange={(value) => {
                    setPhaseId(value);
                    setGroupId("");
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Seleccionar fase" />
                  </SelectTrigger>
                  <SelectContent>
                    {phases.map((phase) => (
                      <SelectItem key={phase.id} value={phase.id}>
                        {phase.name ?? phase.type}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </FormField>
          </div>

          <FormField label="Equipo local" required>
            {() => (
              <Select value={homeTeamId} onValueChange={setHomeTeamId}>
                <SelectTrigger className="w-full"><SelectValue placeholder="Seleccionar equipo" /></SelectTrigger>
                <SelectContent>
                  {tournamentTeams.map((team) => (
                    <SelectItem key={team.id} value={team.id}>
                      {teamLabel(team)} — {team.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </FormField>

          <FormField label="Equipo visitante" required>
            {() => (
              <Select value={awayTeamId} onValueChange={setAwayTeamId}>
                <SelectTrigger className="w-full"><SelectValue placeholder="Seleccionar equipo" /></SelectTrigger>
                <SelectContent>
                  {tournamentTeams.map((team) => (
                    <SelectItem key={team.id} value={team.id}>
                      {teamLabel(team)} — {team.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </FormField>

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

          <FormField label="Estado" required>
            {() => (
              <Select value={status} onValueChange={(value) => setStatus(value as MatchStatus)}>
                <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="SCHEDULED">Programado</SelectItem>
                  <SelectItem value="IN_PROGRESS">En juego</SelectItem>
                  <SelectItem value="POSTPONED">Pospuesto</SelectItem>
                  <SelectItem value="CANCELLED">Cancelado</SelectItem>
                </SelectContent>
              </Select>
            )}
          </FormField>

          {error && (
            <p className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" disabled={submitting}>
            {submitting ? "Guardando…" : "Guardar cambios"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── Load Result Inline Form ───────────────────────────────────────────────

function LoadResultInline({
  match,
  onSaved,
}: {
  match: Match;
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [homeScore, setHomeScore] = useState(
    match.homeScore !== null ? String(match.homeScore) : "",
  );
  const [awayScore, setAwayScore] = useState(
    match.awayScore !== null ? String(match.awayScore) : "",
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    const hs = parseInt(homeScore, 10);
    const as = parseInt(awayScore, 10);
    if (isNaN(hs) || isNaN(as) || hs < 0 || hs > 99 || as < 0 || as > 99) {
      setError("Ingresá goles válidos (0–99).");
      return;
    }

    setSubmitting(true);
    try {
      await loadResult(match.id, { homeScore: hs, awayScore: as });
      setOpen(false);
      onSaved();
    } catch (e) {
      setError(apiErrorMessage(e, "No se pudo cargar el resultado."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="xs" variant="outline">
          {match.status === "FINISHED" ? "Corregir resultado" : "Cargar resultado"}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cargar resultado</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <p className="text-sm text-muted-foreground">
             {match.homeTeam.shortName || match.homeTeam.name} vs {match.awayTeam.shortName || match.awayTeam.name}
          </p>
          <div className="grid grid-cols-2 gap-4">
            <FormField label="Goles local" required>
              {(field) => (
                <Input
                  {...field}
                  type="number"
                  min={0}
                  max={99}
                  value={homeScore}
                  onChange={(e) => setHomeScore(e.target.value)}
                  placeholder="0"
                />
              )}
            </FormField>
            <FormField label="Goles visitante" required>
              {(field) => (
                <Input
                  {...field}
                  type="number"
                  min={0}
                  max={99}
                  value={awayScore}
                  onChange={(e) => setAwayScore(e.target.value)}
                  placeholder="0"
                />
              )}
            </FormField>
          </div>

          {error && (
            <p className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" disabled={submitting}>
            {submitting ? "Cargando…" : "Cargar resultado"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────

export function AdminTournamentManagePage() {
  const { tournamentId } = useParams<{ tournamentId: string }>();
  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [tournamentTeams, setTournamentTeams] = useState<Team[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [phases, setPhases] = useState<Phase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  usePageHeader(tournament?.name ?? "Administrar torneo");

  const loadMeta = useCallback(() => {
    if (!tournamentId) return;
    setLoading(true);
    setError(null);
    Promise.all([
      getTournament(tournamentId),
      getTournamentTeams(tournamentId),
      listGroups(tournamentId),
      listPhases(tournamentId),
    ])
      .then(([t, teams, g, p]) => {
        setTournament(t);
        setTournamentTeams(teams);
        setGroups(g);
        setPhases(p);
      })
      .catch(() => setError("No se pudo cargar la información del torneo."))
      .finally(() => setLoading(false));
  }, [tournamentId]);

  useEffect(() => {
    loadMeta();
  }, [loadMeta]);

  if (loading) return <LoadingState label="Cargando torneo…" />;
  if (error) return <ErrorState title="Error" description={error} onRetry={loadMeta} />;
  if (!tournament || !tournamentId) {
    return <ErrorState title="No encontrado" description="El torneo no existe." />;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold">{tournament.name}</h2>
          <p className="text-sm text-muted-foreground">{tournament.description}</p>
        </div>
        <TournamentStatusBadge status={tournament.status as TournamentStatus} />
      </div>

      <TeamAssignmentSection
        tournamentId={tournament.id}
        tournamentStatus={tournament.status}
        onChanged={loadMeta}
      />

      <GroupsSection
        tournamentId={tournament.id}
        tournamentStatus={tournament.status}
        tournamentTeams={tournamentTeams}
        onChanged={loadMeta}
      />

      <PhasesSection
        tournamentId={tournament.id}
        tournamentStatus={tournament.status}
        onChanged={loadMeta}
      />

      <MatchesSection
        tournamentId={tournament.id}
        tournamentStatus={tournament.status}
        groups={groups}
        phases={phases}
        tournamentTeams={tournamentTeams}
        onChanged={loadMeta}
      />
    </div>
  );
}
