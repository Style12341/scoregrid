import { describe, expect, it } from "vitest";
import { computeStandings } from "./standings";
import { match, team } from "./testFixtures";

const order = (rows: ReturnType<typeof computeStandings>) => rows.map((row) => row.team.id);

describe("computeStandings", () => {
  it("counts only FINISHED matches: 3 a win, 1 a draw, 0 a loss", () => {
    const rows = computeStandings(
      [team(1), team(2), team(3)],
      [
        match(1, 2, { score: [2, 0] }),
        match(2, 3, { score: [1, 1] }),
        match(1, 3, { status: "IN_PROGRESS", score: [5, 0] }),
        match(3, 1),
      ],
    );
    const byTeam = Object.fromEntries(rows.map((row) => [row.team.id, row]));

    expect(byTeam["1"]).toMatchObject({ played: 1, won: 1, points: 3, goalsFor: 2, goalDifference: 2 });
    expect(byTeam["2"]).toMatchObject({ played: 2, drawn: 1, lost: 1, points: 1, goalDifference: -2 });
    expect(byTeam["3"]).toMatchObject({ played: 1, drawn: 1, points: 1, goalsFor: 1 });
  });

  it("orders teams level on points by goal difference, then goals for", () => {
    // A cycle where every team wins once and loses once: all on 3 points.
    const rows = computeStandings(
      [team(1), team(2), team(3), team(4)],
      [
        match(1, 2, { score: [1, 0] }),
        match(2, 3, { score: [3, 0] }),
        match(3, 4, { score: [2, 1] }),
        match(4, 1, { score: [3, 2] }),
      ],
    );

    expect(rows.map((row) => row.points)).toEqual([3, 3, 3, 3]);
    // 2: +2. Then 4 and 1 on 0, 4 with more goals for. Then 3: -2.
    expect(order(rows)).toEqual(["2", "4", "1", "3"]);
  });

  it("orders teams level on everything by name, and lists those without results with zeros", () => {
    const rows = computeStandings([team(2), team(1)], []);

    expect(order(rows)).toEqual(["1", "2"]);
    expect(rows.every((row) => row.played === 0 && row.points === 0)).toBe(true);
  });
});
