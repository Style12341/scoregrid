import { describe, expect, it } from "vitest";
import {
  findKnockoutSource,
  proposeFromGroups,
  proposeFromRound,
  undecidedMatches,
  withoutExistingMatches,
  type KnockoutSource,
  type ProposedMatch,
} from "./knockout";
import { computeStandings } from "./standings";
import { match, phase, team } from "./testFixtures";

const describeProposals = (proposals: ProposedMatch[]) =>
  proposals.map(
    (proposal) => `${proposal.phaseType} ${proposal.home?.id ?? "?"}-${proposal.away?.id ?? "?"}`,
  );

const cupPhases = [
  phase("qf", "QUARTER_FINAL", 1),
  phase("sf", "SEMI_FINAL", 2),
  phase("third", "THIRD_PLACE", 3),
  phase("final", "FINAL", 4),
];

// Listed out of kickoff order on purpose; the third one is a draw.
const quarterFinals = [
  match(3, 4, { phaseId: "qf", score: [0, 2], startTime: "2026-10-10T20:00:00Z" }),
  match(1, 8, { phaseId: "qf", score: [1, 0], startTime: "2026-10-10T18:00:00Z" }),
  match(2, 7, { phaseId: "qf", score: [1, 1], startTime: "2026-10-11T18:00:00Z" }),
  match(5, 6, { phaseId: "qf", score: [3, 1], startTime: "2026-10-11T20:00:00Z" }),
];

function roundSource(phases = cupPhases, matches = quarterFinals) {
  const source = findKnockoutSource(phases, matches, 0);
  if (source?.kind !== "round") throw new Error(`expected a knockout round, got ${JSON.stringify(source)}`);
  return source;
}

/** Four groups of four where team N1 wins the group and N2 is runner-up (A = 1x, B = 2x…). */
function groupTables() {
  return [1, 2, 3, 4].map((group) => {
    const [first, second, third, fourth] = [1, 2, 3, 4].map((position) => group * 10 + position);
    return computeStandings(
      [first, second, third, fourth].map(team),
      [
        match(first, second, { score: [2, 0] }),
        match(first, third, { score: [2, 0] }),
        match(first, fourth, { score: [2, 0] }),
        match(second, third, { score: [1, 0] }),
        match(second, fourth, { score: [1, 0] }),
        match(third, fourth, { score: [0, 0] }),
      ],
    );
  });
}

describe("proposeFromGroups", () => {
  it("crosses winners and runners-up of paired groups, A and B in opposite halves", () => {
    expect(describeProposals(proposeFromGroups(groupTables()))).toEqual([
      "QUARTER_FINAL 11-22",
      "QUARTER_FINAL 31-42",
      "QUARTER_FINAL 21-12",
      "QUARTER_FINAL 41-32",
    ]);
  });

  it("sends a single league's top four to semi-finals, 1st-4th and 2nd-3rd", () => {
    expect(describeProposals(proposeFromGroups(groupTables().slice(0, 1)))).toEqual([
      "SEMI_FINAL 11-14",
      "SEMI_FINAL 12-13",
    ]);
  });
});

describe("proposeFromRound", () => {
  it("pairs the winners of consecutive matches in kickoff order", () => {
    const source = roundSource();

    expect(source.round).toBe("QUARTER_FINAL");
    expect(describeProposals(proposeFromRound(source, {}))).toEqual([
      "SEMI_FINAL 1-4",
      "SEMI_FINAL ?-5",
    ]);
  });

  it("needs a pick after a draw, and uses it", () => {
    const source = roundSource();
    const [draw] = undecidedMatches(source.matches);

    expect(undecidedMatches(source.matches)).toHaveLength(1);
    expect(draw.homeTeam.id).toBe("2");
    expect(describeProposals(proposeFromRound(source, { [draw.id]: "7" }))).toEqual([
      "SEMI_FINAL 1-4",
      "SEMI_FINAL 7-5",
    ]);
  });

  it("after the semi-finals sends the losers to THIRD_PLACE, listed before the final", () => {
    const semiFinals = [
      match(1, 4, { phaseId: "sf", score: [2, 1], startTime: "2026-10-17T18:00:00Z" }),
      match(7, 5, { phaseId: "sf", score: [0, 1], startTime: "2026-10-17T20:00:00Z" }),
    ];
    const source = roundSource(cupPhases, [...quarterFinals, ...semiFinals]);

    expect(source.round).toBe("SEMI_FINAL");
    expect(describeProposals(proposeFromRound(source, {}))).toEqual([
      "THIRD_PLACE 4-7",
      "FINAL 1-5",
    ]);
  });
});

describe("re-running after a partial failure", () => {
  const oneSemiFinal = match(1, 4, { phaseId: "sf", score: undefined });

  it("keeps the source on the quarter-finals while the semi-finals are half created", () => {
    const source = roundSource(cupPhases, [...quarterFinals, oneSemiFinal]);

    expect(source.round).toBe("QUARTER_FINAL");
  });

  it("proposes only the semi-final that is still missing", () => {
    const matches = [...quarterFinals, oneSemiFinal];
    const source = roundSource(cupPhases, matches);
    const proposals = proposeFromRound(source, { [undecidedMatches(source.matches)[0].id]: "7" });

    expect(describeProposals(withoutExistingMatches(proposals, cupPhases, matches))).toEqual([
      "SEMI_FINAL 7-5",
    ]);
  });

  it("offers the third-place match alone when only the final was created", () => {
    const semiFinals = [
      match(1, 4, { phaseId: "sf", score: [2, 1] }),
      match(7, 5, { phaseId: "sf", score: [0, 1] }),
    ];
    const matches = [...quarterFinals, ...semiFinals, match(1, 5, { phaseId: "final" })];
    const source = roundSource(cupPhases, matches);

    expect(source.round).toBe("SEMI_FINAL");
    expect(describeProposals(withoutExistingMatches(proposeFromRound(source, {}), cupPhases, matches))).toEqual([
      "THIRD_PLACE 4-7",
    ]);
  });

  it("finds nothing left once the final and the third-place match exist", () => {
    const matches = [
      ...quarterFinals,
      match(1, 4, { phaseId: "sf", score: [2, 1] }),
      match(7, 5, { phaseId: "sf", score: [0, 1] }),
      match(4, 7, { phaseId: "third" }),
      match(1, 5, { phaseId: "final" }),
    ];

    expect(findKnockoutSource(cupPhases, matches, 0)).toBeNull();
  });

  it("stays on the group stage while its first knockout round is half created", () => {
    const halfBuilt = [match(11, 22, { phaseId: "qf" }), match(31, 42, { phaseId: "qf" })];
    const expected: KnockoutSource = { kind: "groups" };

    expect(findKnockoutSource(cupPhases, halfBuilt, 4)).toEqual(expected);
  });
});
