import { Pencil, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { MatchStatusBadge } from "@/components/common/StatusBadge";
import { EmptyState } from "@/components/common/states";
import { MatchTeams, ScoreOrVs } from "@/features/tournaments/components/MatchTeams";
import type { Group, Match, Phase, Team, Tournament } from "@/features/tournaments/types/tournament";
import { acceptsResult, byKickoff, formatKickoff, phaseLabel } from "@/features/tournaments/format";
import { MatchResultForm } from "../components/MatchResultForm";
import { MatchFormDialog } from "./MatchFormDialog";
import { isConfigurable } from "./status";

function locationLabel(match: Match, groups: Group[], phases: Phase[]): string | null {
  if (match.groupId) return groups.find((group) => group.id === match.groupId)?.name ?? null;
  const phase = phases.find((p) => p.id === match.phaseId);
  return phase ? phaseLabel(phase) : null;
}

export function MatchesTab({
  tournament,
  matches,
  groups,
  phases,
  tournamentTeams,
  onChanged,
}: {
  tournament: Tournament;
  matches: Match[];
  groups: Group[];
  phases: Phase[];
  tournamentTeams: Team[];
  onChanged: () => void;
}) {
  const canEdit = isConfigurable(tournament.status);
  // GROUP_STAGE phases are not offered as a location (MatchFormDialog): group matches come from a group.
  const knockoutPhases = phases.filter((phase) => phase.type !== "GROUP_STAGE");
  const canCreate =
    canEdit && (groups.length > 0 || knockoutPhases.length > 0) && tournamentTeams.length >= 2;
  const sorted = [...matches].sort(byKickoff);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Partidos</CardTitle>
        <CardDescription>
          Cargá el resultado cuando termine cada partido: los puntajes se calculan solos.
        </CardDescription>
        {canEdit && (
          <CardAction>
            <MatchFormDialog
              tournamentId={tournament.id}
              groups={groups}
              phases={phases}
              tournamentTeams={tournamentTeams}
              onSaved={onChanged}
              trigger={
                <Button size="sm" variant="secondary" disabled={!canCreate}>
                  <Plus aria-hidden="true" />
                  Crear partido
                </Button>
              }
            />
          </CardAction>
        )}
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {canEdit && !canCreate && (
          <p className="text-sm text-muted-foreground">
            Para crear partidos, el torneo necesita al menos dos equipos y un grupo o una fase.
          </p>
        )}

        {sorted.length === 0 ? (
          <EmptyState
            title="Todavía no hay partidos"
            description="Creá partidos para armar el fixture del torneo."
          />
        ) : (
          sorted.map((match) => {
            const location = locationLabel(match, groups, phases);
            const editable = canEdit && match.status !== "CANCELLED" && match.status !== "FINISHED";
            return (
              <article
                key={match.id}
                aria-label={`${match.homeTeam.name} contra ${match.awayTeam.name}`}
                className="flex flex-col gap-3 rounded-lg border border-border bg-muted p-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                  <div className="flex items-center gap-2">
                    <MatchStatusBadge status={match.status} />
                    {location && (
                      <span className="font-semibold text-muted-foreground">{location}</span>
                    )}
                  </div>
                  <span className="text-xs text-muted-foreground">{formatKickoff(match.startTime)}</span>
                </div>

                <MatchTeams
                  homeTeam={match.homeTeam}
                  awayTeam={match.awayTeam}
                  center={<ScoreOrVs homeScore={match.homeScore} awayScore={match.awayScore} />}
                />

                {(acceptsResult(match) || editable) && (
                  <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-3">
                    {acceptsResult(match) ? (
                      <MatchResultForm
                        key={`${match.id}-${match.status}-${match.homeScore}-${match.awayScore}`}
                        match={match}
                        onSaved={onChanged}
                      />
                    ) : (
                      <span />
                    )}
                    {editable && (
                      <MatchFormDialog
                        tournamentId={tournament.id}
                        match={match}
                        groups={groups}
                        phases={phases}
                        tournamentTeams={tournamentTeams}
                        onSaved={onChanged}
                        trigger={
                          <Button size="sm" variant="ghost">
                            <Pencil aria-hidden="true" />
                            Editar partido
                          </Button>
                        }
                      />
                    )}
                  </div>
                )}
              </article>
            );
          })
        )}
      </CardContent>
    </Card>
  );
}
