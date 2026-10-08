/**
 * The next knockout round, proposed in the browser from results already on
 * screen. The backend has no bracket logic (docs/PRD.md): the admin reviews
 * and edits the proposal, and the matches are created through the regular
 * create-match endpoint.
 *
 * Everything here is derived from the current matches, so after a batch that
 * stopped half-way the same proposal minus what already exists comes back:
 * re-running creates only what is missing. Cancelled matches count as never
 * played, so a cancelled knockout match is proposed again.
 */
import { byKickoff, hasResult } from "./format";
import type { StandingRow } from "./standings";
import type { Match, Phase, PhaseType, TeamRef } from "./types/tournament";

/** Main knockout rounds in playing order. THIRD_PLACE is played beside FINAL by the semi-final losers. */
export const KNOCKOUT_ROUNDS = ["ROUND_OF_16", "QUARTER_FINAL", "SEMI_FINAL", "FINAL"] as const;
type KnockoutRound = (typeof KNOCKOUT_ROUNDS)[number];
/** Every round but the final leads to another one. */
type SourceRound = Exclude<KnockoutRound, "FINAL">;

const NEXT_ROUND: Record<SourceRound, KnockoutRound> = {
  ROUND_OF_16: "QUARTER_FINAL",
  QUARTER_FINAL: "SEMI_FINAL",
  SEMI_FINAL: "FINAL",
};

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
  | { kind: "round"; round: SourceRound; matches: Match[] };

/** Home against away, either still to be decided. */
interface OpenPairing {
  home: TeamRef | null;
  away: TeamRef | null;
}

export interface ProposedMatch extends OpenPairing {
  phaseType: PhaseType;
}

/** The matches, not cancelled, played in any phase of `type`. */
function matchesOfType(phases: Phase[], matches: Match[], type: PhaseType): Match[] {
  const phaseIds = new Set(phases.filter((phase) => phase.type === type).map((phase) => phase.id));
  return matches.filter(
    (match) => match.status !== "CANCELLED" && match.phaseId !== null && phaseIds.has(match.phaseId),
  );
}

/**
 * How many matches the first knockout round gets from the group stage: one per
 * group, or two for a single league. A league of fewer than four teams plays a
 * single final, so its playoff never looks complete; the dialog then simply
 * finds nothing left to create.
 */
function firstRoundSizeFromGroups(groupCount: number): number {
  return groupCount === 1 ? LEAGUE_PLAYOFF_PAIRINGS.length : groupCount;
}

/** Whether the round after `round` already has every match it needs, third place included. */
function isNextRoundComplete(round: SourceRound, roundMatches: Match[], phases: Phase[], matches: Match[]) {
  const winnersPairedUp = Math.ceil(roundMatches.length / 2);
  if (matchesOfType(phases, matches, NEXT_ROUND[round]).length < winnersPairedUp) return false;
  if (round !== "SEMI_FINAL") return true;
  // Third place is optional: only a THIRD_PLACE phase without its match is missing something.
  const hasThirdPlacePhase = phases.some((phase) => phase.type === "THIRD_PLACE");
  return !hasThirdPlacePhase || matchesOfType(phases, matches, "THIRD_PLACE").length > 0;
}

/**
 * The earliest stage whose following round is not complete yet: the group
 * stage while the first knockout round is missing matches, else the first
 * knockout round whose next round is. Null when the final (and third place,
 * if its phase exists) is set, or when there is nothing to start from.
 */
export function findKnockoutSource(
  phases: Phase[],
  matches: Match[],
  groupCount: number,
): KnockoutSource | null {
  const rounds = KNOCKOUT_ROUNDS.map((round) => ({
    round,
    matches: matchesOfType(phases, matches, round),
  })).filter((entry) => entry.matches.length > 0);

  if (rounds.length === 0) return groupCount > 0 ? { kind: "groups" } : null;
  if (groupCount > 0 && rounds[0].matches.length < firstRoundSizeFromGroups(groupCount)) {
    return { kind: "groups" };
  }
  for (const { round, matches: roundMatches } of rounds) {
    if (round === "FINAL") return null;
    if (!isNextRoundComplete(round, roundMatches, phases, matches)) {
      return { kind: "round", round, matches: [...roundMatches].sort(byKickoff) };
    }
  }
  return null;
}

/** The matches the next round is decided from: the source round's, or every group match. */
export function sourceMatches(source: KnockoutSource, matches: Match[]): Match[] {
  return source.kind === "round"
    ? source.matches
    : matches.filter((match) => match.groupId !== null && match.status !== "CANCELLED");
}

/** The team that went through on the score; null after a draw or without a result. */
function winnerOnScore(match: Match): TeamRef | null {
  if (match.status !== "FINISHED" || !hasResult(match) || match.homeScore === match.awayScore) {
    return null;
  }
  return match.homeScore > match.awayScore ? match.homeTeam : match.awayTeam;
}

/** Matches whose score does not say who went through (a draw): the admin picks. */
export function undecidedMatches(matches: Match[]): Match[] {
  return matches.filter((match) => winnerOnScore(match) === null);
}

/** The team that went through: on the score, or the admin's pick (team id by match id). */
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
 * The next round from a knockout round: winners of matches 1 and 2 meet, then
 * 3 and 4, in kickoff order. After the semi-finals, the losers are paired for
 * third place too, listed first.
 */
export function proposeFromRound(
  source: Extract<KnockoutSource, { kind: "round" }>,
  picks: Record<string, string>,
): ProposedMatch[] {
  const advancing = source.matches.map((match) => advancingTeam(match, picks));
  const nextRound = pairInOrder(advancing, NEXT_ROUND[source.round]);
  if (source.round !== "SEMI_FINAL") return nextRound;

  const eliminated = source.matches.map((match, index) => eliminatedTeam(match, advancing[index]));
  return [...pairInOrder(eliminated, "THIRD_PLACE"), ...nextRound];
}

function teamAt(table: StandingRow[], position: number): TeamRef | null {
  return table[position - 1]?.team ?? null;
}

/** The smallest round with room for `matchCount` matches. */
function roundFor(matchCount: number): KnockoutRound {
  return [...KNOCKOUT_ROUNDS].reverse().find((round) => MATCHES_IN_ROUND[round] >= matchCount) ?? "ROUND_OF_16";
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
  let pairings: OpenPairing[];

  if (tables.length === 1) {
    const [table] = tables;
    pairings =
      table.length >= LEAGUE_PLAYOFF_TEAMS
        ? LEAGUE_PLAYOFF_PAIRINGS.map(([home, away]) => ({ home: teamAt(table, home), away: teamAt(table, away) }))
        : [{ home: teamAt(table, GROUP_WINNER), away: teamAt(table, GROUP_RUNNER_UP) }];
  } else {
    const firstHalf: OpenPairing[] = [];
    const secondHalf: OpenPairing[] = [];
    for (let index = 0; index < tables.length; index += 2) {
      const table = tables[index];
      const partner = tables[index + 1];
      if (!partner) {
        secondHalf.push({ home: teamAt(table, GROUP_WINNER), away: teamAt(table, GROUP_RUNNER_UP) });
        continue;
      }
      firstHalf.push({ home: teamAt(table, GROUP_WINNER), away: teamAt(partner, GROUP_RUNNER_UP) });
      secondHalf.push({ home: teamAt(partner, GROUP_WINNER), away: teamAt(table, GROUP_RUNNER_UP) });
    }
    pairings = [...firstHalf, ...secondHalf];
  }

  const round = roundFor(pairings.length);
  return pairings.map((pairing) => ({ phaseType: round, ...pairing }));
}

/**
 * Drops every proposal with a team that already plays a (not cancelled) match
 * in a phase of the proposal's type: that match exists, so re-running after a
 * partial failure creates only what is still missing.
 */
export function withoutExistingMatches(
  proposals: ProposedMatch[],
  phases: Phase[],
  matches: Match[],
): ProposedMatch[] {
  const playing = new Set(
    [...new Set(proposals.map((proposal) => proposal.phaseType))].flatMap((type) =>
      matchesOfType(phases, matches, type).flatMap((match) => [
        `${type}:${match.homeTeam.id}`,
        `${type}:${match.awayTeam.id}`,
      ]),
    ),
  );
  return proposals.filter(
    (proposal) =>
      ![proposal.home, proposal.away].some(
        (team) => team !== null && playing.has(`${proposal.phaseType}:${team.id}`),
      ),
  );
}
