import { useEffect, useState, useCallback } from "react";
import { Link, useParams } from "react-router-dom";
import { Calendar } from "lucide-react";
import { useAuth } from "@/auth/AuthContext";
import { usePageHeader } from "@/components/layout/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { TournamentStatusBadge, MatchStatusBadge, PredictionLockBadge } from "@/components/common/StatusBadge";
import { LoadingState, EmptyState, ErrorState } from "@/components/common/states";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Separator } from "@/components/ui/separator";
import {
  getTournament,
  listGroups,
  getGroupTeams,
  listPhases,
  listMatches,
  getEnrolment,
  joinTournament,
} from "../api/tournaments";
import type {
  Tournament,
  Group,
  Phase,
  Match,
  Team,
} from "../types/tournament";
import { apiErrorMessage } from "../errors";

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("es-AR", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatDateOnly(dateStr: string | null): string {
  if (!dateStr) return "Sin fecha";
  const [year, month, day] = dateStr.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("es-AR", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

// ── Groups Tab ────────────────────────────────────────────────────────────

function GroupsTab({ tournamentId }: { tournamentId: string }) {
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

  if (loading) return <LoadingState label="Cargando grupos…" />;
  if (error) return <ErrorState title="Error" description={error} onRetry={load} />;
  if (groups.length === 0) {
    return (
      <EmptyState
        title="Sin grupos"
        description="Este torneo todavía no tiene grupos definidos."
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {groups.map((group) => {
        const teams = teamsByGroup[group.id] ?? [];
        return (
          <Card key={group.id}>
            <CardContent>
              <h4 className="mb-3 text-base font-bold">{group.name}</h4>
              {teams.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Sin equipos asignados.
                </p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {teams.map((team) => (
                    <span
                      key={team.id}
                      className="rounded-full border border-border bg-muted px-3 py-1 text-sm font-medium"
                    >
                      {team.shortName || team.name}
                    </span>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}

// ── Phases Tab ────────────────────────────────────────────────────────────

const PHASE_LABELS: Record<string, string> = {
  GROUP_STAGE: "Fase de grupos",
  ROUND_OF_16: "Octavos de final",
  QUARTER_FINAL: "Cuartos de final",
  SEMI_FINAL: "Semifinal",
  THIRD_PLACE: "Tercer puesto",
  FINAL: "Final",
};

function PhasesTab({ tournamentId }: { tournamentId: string }) {
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

  if (loading) return <LoadingState label="Cargando fases…" />;
  if (error) return <ErrorState title="Error" description={error} onRetry={load} />;
  if (phases.length === 0) {
    return (
      <EmptyState
        title="Sin fases"
        description="Este torneo todavía no tiene fases definidas."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {phases.map((phase) => (
        <div
          key={phase.id}
          className="flex items-center justify-between rounded-lg border border-border bg-card px-5 py-3"
        >
          <div>
            <p className="text-sm font-bold">
              {phase.name ?? PHASE_LABELS[phase.type] ?? phase.type}
            </p>
            <p className="text-xs text-muted-foreground">
              {PHASE_LABELS[phase.type] ?? phase.type}
            </p>
          </div>
          <span className="text-xs text-muted-foreground">
            Orden: {phase.displayOrder}
          </span>
        </div>
      ))}
    </div>
  );
}

// ── Fixture Tab ────────────────────────────────────────────────────────────

function FixtureTab({ tournamentId }: { tournamentId: string }) {
  const [matches, setMatches] = useState<Match[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>("");

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listMatches(tournamentId, statusFilter || undefined)
      .then(setMatches)
      .catch((error) => setError(apiErrorMessage(error, "No se pudieron cargar los partidos.")))
      .finally(() => setLoading(false));
  }, [tournamentId, statusFilter]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <LoadingState label="Cargando fixture…" />;
  if (error) return <ErrorState title="Error" description={error} onRetry={load} />;
  if (matches.length === 0) {
    return (
      <EmptyState
        title="Sin partidos"
        description="Todavía no hay partidos programados para este torneo."
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2">
        {[
          { label: "Todos", value: "" },
          { label: "Programados", value: "SCHEDULED" },
          { label: "En juego", value: "IN_PROGRESS" },
          { label: "Finalizados", value: "FINISHED" },
        ].map((f) => (
          <Button
            key={f.value}
            variant={statusFilter === f.value ? "default" : "secondary"}
            size="sm"
            onClick={() => setStatusFilter(f.value)}
          >
            {f.label}
          </Button>
        ))}
      </div>

      <div className="flex flex-col gap-3">
        {matches.map((match) => (
          <Card key={match.id}>
            <CardContent className="flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <MatchStatusBadge status={match.status} />
                  {match.predictionsOpen && <PredictionLockBadge open />}
                </div>
                <span className="text-xs text-muted-foreground">
                  {formatDate(match.startTime)}
                </span>
              </div>

              <div className="flex items-center justify-center gap-4 text-lg font-bold">
                 <span className="text-right flex-1">{match.homeTeam.shortName || match.homeTeam.name}</span>
                <span className="text-muted-foreground text-base">
                  {match.homeScore !== null && match.awayScore !== null
                    ? `${match.homeScore} – ${match.awayScore}`
                    : "vs"}
                </span>
                 <span className="flex-1">{match.awayTeam.shortName || match.awayTeam.name}</span>
              </div>

              <Separator />

              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span>
                  {match.homeTeam.name} vs {match.awayTeam.name}
                </span>
                {match.predictionsOpen && (
                  <Button asChild size="xs" variant="success">
                    <Link to={`/matches/${match.id}/predict`}>Pronosticá</Link>
                  </Button>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

// ── Detail Page ───────────────────────────────────────────────────────────

export function TournamentDetailPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>();
  const { user } = useAuth();

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [enrolled, setEnrolled] = useState(false);
  const [enrolmentLoading, setEnrolmentLoading] = useState(false);
  const [enrolmentError, setEnrolmentError] = useState<string | null>(null);
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (!tournamentId) return;
    setLoading(true);
    setError(null);
    setEnrolled(false);
    setEnrolmentError(null);
    setEnrolmentLoading(Boolean(user));
    getTournament(tournamentId)
      .then(async (t) => {
        setTournament(t);
        if (user) {
          try {
            const enrolment = await getEnrolment(t.id, user.id);
            setEnrolled(enrolment !== null);
          } catch (enrolmentRequestError) {
            setEnrolmentError(apiErrorMessage(
              enrolmentRequestError,
              "No pudimos comprobar tu inscripción.",
            ));
          } finally {
            setEnrolmentLoading(false);
          }
        }
      })
      .catch((requestError) => setError(apiErrorMessage(requestError, "No se pudo cargar el torneo.")))
      .finally(() => setLoading(false));
  }, [tournamentId, user, retry]);

  // Set the page header based on tournament name
  usePageHeader(tournament?.name ?? "Torneo");

  async function handleJoin() {
    if (!tournamentId) return;
    setJoining(true);
    setJoinError(null);
    try {
      await joinTournament(tournamentId);
      setEnrolled(true);
    } catch (e) {
      setJoinError(apiErrorMessage(e, "No se pudo inscribir en el torneo."));
    } finally {
      setJoining(false);
    }
  }

  if (loading) return <LoadingState label="Cargando torneo…" />;
  if (error) return <ErrorState title="Error" description={error} onRetry={() => setRetry((r) => r + 1)} />;
  if (!tournament || !tournamentId) {
    return <ErrorState title="No encontrado" description="El torneo no existe." />;
  }

  const canJoin =
    tournament.status === "ACTIVE" &&
    user &&
    user.roles.includes("PLAYER") &&
    !enrolled &&
    !enrolmentLoading &&
    !enrolmentError;

  return (
    <div className="flex flex-col gap-6">
      {/* Tournament info */}
      <Card>
        <CardContent className="flex flex-col gap-3">
          <div className="flex items-start justify-between gap-2">
            <div>
              <h2 className="text-xl font-bold">{tournament.name}</h2>
              {tournament.description && (
                <p className="mt-1 text-sm text-muted-foreground">
                  {tournament.description}
                </p>
              )}
            </div>
            <TournamentStatusBadge status={tournament.status} />
          </div>

          <div className="flex items-center gap-4 text-sm text-muted-foreground">
            <span className="flex items-center gap-1.5">
              <Calendar className="size-4" aria-hidden="true" />
              {formatDateOnly(tournament.startDate)} – {formatDateOnly(tournament.endDate)}
            </span>
          </div>

          {canJoin && (
            <div className="flex flex-col gap-2">
              <Button
                variant="success"
                onClick={handleJoin}
                disabled={joining}
              >
                {joining ? "Inscribiéndote…" : "Inscribirme en este torneo"}
              </Button>
              {joinError && (
                <p className="text-sm font-bold text-destructive">{joinError}</p>
              )}
            </div>
          )}

          {enrolmentError && (
            <p className="text-sm font-bold text-destructive">{enrolmentError}</p>
          )}

          {enrolled && (
            <p className="text-sm font-medium text-success">
              Ya estás inscripto en este torneo.
            </p>
          )}
        </CardContent>
      </Card>

      {/* Tabs */}
      <Tabs defaultValue="groups">
        <TabsList variant="pill">
          <TabsTrigger value="groups">Grupos</TabsTrigger>
          <TabsTrigger value="phases">Fases</TabsTrigger>
          <TabsTrigger value="fixture">Fixture</TabsTrigger>
        </TabsList>

        <TabsContent value="groups" className="mt-4">
          {tournamentId && <GroupsTab tournamentId={tournamentId} />}
        </TabsContent>

        <TabsContent value="phases" className="mt-4">
          {tournamentId && <PhasesTab tournamentId={tournamentId} />}
        </TabsContent>

        <TabsContent value="fixture" className="mt-4">
          {tournamentId && <FixtureTab tournamentId={tournamentId} />}
        </TabsContent>
      </Tabs>
    </div>
  );
}
