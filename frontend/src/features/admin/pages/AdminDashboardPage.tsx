import { useEffect, useState, useCallback, type FormEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Plus, Pencil } from "lucide-react";
import { toast } from "sonner";
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
  DialogDescription,
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
  listTeams,
  createTeam,
  updateTeam,
} from "@/features/tournaments/api/tournaments";
import type { Tournament, Team, CreateTeamInput } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { formatDateOnly } from "@/features/tournaments/format";
import { TournamentFormDialog } from "../components/TournamentFormDialog";
import { AdminUsersSection } from "../users/AdminUsersSection";

// ── Team Form Dialog ──────────────────────────────────────────────────────

function TeamFormDialog({
  team,
  onSaved,
  trigger,
}: {
  team?: Team;
  onSaved: () => void;
  trigger: ReactNode;
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
      setError("Completá el nombre, el nombre corto y el país.");
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
      toast.success(editing ? "Cambios guardados" : "Equipo creado", {
        description: editing
          ? `Actualizamos ${input.name}.`
          : `${input.name} ya está en el catálogo. Asignalo a un torneo desde su página de administración.`,
      });
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
          <DialogDescription>
            Los equipos del catálogo se pueden asignar a cualquier torneo.
          </DialogDescription>
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
            <FormField label="País (código de 2 letras)" required>
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

          <FormField label="URL del logo">
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
            <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
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
  usePageHeader("Panel de administración", "Torneos, equipos y usuarios.");

  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [loadingT, setLoadingT] = useState(true);
  const [loadingTeams, setLoadingTeams] = useState(true);
  const [errorT, setErrorT] = useState<string | null>(null);
  const [errorTeams, setErrorTeams] = useState<string | null>(null);
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

  return (
    <div className="flex flex-col gap-8">
      <section>
        <PageTitle
          title="Torneos"
          action={
            <TournamentFormDialog
              onSaved={() => loadTournaments()}
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
          <ErrorState title="No pudimos cargar los torneos" description={errorT} onRetry={() => loadTournaments()} />
        ) : tournaments.length === 0 ? (
          <EmptyState title="Todavía no hay torneos" description="Creá el primer torneo con el botón “Crear torneo”." />
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
                      <TableCell className="font-medium">
                        <Link
                          to={`/admin/tournaments/${t.id}`}
                          className="rounded-sm hover:text-primary hover:underline"
                        >
                          {t.name}
                        </Link>
                      </TableCell>
                      <TableCell>
                        <TournamentStatusBadge status={t.status} />
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatDateOnly(t.startDate)} – {formatDateOnly(t.endDate)}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <TournamentFormDialog
                            tournament={t}
                            onSaved={() => loadTournaments()}
                            trigger={
                              <Button
                                size="icon-sm"
                                variant="ghost"
                                aria-label={`Editar ${t.name}`}
                                title="Editar"
                              >
                                <Pencil aria-hidden="true" />
                              </Button>
                            }
                          />
                          <Button asChild size="sm">
                            <Link to={`/admin/tournaments/${t.id}`}>Administrar</Link>
                          </Button>
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
          <ErrorState title="No pudimos cargar los equipos" description={errorTeams} onRetry={loadTeams} />
        ) : teams.length === 0 ? (
          <EmptyState title="Todavía no hay equipos" description="Creá equipos en el catálogo para asignarlos a los torneos." />
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
                        {team.logoUrl ? "Cargado" : "Sin logo"}
                      </TableCell>
                      <TableCell className="text-right">
                        <TeamFormDialog
                          team={team}
                          onSaved={loadTeams}
                          trigger={
                            <Button
                              size="icon-sm"
                              variant="ghost"
                              aria-label={`Editar ${team.name}`}
                              title="Editar"
                            >
                              <Pencil aria-hidden="true" />
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

      <Separator />

      <AdminUsersSection />
    </div>
  );
}
