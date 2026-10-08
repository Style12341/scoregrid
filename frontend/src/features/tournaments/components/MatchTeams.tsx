import type { ReactNode } from "react";
import type { TeamRef } from "../types/tournament";
import { teamCode } from "../format";
import { cn } from "@/lib/utils";

function TeamName({ team, align }: { team: TeamRef; align: "left" | "right" }) {
  const code = teamCode(team);
  return (
    <span
      className={cn(
        "flex min-w-0 flex-1 flex-col",
        align === "right" ? "items-end text-right" : "items-start text-left",
      )}
    >
      <span className="truncate font-bold">{team.name}</span>
      {code && <span className="text-xs font-semibold text-muted-foreground">{code}</span>}
    </span>
  );
}

/**
 * "Home  2 – 1  Away" with full team names and the short code underneath.
 * `center` replaces the score, e.g. with score inputs or "vs".
 */
export function MatchTeams({
  homeTeam,
  awayTeam,
  center,
  className,
}: {
  homeTeam: TeamRef;
  awayTeam: TeamRef;
  center: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex items-center gap-4", className)}>
      <TeamName team={homeTeam} align="right" />
      <span className="shrink-0 text-center">{center}</span>
      <TeamName team={awayTeam} align="left" />
    </div>
  );
}

/** The centre of a MatchTeams row: the result when there is one, else "vs". */
export function ScoreOrVs({
  homeScore,
  awayScore,
}: {
  homeScore: number | null;
  awayScore: number | null;
}) {
  if (homeScore === null || awayScore === null) {
    return <span className="text-sm font-semibold text-muted-foreground">vs</span>;
  }
  return (
    <span className="text-xl font-extrabold tabular-nums">
      {homeScore} – {awayScore}
    </span>
  );
}
