import { useEffect, useState, useCallback, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Plus, Pencil, Trash2, Settings } from "lucide-react";
import { usePageHeader } from "@/components/layout/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PageTitle } from "@/components/common/PageTitle";
import { TournamentStatusBadge } from "@/components/common/StatusBadge";
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Separator } from "@/components/ui/separator";
import {
  listTournaments,
  createTournament,
  updateTournament,
  deleteTournament,
  updateTournamentStatus,
  listTeams,
  createTeam,
  updateTeam,
} from "@/features/tournaments/api/tournaments";
import type {
  Tournament,
  CreateTournamentInput,
  Team,
  CreateTeamInput,
} from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";
import {
  TOURNAMENT_STATUS,
  type TournamentStatus,
} from "@/features/tournaments/types/tournament";

function formatDateOnly(dateStr: string | null): string {
  if (!dateStr) return "Sin fecha";
  const [year, month, day] = dateStr.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("es-AR");
}

// ── Tournament Form Dialog ────────────────────────────────────────────────

function TournamentFormDialog({
  tournament,
  onSaved,
  trigger,
}: {
  tournament?: Tournament;
  onSaved: () => void;
  trigger: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(tournament?.name ?? "");
  const [description, setDescription] = useState(tournament?.description ?? "");
  const [startDate, setStartDate] = useState(
    tournament?.startDate ? tournament.startDate.split("T")[0] : "",
  );
  const [endDate, setEndDate] = useState(
    tournament?.endDate ? tournament.endDate.split("T")[0] : "",
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const editing = !!tournament;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim() || !startDate || !endDate) {
      setError("Completá todos los campos requeridos.");
      return;
    }

    const input: CreateTournamentInput = {
      name: name.trim(),
      description: description.trim() || undefined,
      startDate: startDate,
      endDate: endDate,
    };

    setSubmitting(true);
    try {
      if (editing && tournament) {
        await updateTournament(tournament.id, input);
      } else {
        await createTournament(input);
      }
      setOpen(false);
      onSaved();
    } catch (e) {
      setError(apiErrorMessage(e, "No se pudo guardar el torneo."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {editing ? "Editar torneo" : "Crear torneo"}
          </DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <FormField label="Nombre" required>
            {(field) => (
              <Input
                {...field}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Copa Oficina 2026"
              />
            )}
          </FormField>

          <FormField label="Descripción">
            {(field) => (
              <Input
                {...field}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Torneo interno"
              />
            )}
          </FormField>

          <div className="grid grid-cols-2 gap-4">
            <FormField label="Fecha inicio" required>
              {(field) => (
                <Input
                  {...field}
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                />
              )}
            </FormField>
            <FormField label="Fecha fin" required>
              {(field) => (
                <Input
                  {...field}
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
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
            {submitting ? "Guardando…" : editing ? "Guardar cambios" : "Crear torneo"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── Team Form Dialog ──────────────────────────────────────────────────────

function TeamFormDialog({
  team,
  onSaved,
  trigger,
}: {
  team?: Team;
  onSaved: () => void;
  trigger: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(team?.name ?? "");
  const [shortName, setShortName] = useState(team?.shortName ?? "");
  const [country, setCountry] = useState(team?.country ?? "");
  const [logoUrl, setLogoUrl] = useState(team?.logoUrl ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const editing = Boolean(team);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim() || !shortName.trim() || !country.trim()) {
      setError("Completá todos los campos requeridos.");
      return;
    }

    const input: CreateTeamInput = {
      name: name.trim(),
      shortName: shortName.trim(),
      country: country.trim().toUpperCase(),
      logoUrl: logoUrl.trim() || undefined,
    };

    setSubmitting(true);
    try {
      if (team) {
        await updateTeam(team.id, input);
      } else {
        await createTeam(input);
      }
      setOpen(false);
      if (!editing) {
        setName("");
        setShortName("");
        setCountry("");
        setLogoUrl("");
      }
      onSaved();
    } catch (e) {
      setError(apiErrorMessage(e, editing ? "No se pudo actualizar el equipo." : "No se pudo crear el equipo."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? "Editar equipo" : "Crear equipo"}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <FormField label="Nombre completo" required>
            {(field) => (
              <Input
                {...field}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Club Atlético Central"
              />
            )}
          </FormField>

          <div className="grid grid-cols-2 gap-4">
            <FormField label="Nombre corto" required>
              {(field) => (
                <Input
                  {...field}
                  value={shortName}
                  onChange={(e) => setShortName(e.target.value)}
                  placeholder="CAC"
                />
              )}
            </FormField>
            <FormField label="País (código)" required>
              {(field) => (
                <Input
                  {...field}
                  value={country}
                  onChange={(e) => setCountry(e.target.value)}
                  placeholder="AR"
                  maxLength={2}
                />
              )}
            </FormField>
          </div>

          <FormField label="Logo URL">
            {(field) => (
              <Input
                {...field}
                value={logoUrl}
                onChange={(e) => setLogoUrl(e.target.value)}
                placeholder="https://..."
              />
            )}
          </FormField>

          {error && (
            <p className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" disabled={submitting}>
             {submitting ? "Guardando…" : editing ? "Guardar cambios" : "Crear equipo"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────

export function AdminDashboardPage() {
  usePageHeader("Panel de administración");

  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [loadingT, setLoadingT] = useState(true);
  const [loadingTeams, setLoadingTeams] = useState(true);
  const [errorT, setErrorT] = useState<string | null>(null);
  const [errorTeams, setErrorTeams] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const loadTournaments = useCallback((pageNumber = page) => {
    setLoadingT(true);
    setErrorT(null);
    listTournaments(undefined, pageNumber, 20)
      .then((result) => {
        setTournaments(result.content);
        setTotalPages(result.totalPages);
      })
      .catch((requestError) => setErrorT(apiErrorMessage(requestError, "No se pudieron cargar los torneos.")))
      .finally(() => setLoadingT(false));
  }, [page]);

  const loadTeams = useCallback(() => {
    setLoadingTeams(true);
    setErrorTeams(null);
    listTeams()
      .then(setTeams)
      .catch(() => setErrorTeams("No se pudieron cargar los equipos."))
      .finally(() => setLoadingTeams(false));
  }, []);

  useEffect(() => {
    loadTournaments();
    loadTeams();
  }, [loadTournaments, loadTeams]);

  async function handleDelete(id: string) {
    if (!window.confirm("¿Estás seguro de que querés eliminar este torneo?")) return;
    setActionError(null);
    try {
      await deleteTournament(id);
      loadTournaments();
    } catch (e) {
      setActionError(apiErrorMessage(e, "No se pudo eliminar el torneo."));
    }
  }

  async function handleStatusChange(id: string, status: string) {
    setActionError(null);
    try {
      await updateTournamentStatus(id, status);
      loadTournaments();
    } catch (e) {
      setActionError(apiErrorMessage(e, "No se pudo cambiar el estado."));
    }
  }

  return (
    <div className="flex flex-col gap-8">
      {actionError && (
        <p className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
          {actionError}
        </p>
      )}
      {/* Torneos Section */}
      <section>
        <PageTitle
          title="Torneos"
          action={
            <TournamentFormDialog
              onSaved={loadTournaments}
              trigger={
                <Button size="sm">
                  <Plus className="size-4" aria-hidden="true" />
                  Crear torneo
                </Button>
              }
            />
          }
        />

        {loadingT ? (
          <LoadingState label="Cargando torneos…" />
        ) : errorT ? (
          <ErrorState title="Error" description={errorT} onRetry={loadTournaments} />
        ) : tournaments.length === 0 ? (
          <EmptyState title="Sin torneos" description="Creá el primer torneo para empezar." />
        ) : (
          <Card>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nombre</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Fechas</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {tournaments.map((t) => (
                    <TableRow key={t.id}>
                      <TableCell className="font-medium">{t.name}</TableCell>
                      <TableCell>
                        <TournamentStatusBadge status={t.status as TournamentStatus} />
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                         {formatDateOnly(t.startDate)} – {formatDateOnly(t.endDate)}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          {t.status === TOURNAMENT_STATUS.DRAFT && (
                            <Button
                              size="icon-xs"
                              variant="ghost"
                              title="Activar"
                              onClick={() =>
                                handleStatusChange(t.id, TOURNAMENT_STATUS.ACTIVE)
                              }
                            >
                              ▶
                            </Button>
                          )}
                          {t.status === TOURNAMENT_STATUS.ACTIVE && (
                            <Button
                              size="icon-xs"
                              variant="ghost"
                              title="Finalizar"
                              onClick={() =>
                                handleStatusChange(t.id, TOURNAMENT_STATUS.FINISHED)
                              }
                            >
                              ⏹
                            </Button>
                          )}
                          {(t.status === TOURNAMENT_STATUS.DRAFT ||
                            t.status === TOURNAMENT_STATUS.ACTIVE) && (
                            <Button
                              size="icon-xs"
                              variant="ghost"
                              title="Cancelar"
                              onClick={() =>
                                handleStatusChange(t.id, TOURNAMENT_STATUS.CANCELLED)
                              }
                            >
                              ×
                            </Button>
                          )}

                          <TournamentFormDialog
                            tournament={t}
                            onSaved={loadTournaments}
                            trigger={
                              <Button size="icon-xs" variant="ghost" title="Editar">
                                <Pencil className="size-3.5" aria-hidden="true" />
                              </Button>
                            }
                          />

                          <Button asChild size="icon-xs" variant="ghost" title="Administrar">
                            <Link to={`/admin/tournaments/${t.id}`}>
                              <Settings className="size-3.5" aria-hidden="true" />
                            </Link>
                          </Button>

                          {t.status === TOURNAMENT_STATUS.DRAFT && (
                            <Button
                              size="icon-xs"
                              variant="ghost"
                              title="Eliminar"
                              onClick={() => handleDelete(t.id)}
                            >
                              <Trash2 className="size-3.5 text-destructive" aria-hidden="true" />
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}

        {totalPages > 1 && !loadingT && !errorT && (
          <div className="mt-4 flex items-center justify-between gap-3">
            <Button
              variant="secondary"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((current) => current - 1)}
            >
              Anterior
            </Button>
            <span className="text-sm text-muted-foreground">
              Página {page + 1} de {totalPages}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Siguiente
            </Button>
          </div>
        )}
      </section>

      <Separator />

      {/* Teams Section */}
      <section>
        <PageTitle
          title="Equipos"
          action={
            <TeamFormDialog
              onSaved={loadTeams}
              trigger={
                <Button size="sm">
                  <Plus className="size-4" aria-hidden="true" />
                  Crear equipo
                </Button>
              }
            />
          }
        />

        {loadingTeams ? (
          <LoadingState label="Cargando equipos…" />
        ) : errorTeams ? (
          <ErrorState title="Error" description={errorTeams} onRetry={loadTeams} />
        ) : teams.length === 0 ? (
          <EmptyState title="Sin equipos" description="Creá equipos en el catálogo para asignarlos a torneos." />
        ) : (
          <Card>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nombre</TableHead>
                    <TableHead>Corto</TableHead>
                    <TableHead>País</TableHead>
                    <TableHead>Logo</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {teams.map((team) => (
                    <TableRow key={team.id}>
                      <TableCell className="font-medium">{team.name}</TableCell>
                      <TableCell className="font-bold">{team.shortName || team.name}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {team.country}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {team.logoUrl ? "✓" : "—"}
                      </TableCell>
                      <TableCell className="text-right">
                        <TeamFormDialog
                          team={team}
                          onSaved={loadTeams}
                          trigger={
                            <Button size="icon-xs" variant="ghost" title="Editar">
                              <Pencil className="size-3.5" aria-hidden="true" />
                            </Button>
                          }
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </section>
    </div>
  );
}
