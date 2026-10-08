import { describe, expect, it } from "vitest";
import { planRoundRobin, roundRobinRounds, type Pairing } from "./fixture";
import { match, team } from "./testFixtures";

const pairingKey = (pairing: Pairing) => [pairing.home.id, pairing.away.id].sort().join(":");
const teams = (count: number) => Array.from({ length: count }, (_, index) => team(index + 1));

describe("roundRobinRounds", () => {
  it.each([4, 10])("with %i teams every pair meets exactly once, one match per team per round", (count) => {
    const rounds = roundRobinRounds(teams(count));
    const pairings = rounds.flat();

    expect(rounds).toHaveLength(count - 1);
    expect(pairings).toHaveLength((count * (count - 1)) / 2);
    expect(new Set(pairings.map(pairingKey)).size).toBe(pairings.length);
    for (const round of rounds) {
      const teamIds = round.flatMap((pairing) => [pairing.home.id, pairing.away.id]);
      expect(new Set(teamIds).size).toBe(teamIds.length);
    }
  });

  it("with an odd count one team rests each round and every pair still meets once", () => {
    const rounds = roundRobinRounds(teams(5));
    const pairings = rounds.flat();

    expect(rounds).toHaveLength(5);
    expect(rounds.every((round) => round.length === 2)).toBe(true);
    expect(new Set(pairings.map(pairingKey)).size).toBe(10);
  });
});

describe("planRoundRobin", () => {
  const firstKickoff = new Date(2026, 9, 10, 21, 0);

  it("skips pairs that already have a match, in either order, but not cancelled ones", () => {
    const existing = [
      match(2, 1, { score: [1, 0], groupId: "g" }),
      match(3, 4, { status: "CANCELLED", groupId: "g" }),
    ];

    const plan = planRoundRobin(teams(4), existing, firstKickoff, 7);
    const keys = plan.map(pairingKey);

    expect(plan).toHaveLength(5);
    expect(keys).not.toContain("1:2");
    expect(keys).toContain("3:4");
  });

  it("drops rounds left empty and keeps the dates consecutive at the same wall-clock time", () => {
    const plan = planRoundRobin(teams(4), [match(1, 4, { groupId: "g" })], firstKickoff, 7);
    const rounds = [...new Set(plan.map((planned) => planned.round))];

    expect(rounds).toEqual([1, 2, 3]);
    expect(plan.every((planned) => planned.startTime.getHours() === 21)).toBe(true);
    expect(plan[plan.length - 1].startTime.getDate()).toBe(24);
  });
});
