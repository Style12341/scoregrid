import { describe, expect, it } from "vitest";
import { dateAtDefaultHour, dayAfterLatest, suggestedKickoff, toDateTimeLocal } from "./kickoff";

const now = new Date(2026, 9, 8, 15, 30); // 8 Oct 2026, 15:30 local

describe("suggestedKickoff", () => {
  it("is tomorrow at 21:00 when nothing says otherwise", () => {
    expect(suggestedKickoff(null, now)).toBe("2026-10-09T21:00");
  });

  it("puts the next round one day after the source round's last match, same time of day", () => {
    const quarterFinals = [new Date(2026, 10, 7, 18, 0), new Date(2026, 10, 8, 20, 0), new Date(2026, 10, 7, 20, 0)];

    expect(suggestedKickoff(dayAfterLatest(quarterFinals.map((kickoff) => kickoff.toISOString())), now)).toBe(
      "2026-11-09T20:00",
    );
  });

  it("never suggests earlier than the default, even after a round already played", () => {
    const lastMatch = toDateTimeLocal(new Date(2026, 9, 1, 18, 0));

    expect(suggestedKickoff(dayAfterLatest([lastMatch]), now)).toBe("2026-10-09T21:00");
  });

  it("starts a fixture on the tournament's start date at 21:00 when that is later", () => {
    expect(suggestedKickoff(dateAtDefaultHour("2026-10-22"), now)).toBe("2026-10-22T21:00");
    expect(suggestedKickoff(dateAtDefaultHour("2026-10-01"), now)).toBe("2026-10-09T21:00");
  });
});
