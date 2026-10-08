import { useEffect, useState, useCallback } from "react";
import { Link, useParams } from "react-router-dom";
import { BarChart3, Calendar, CheckCircle2, Settings } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/auth/AuthContext";
import { usePageHeader } from "@/components/layout/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { TournamentStatusBadge, MatchStatusBadge, PredictionLockBadge } from "@/components/common/StatusBadge";
import { LoadingState, EmptyState, ErrorState } from "@/components/common/states";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { getMyPredictions, type Prediction } from "@/features/predictions/api";
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
import { byKickoff, formatDateOnly, formatKickoff, matchesInGroup, phaseLabel, phaseTypeLabel } from "../format";
import { GroupStandings } from "../components/GroupStandings";
import { MatchTeams, ScoreOrVs } from "../components/MatchTeams";

// ── Groups Tab ────────────────────────────────────────────────────────────

function GroupsTab({ tournamentId }: { tournamentId: string }) {
  const [groups, setGroups] = useState<Group[]>([]);
  const [teamsByGroup, setTeamsByGroup] = useState<Record<string, Team[]>>({});
  const [matches, setMatches] = useState<Match[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all([listGroups(tournamentId), listMatches(tournamentId)])
      .then(async ([nextGroups, nextMatches]) => {
        const entries = await Promise.all(
          nextGroups.map((group) => getGroupTeams(group.id).then((teams) => [group.id, teams] as const)),
        );
        setGroups(nextGroups);
        setMatches(nextMatches);
        setTeamsByGroup(Object.fromEntries(entries));
      })
      .catch((error) => setError(apiErrorMessage(error, "No se pudieron cargar los grupos.")))
      .finally(() => setLoading(false));
  }, [tournamentId]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <LoadingState label="Cargando grupos…" />;
  if (error) return <ErrorState title="No pudimos cargar los grupos" description={error} onRetry={load} />;
  if (groups.length === 0) {
    return (
      <EmptyState
        title="Todavía no hay grupos"
        description="Este torneo todavía no tiene grupos definidos."
      />
    );
  }

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      {groups.map((group) => {
        const teams = teamsByGroup[group.id] ?? [];
        return (
          <Card key={group.id}>
            <CardContent>
              <h3 className="mb-3 text-base font-bold">{group.name}</h3>
              {teams.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Sin equipos asignados.
                </p>
              ) : (
                <GroupStandings
                  groupName={group.name}
                  teams={teams}
                  matches={matchesInGroup(matches, group.id)}
                />
              )}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}

// ── Phases Tab ────────────────────────────────────────────────────────────

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
  if (error) return <ErrorState title="No pudimos cargar las fases" description={error} onRetry={load} />;
  if (phases.length === 0) {
    return (
      <EmptyState
        title="Todavía no hay fases"
        description="Este torneo todavía no tiene fases definidas."
      />
    );
  }

  return (
    <ol className="flex flex-col gap-3">
      {phases.map((phase) => (
        <li
          key={phase.id}
          className="flex items-center justify-between rounded-lg border border-border bg-card px-5 py-3"
        >
          <span className="text-sm font-bold">{phaseLabel(phase)}</span>
          {phase.name && (
            <span className="text-xs text-muted-foreground">{phaseTypeLabel(phase.type)}</span>
          )}
        </li>
      ))}
    </ol>
  );
}

// ── Fixture Tab ────────────────────────────────────────────────────────────

const STATUS_FILTERS = [
  { label: "Todos", value: "ALL" },
  { label: "Programados", value: "SCHEDULED" },
  { label: "En juego", value: "IN_PROGRESS" },
  { label: "Finalizados", value: "FINISHED" },
] as const;

type StatusFilter = (typeof STATUS_FILTERS)[number]["value"];

function PredictionLine({ prediction }: { prediction: Prediction }) {
  return (
    <span className="text-sm text-muted-foreground">
      Tu pronóstico{" "}
      <strong className="font-extrabold text-foreground tabular-nums">
        {prediction.homeScore}–{prediction.awayScore}
      </strong>
    </span>
  );
}

/**
 * What the participant can do with this match, from data already on screen:
 * predict it, edit their prediction, or see that it is closed.
 *
 * Points per match are not shown: no endpoint returns a user's score for one
 * match (only ranking totals), and recomputing the scoring rule here would
 * duplicate score-service logic.
 */
function MatchPredictionArea({
  match,
  prediction,
  canPredict,
  isPlayer,
}: {
  match: Match;
  prediction: Prediction | undefined;
  canPredict: boolean;
  isPlayer: boolean;
}) {
  const predictPath = `/matches/${match.id}/predict`;

  if (!isPlayer) {
    return match.status === "FINISHED" ? null : <PredictionLockBadge open={match.predictionsOpen} />;
  }

  if (match.predictionsOpen) {
    if (!canPredict) {
      return <span className="text-sm text-muted-foreground">Inscribite para pronosticar</span>;
    }
    return prediction ? (
      <div className="flex items-center gap-3">
        <PredictionLine prediction={prediction} />
        <Button asChild size="sm" variant="outline">
          <Link to={predictPath} aria-label={`Editar pronóstico de ${match.homeTeam.name} contra ${match.awayTeam.name}`}>
            Editar
          </Link>
        </Button>
      </div>
    ) : (
      <Button asChild size="sm">
        <Link to={predictPath} aria-label={`Pronosticar ${match.homeTeam.name} contra ${match.awayTeam.name}`}>
          Pronosticar
        </Link>
      </Button>
    );
  }

  return (
    <div className="flex items-center gap-3">
      {prediction ? (
        <PredictionLine prediction={prediction} />
      ) : (
        canPredict && <span className="text-sm text-muted-foreground">No pronosticaste</span>
      )}
      {match.status !== "FINISHED" && <PredictionLockBadge open={false} />}
    </div>
  );
}

function FixtureTab({
  tournamentId,
  isPlayer,
  enrolled,
}: {
  tournamentId: string;
  isPlayer: boolean;
  enrolled: boolean;
}) {
  const [matches, setMatches] = useState<Match[]>([]);
  const [predictions, setPredictions] = useState<Map<string, Prediction>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");

  const canPredict = isPlayer && enrolled;

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    // Predictions are a nice-to-have on this screen: if they fail, the fixture
    // still renders and each row links to the prediction page.
    const myPredictions = canPredict
      ? getMyPredictions(tournamentId, 0, 200).catch(() => [] as Prediction[])
      : Promise.resolve([] as Prediction[]);
    Promise.all([listMatches(tournamentId), myPredictions])
      .then(([nextMatches, nextPredictions]) => {
        setMatches([...nextMatches].sort(byKickoff));
        setPredictions(new Map(nextPredictions.map((p) => [p.matchId, p])));
      })
      .catch((error) => setError(apiErrorMessage(error, "No se pudieron cargar los partidos.")))
      .finally(() => setLoading(false));
  }, [tournamentId, canPredict]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <LoadingState label="Cargando fixture…" />;
  if (error) return <ErrorState title="No pudimos cargar el fixture" description={error} onRetry={load} />;
  if (matches.length === 0) {
    return (
      <EmptyState
        title="Todavía no hay partidos"
        description="Cuando el administrador cargue el fixture, los partidos van a aparecer acá."
      />
    );
  }

  const visible =
    statusFilter === "ALL" ? matches : matches.filter((match) => match.status === statusFilter);
  const pending = canPredict
    ? matches.filter((match) => match.predictionsOpen && !predictions.has(match.id)).length
    : 0;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        {/* A filter, not a section: the muted segmented track sets it apart
            from the section pills above. */}
        <Tabs value={statusFilter} onValueChange={(value) => setStatusFilter(value as StatusFilter)}>
          <TabsList aria-label="Filtrar partidos por estado" className="border border-border bg-card">
            {STATUS_FILTERS.map((filter) => (
              <TabsTrigger
                key={filter.value}
                value={filter.value}
                className="px-3 text-[13px] data-[state=active]:bg-secondary data-[state=active]:text-secondary-foreground data-[state=active]:shadow-none"
              >
                {filter.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        {pending > 0 && (
          <p className="text-sm font-semibold text-primary">
            {pending === 1 ? "Te falta pronosticar 1 partido" : `Te faltan pronosticar ${pending} partidos`}
          </p>
        )}
      </div>

      {visible.length === 0 ? (
        <EmptyState
          title="No hay partidos con ese estado"
          action={
            <Button variant="secondary" onClick={() => setStatusFilter("ALL")}>
              Ver todos
            </Button>
          }
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {visible.map((match) => (
            <li key={match.id}>
              <Card className="gap-3 py-4">
                <CardContent className="flex flex-col gap-3">
                  <div className="flex items-center justify-between gap-2">
                    <MatchStatusBadge status={match.status} />
                    <span className="text-xs text-muted-foreground">{formatKickoff(match.startTime)}</span>
                  </div>
                  <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <MatchTeams
                      className="md:max-w-md md:flex-1"
                      homeTeam={match.homeTeam}
                      awayTeam={match.awayTeam}
                      center={<ScoreOrVs homeScore={match.homeScore} awayScore={match.awayScore} />}
                    />
                    <div className="flex justify-end">
                      <MatchPredictionArea
                        match={match}
                        prediction={predictions.get(match.id)}
                        canPredict={canPredict}
                        isPlayer={isPlayer}
                      />
                    </div>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ── Detail Page ───────────────────────────────────────────────────────────

export function TournamentDetailPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>();
  const { user, hasRole } = useAuth();

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [enrolled, setEnrolled] = useState(false);
  const [enrolmentLoading, setEnrolmentLoading] = useState(false);
  const [enrolmentError, setEnrolmentError] = useState<string | null>(null);
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const [tab, setTab] = useState<string | null>(null);

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
        // Participants come here to predict: open on the fixture while the
        // tournament is running, on the groups before it starts.
        setTab((current) => current ?? (t.status === "ACTIVE" || t.status === "FINISHED" ? "fixture" : "groups"));
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

  const name = tournament?.name ?? "Torneo";
  usePageHeader({
    title: name,
    subtitle: tournament?.description || undefined,
    breadcrumbs: [{ label: "Torneos", to: "/tournaments" }, { label: name }],
  });

  async function handleJoin() {
    if (!tournamentId) return;
    setJoining(true);
    setJoinError(null);
    try {
      await joinTournament(tournamentId);
      setEnrolled(true);
      setTab("fixture");
      toast.success("Te inscribiste", {
        description: "Ya podés pronosticar los partidos abiertos.",
      });
    } catch (e) {
      setJoinError(apiErrorMessage(e, "No se pudo completar la inscripción. Volvé a intentarlo."));
    } finally {
      setJoining(false);
    }
  }

  if (loading) return <LoadingState label="Cargando torneo…" />;
  if (error) return <ErrorState title="No pudimos cargar el torneo" description={error} onRetry={() => setRetry((r) => r + 1)} />;
  if (!tournament || !tournamentId) {
    return <ErrorState title="No encontramos este torneo" description="Volvé a la lista de torneos y elegí otro." />;
  }

  const isPlayer = hasRole("PLAYER");
  const canJoin =
    tournament.status === "ACTIVE" &&
    isPlayer &&
    !enrolled &&
    !enrolmentLoading &&
    !enrolmentError;

  return (
    <div className="flex flex-col gap-6">
      <Card className="px-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
              <TournamentStatusBadge status={tournament.status} />
              <span className="flex items-center gap-1.5">
                <Calendar className="size-4" aria-hidden="true" />
                {formatDateOnly(tournament.startDate)} – {formatDateOnly(tournament.endDate)}
              </span>
            </div>
            {enrolled && (
              <p className="flex items-center gap-1.5 text-sm font-semibold text-success">
                <CheckCircle2 className="size-4" aria-hidden="true" />
                Ya estás inscripto en este torneo.
              </p>
            )}
            {enrolmentError && (
              <p className="text-sm font-bold text-destructive">{enrolmentError}</p>
            )}
            {joinError && <p role="alert" className="text-sm font-bold text-destructive">{joinError}</p>}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {hasRole("ADMIN") && (
              <Button asChild variant="outline">
                <Link to={`/admin/tournaments/${tournament.id}`}>
                  <Settings aria-hidden="true" />
                  Administrar
                </Link>
              </Button>
            )}
            {(tournament.status === "ACTIVE" || tournament.status === "FINISHED") && (
              <Button asChild variant="secondary">
                <Link to={`/rankings/tournament/${tournament.id}`}>
                  <BarChart3 aria-hidden="true" />
                  Ver ranking
                </Link>
              </Button>
            )}
            {canJoin && (
              <Button variant="success" onClick={handleJoin} disabled={joining}>
                {joining ? "Inscribiéndote…" : "Inscribirme en este torneo"}
              </Button>
            )}
          </div>
        </div>
      </Card>

      <Tabs value={tab ?? "groups"} onValueChange={setTab}>
        <TabsList variant="pill" aria-label="Secciones del torneo">
          <TabsTrigger value="fixture">Fixture</TabsTrigger>
          <TabsTrigger value="groups">Grupos</TabsTrigger>
          <TabsTrigger value="phases">Fases</TabsTrigger>
        </TabsList>

        <TabsContent value="fixture" className="mt-3">
          <FixtureTab tournamentId={tournamentId} isPlayer={isPlayer} enrolled={enrolled} />
        </TabsContent>

        <TabsContent value="groups" className="mt-3">
          <GroupsTab tournamentId={tournamentId} />
        </TabsContent>

        <TabsContent value="phases" className="mt-3">
          <PhasesTab tournamentId={tournamentId} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
