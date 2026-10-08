/** Small builders for the pure-module tests: teams, matches and phases with only the fields that matter. */
import type { MatchStatus } from "@/components/common/StatusBadge";
import type { Match, Phase, PhaseType, TeamRef } from "./types/tournament";

export function team(id: number): TeamRef {
  return { id: String(id), name: `Equipo ${String(id).padStart(2, "0")}`, shortName: null };
}

let nextMatchId = 0;

export function match(
  home: number,
  away: number,
  options: {
    status?: MatchStatus;
    score?: [number, number];
    groupId?: string;
    phaseId?: string;
    startTime?: string;
  } = {},
): Match {
  nextMatchId += 1;
  const [homeScore, awayScore] = options.score ?? [null, null];
  return {
    id: String(nextMatchId),
    tournamentId: "1",
    groupId: options.groupId ?? null,
    phaseId: options.phaseId ?? null,
    homeTeam: team(home),
    awayTeam: team(away),
    startTime: options.startTime ?? "2026-10-10T18:00:00Z",
    status: options.status ?? (options.score ? "FINISHED" : "SCHEDULED"),
    homeScore,
    awayScore,
    predictionsOpen: false,
  };
}

export function phase(id: string, type: PhaseType, displayOrder: number): Phase {
  return { id, tournamentId: "1", name: null, type, displayOrder };
}
