import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { BarChart3, Calendar, Eye, Pencil } from "lucide-react";
import { toast } from "sonner";
import { usePageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { TournamentStatusBadge } from "@/components/common/StatusBadge";
import { ErrorState, LoadingState } from "@/components/common/states";
import {
  getTournament,
  getTournamentTeams,
  listGroups,
  listMatches,
  listPhases,
} from "@/features/tournaments/api/tournaments";
import type { Group, Match, Phase, Team, Tournament } from "@/features/tournaments/types/tournament";
import { apiErrorMessage, isNotFoundError } from "@/features/tournaments/errors";
import { formatDateOnly } from "@/features/tournaments/format";
import { TournamentFormDialog } from "../components/TournamentFormDialog";
import { GroupsTab } from "../manage/GroupsTab";
import { MatchesTab } from "../manage/MatchesTab";
import { PhasesTab } from "../manage/PhasesTab";
import { TeamsTab } from "../manage/TeamsTab";
import { TournamentLifecycleActions } from "../manage/TournamentLifecycleActions";

interface ManageData {
  tournament: Tournament;
  teams: Team[];
  groups: Group[];
  phases: Phase[];
  matches: Match[];
}

type Tab = "teams" | "groups" | "phases" | "matches";

function TabLabel({ label, count }: { label: string; count: number }) {
  return (
    <>
      {label}
      <span className="tabular-nums opacity-70">{count}</span>
    </>
  );
}

export function AdminTournamentManagePage() {
  const { tournamentId } = useParams<{ tournamentId: string }>();
  const [data, setData] = useState<ManageData | null>(null);
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [tab, setTab] = useState<Tab | null>(null);
  const hasData = useRef(false);

  const name = data?.tournament.name ?? "Torneo";
  usePageHeader({
    title: name,
    subtitle: data?.tournament.description || undefined,
    breadcrumbs: [{ label: "Panel admin", to: "/admin" }, { label: name }],
  });

  /**
   * After the first load, refreshes are silent: the tabs and the open section
   * stay on screen and only the numbers change. A failed refresh keeps the old
   * data and says so in a toast.
   */
  const load = useCallback(() => {
    if (!tournamentId) return;
    if (!hasData.current) setLoadError(null);
    Promise.all([
      getTournament(tournamentId),
      getTournamentTeams(tournamentId),
      listGroups(tournamentId),
      listPhases(tournamentId),
      listMatches(tournamentId),
    ])
      .then(([tournament, teams, groups, phases, matches]) => {
        hasData.current = true;
        setData({ tournament, teams, groups, phases, matches });
        setTab((current) => current ?? (tournament.status === "ACTIVE" ? "matches" : "teams"));
      })
      .catch((error) => {
        if (hasData.current) {
          toast.error("No pudimos actualizar el torneo", {
            description: "Lo que ves puede estar desactualizado. Recargá la página.",
          });
          return;
        }
        setLoadError({
          message: apiErrorMessage(error, "No pudimos cargar el torneo. Volvé a intentarlo."),
          notFound: isNotFoundError(error),
        });
      });
  }, [tournamentId]);

  useEffect(() => {
    hasData.current = false;
    setData(null);
    setTab(null);
    load();
  }, [load]);

  if (loadError) {
    return loadError.notFound ? (
      <ErrorState
        title="No encontramos este torneo"
        description="Puede que lo hayan eliminado. Volvé al Panel admin para ver la lista."
      />
    ) : (
      <ErrorState title="No pudimos cargar el torneo" description={loadError.message} onRetry={load} />
    );
  }
  if (!data || !tab) return <LoadingState label="Cargando torneo…" />;

  const { tournament, teams, groups, phases, matches } = data;

  return (
    <div className="flex flex-col gap-6">
      <Card className="px-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
              <TournamentStatusBadge status={tournament.status} />
              <span className="flex items-center gap-1.5">
                <Calendar className="size-4" aria-hidden="true" />
                {formatDateOnly(tournament.startDate)} – {formatDateOnly(tournament.endDate)}
              </span>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button asChild size="sm" variant="secondary">
                <Link to={`/tournaments/${tournament.id}`}>
                  <Eye aria-hidden="true" />
                  Ver como participante
                </Link>
              </Button>
              <Button asChild size="sm" variant="secondary">
                <Link to={`/rankings/tournament/${tournament.id}`}>
                  <BarChart3 aria-hidden="true" />
                  Ver ranking
                </Link>
              </Button>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <TournamentFormDialog
              tournament={tournament}
              onSaved={load}
              trigger={
                <Button variant="outline">
                  <Pencil aria-hidden="true" />
                  Editar
                </Button>
              }
            />
            <TournamentLifecycleActions tournament={tournament} matches={matches} onChanged={load} />
          </div>
        </div>
      </Card>

      <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
        <TabsList variant="pill" aria-label="Secciones del torneo">
          <TabsTrigger value="teams">
            <TabLabel label="Equipos" count={teams.length} />
          </TabsTrigger>
          <TabsTrigger value="groups">
            <TabLabel label="Grupos" count={groups.length} />
          </TabsTrigger>
          <TabsTrigger value="phases">
            <TabLabel label="Fases" count={phases.length} />
          </TabsTrigger>
          <TabsTrigger value="matches">
            <TabLabel label="Partidos" count={matches.length} />
          </TabsTrigger>
        </TabsList>

        <TabsContent value="teams" className="mt-3">
          <TeamsTab tournament={tournament} tournamentTeams={teams} onChanged={load} />
        </TabsContent>
        <TabsContent value="groups" className="mt-3">
          <GroupsTab
            tournament={tournament}
            groups={groups}
            matches={matches}
            tournamentTeams={teams}
            onChanged={load}
          />
        </TabsContent>
        <TabsContent value="phases" className="mt-3">
          <PhasesTab
            tournament={tournament}
            phases={phases}
            groups={groups}
            matches={matches}
            tournamentTeams={teams}
            onChanged={load}
          />
        </TabsContent>
        <TabsContent value="matches" className="mt-3">
          <MatchesTab
            tournament={tournament}
            matches={matches}
            groups={groups}
            phases={phases}
            tournamentTeams={teams}
            onChanged={load}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}
