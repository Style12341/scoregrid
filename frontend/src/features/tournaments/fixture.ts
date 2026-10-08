/**
 * Round-robin planning for one group. The backend has no tournament formats
 * (docs/PRD.md): the admin UI proposes the matches and creates them one by one
 * through the regular create-match endpoint.
 */
import type { Match, TeamRef } from "./types/tournament";

export interface Pairing {
  home: TeamRef;
  away: TeamRef;
}

export interface PlannedMatch extends Pairing {
  /** 1-based, counting only the rounds that still have matches to create. */
  round: number;
  startTime: Date;
}

/**
 * Circle method: the first team keeps its seat and the others rotate one seat
 * per round, so every team plays once per round and meets every other team
 * exactly once. An odd team count adds an empty seat: whoever faces it rests.
 */
export function roundRobinRounds(teams: TeamRef[]): Pairing[][] {
  const seats: (TeamRef | null)[] = teams.length % 2 === 0 ? [...teams] : [...teams, null];
  const rounds: Pairing[][] = [];

  for (let round = 0; round < seats.length - 1; round++) {
    const pairings: Pairing[] = [];
    for (let seat = 0; seat < seats.length / 2; seat++) {
      const first = seats[seat];
      const second = seats[seats.length - 1 - seat];
      if (!first || !second) continue;
      // The fixed seat alternates home and away, so its team does not host every round.
      const alternate = seat === 0 && round % 2 === 1;
      pairings.push(alternate ? { home: second, away: first } : { home: first, away: second });
    }
    rounds.push(pairings);
    const last = seats.pop() ?? null;
    seats.splice(1, 0, last);
  }
  return rounds;
}

/** The same pair of teams, whichever of them plays at home. */
function pairingKey(firstTeamId: string, secondTeamId: string): string {
  return [firstTeamId, secondTeamId].sort().join(":");
}

/** The same wall-clock time `days` later, also across daylight-saving changes. */
function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

/**
 * The group's round robin minus every pairing that already has a match that
 * was not cancelled. Rounds left empty are dropped so the dates stay
 * consecutive: round N kicks off `daysBetweenRounds * (N - 1)` days after
 * `firstKickoff`, all of its matches at the same time.
 */
export function planRoundRobin(
  teams: TeamRef[],
  groupMatches: Match[],
  firstKickoff: Date,
  daysBetweenRounds: number,
): PlannedMatch[] {
  const existingPairings = new Set(
    groupMatches
      .filter((match) => match.status !== "CANCELLED")
      .map((match) => pairingKey(match.homeTeam.id, match.awayTeam.id)),
  );

  return roundRobinRounds(teams)
    .map((round) =>
      round.filter((pairing) => !existingPairings.has(pairingKey(pairing.home.id, pairing.away.id))),
    )
    .filter((round) => round.length > 0)
    .flatMap((round, index) => {
      const startTime = addDays(firstKickoff, index * daysBetweenRounds);
      return round.map((pairing) => ({ ...pairing, round: index + 1, startTime }));
    });
}
