/**
 * The sporting table of one group, computed in the browser from its finished
 * matches. The backend keeps no standings (docs/PRD.md); this is a UI helper.
 */
import { hasResult } from "./format";
import type { Match, TeamRef } from "./types/tournament";

export interface StandingRow {
  team: TeamRef;
  played: number;
  won: number;
  drawn: number;
  lost: number;
  goalsFor: number;
  goalsAgainst: number;
  goalDifference: number;
  points: number;
}

export const POINTS_PER_WIN = 3;
export const POINTS_PER_DRAW = 1;

function emptyRow(team: TeamRef): StandingRow {
  return {
    team,
    played: 0,
    won: 0,
    drawn: 0,
    lost: 0,
    goalsFor: 0,
    goalsAgainst: 0,
    goalDifference: 0,
    points: 0,
  };
}

function recordResult(row: StandingRow, scored: number, conceded: number) {
  row.played += 1;
  row.goalsFor += scored;
  row.goalsAgainst += conceded;
  row.goalDifference = row.goalsFor - row.goalsAgainst;
  if (scored > conceded) {
    row.won += 1;
    row.points += POINTS_PER_WIN;
  } else if (scored === conceded) {
    row.drawn += 1;
    row.points += POINTS_PER_DRAW;
  } else {
    row.lost += 1;
  }
}

/**
 * From FINISHED matches only; a loss earns no points. Sorted by points, goal
 * difference, goals for, then name. Every team in `teams` gets a row, even
 * before its first match; a team that only appears in a match is added too.
 */
export function computeStandings(teams: TeamRef[], matches: Match[]): StandingRow[] {
  const rows = new Map<string, StandingRow>();
  const rowFor = (team: TeamRef) => {
    let row = rows.get(team.id);
    if (!row) {
      row = emptyRow(team);
      rows.set(team.id, row);
    }
    return row;
  };

  teams.forEach(rowFor);
  for (const match of matches) {
    if (match.status !== "FINISHED" || !hasResult(match)) continue;
    recordResult(rowFor(match.homeTeam), match.homeScore, match.awayScore);
    recordResult(rowFor(match.awayTeam), match.awayScore, match.homeScore);
  }

  return [...rows.values()].sort(
    (a, b) =>
      b.points - a.points ||
      b.goalDifference - a.goalDifference ||
      b.goalsFor - a.goalsFor ||
      a.team.name.localeCompare(b.team.name, "es"),
  );
}
