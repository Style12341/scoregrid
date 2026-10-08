/**
 * The next knockout round, proposed in the browser from results already on
 * screen. The backend has no bracket logic (docs/PRD.md): the admin reviews
 * and edits the proposal, and the matches are created through the regular
 * create-match endpoint.
 */
import { byKickoff, hasResult } from "./format";
import type { StandingRow } from "./standings";
import type { Match, Phase, PhaseType, TeamRef } from "./types/tournament";

/** Main knockout rounds in playing order. THIRD_PLACE is played beside FINAL by the semi-final losers. */
export const KNOCKOUT_ROUNDS = ["ROUND_OF_16", "QUARTER_FINAL", "SEMI_FINAL", "FINAL"] as const;
export type KnockoutRound = (typeof KNOCKOUT_ROUNDS)[number];

const MATCHES_IN_ROUND: Record<KnockoutRound, number> = {
  ROUND_OF_16: 8,
  QUARTER_FINAL: 4,
  SEMI_FINAL: 2,
  FINAL: 1,
};

/** Standings positions that qualify from each group. */
const GROUP_WINNER = 1;
const GROUP_RUNNER_UP = 2;
/** A single group (a league) with at least four teams plays 1st–4th and 2nd–3rd. */
const LEAGUE_PLAYOFF_PAIRINGS = [
  [1, 4],
  [2, 3],
] as const;
const LEAGUE_PLAYOFF_TEAMS = 4;

/** Where the next round's teams come from. */
export type KnockoutSource =
  | { kind: "groups" }
  | { kind: "round"; round: KnockoutRound; matches: Match[] };

export interface ProposedMatch {
  phaseType: PhaseType;
  home: TeamRef | null;
  away: TeamRef | null;
}

/**
 * The latest knockout round that has matches, or the group stage when no
 * knockout match exists yet. Null when the final is already set, or when there
 * are neither groups nor knockout matches to start from.
 */
export function findKnockoutSource(
  phases: Phase[],
  matches: Match[],
  hasGroups: boolean,
): KnockoutSource | null {
  for (const round of [...KNOCKOUT_ROUNDS].reverse()) {
    const phaseIds = new Set(phases.filter((phase) => phase.type === round).map((phase) => phase.id));
    const roundMatches = matches.filter((match) => match.phaseId !== null && phaseIds.has(match.phaseId));
    if (roundMatches.length > 0) {
      return round === "FINAL" ? null : { kind: "round", round, matches: [...roundMatches].sort(byKickoff) };
    }
  }
  return hasGroups ? { kind: "groups" } : null;
}

function roundAfter(round: KnockoutRound): KnockoutRound {
  return KNOCKOUT_ROUNDS[Math.min(KNOCKOUT_ROUNDS.indexOf(round) + 1, KNOCKOUT_ROUNDS.length - 1)];
}

/** The smallest round with room for `matchCount` matches. */
function roundFor(matchCount: number): KnockoutRound {
  return [...KNOCKOUT_ROUNDS].reverse().find((round) => MATCHES_IN_ROUND[round] >= matchCount) ?? "ROUND_OF_16";
}

/** The team that went through on the score; null after a draw or without a result. */
function winnerOnScore(match: Match): TeamRef | null {
  if (match.status !== "FINISHED" || !hasResult(match) || match.homeScore === match.awayScore) {
    return null;
  }
  return match.homeScore > match.awayScore ? match.homeTeam : match.awayTeam;
}

/** Matches whose score does not say who went through (a draw, or cancelled): the admin picks. */
export function undecidedMatches(matches: Match[]): Match[] {
  return matches.filter((match) => winnerOnScore(match) === null);
}

/**
 * The team that went through: on the score, or the admin's pick (team id by
 * match id) when the score does not say, as after a draw decided on penalties.
 */
function advancingTeam(match: Match, picks: Record<string, string>): TeamRef | null {
  const winner = winnerOnScore(match);
  if (winner) return winner;
  return [match.homeTeam, match.awayTeam].find((team) => team.id === picks[match.id]) ?? null;
}

function eliminatedTeam(match: Match, advancing: TeamRef | null): TeamRef | null {
  if (!advancing) return null;
  return advancing.id === match.homeTeam.id ? match.awayTeam : match.homeTeam;
}

/** Consecutive teams meet: the 1st against the 2nd, the 3rd against the 4th, and so on. */
function pairInOrder(teams: (TeamRef | null)[], phaseType: PhaseType): ProposedMatch[] {
  const proposals: ProposedMatch[] = [];
  for (let index = 0; index < teams.length; index += 2) {
    proposals.push({ phaseType, home: teams[index], away: teams[index + 1] ?? null });
  }
  return proposals;
}

/**
 * The next round from a finished knockout round: winners of matches 1 and 2
 * meet, then 3 and 4, in kickoff order. After the semi-finals, the losers are
 * paired for third place too, listed first.
 */
export function proposeFromRound(
  source: Extract<KnockoutSource, { kind: "round" }>,
  picks: Record<string, string>,
): ProposedMatch[] {
  const advancing = source.matches.map((match) => advancingTeam(match, picks));
  const nextRound = pairInOrder(advancing, roundAfter(source.round));
  if (source.round !== "SEMI_FINAL") return nextRound;

  const eliminated = source.matches.map((match, index) => eliminatedTeam(match, advancing[index]));
  return [...pairInOrder(eliminated, "THIRD_PLACE"), ...nextRound];
}

function teamAt(table: StandingRow[], position: number): TeamRef | null {
  return table[position - 1]?.team ?? null;
}

/**
 * The first knockout round from the group tables, in group display order.
 * Groups pair up (A with B, C with D…) and cross: 1A–2B and 1C–2D fill one
 * half of the bracket, 1B–2A and 1D–2C the other, so teams from the same group
 * can only meet again in the final. A group left without a partner plays its
 * own winner against its runner-up; a single group (a league) plays 1st–4th
 * and 2nd–3rd.
 */
export function proposeFromGroups(tables: StandingRow[][]): ProposedMatch[] {
  let pairings: [TeamRef | null, TeamRef | null][];

  if (tables.length === 1) {
    const [table] = tables;
    pairings =
      table.length >= LEAGUE_PLAYOFF_TEAMS
        ? LEAGUE_PLAYOFF_PAIRINGS.map(([home, away]) => [teamAt(table, home), teamAt(table, away)])
        : [[teamAt(table, GROUP_WINNER), teamAt(table, GROUP_RUNNER_UP)]];
  } else {
    const firstHalf: [TeamRef | null, TeamRef | null][] = [];
    const secondHalf: [TeamRef | null, TeamRef | null][] = [];
    for (let index = 0; index < tables.length; index += 2) {
      const table = tables[index];
      const partner = tables[index + 1];
      if (!partner) {
        secondHalf.push([teamAt(table, GROUP_WINNER), teamAt(table, GROUP_RUNNER_UP)]);
        continue;
      }
      firstHalf.push([teamAt(table, GROUP_WINNER), teamAt(partner, GROUP_RUNNER_UP)]);
      secondHalf.push([teamAt(partner, GROUP_WINNER), teamAt(table, GROUP_RUNNER_UP)]);
    }
    pairings = [...firstHalf, ...secondHalf];
  }

  const round = roundFor(pairings.length);
  return pairings.map(([home, away]) => ({ phaseType: round, home, away }));
}
