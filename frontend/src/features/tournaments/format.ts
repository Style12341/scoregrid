/**
 * Spanish (Argentina) labels and dates for tournament data, shared by the
 * player and admin screens so the same phase or date never reads two ways.
 */
import type { Match, PhaseType, TeamRef } from "./types/tournament";

const LOCALE = "es-AR";

/** Tournament dates are plain `YYYY-MM-DD`; parsing them as UTC shifts the day. */
export function formatDateOnly(dateStr: string | null): string {
  if (!dateStr) return "Sin fecha";
  const [year, month, day] = dateStr.split("T")[0].split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(LOCALE, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/** Match kickoff, e.g. "vie, 9 oct, 21:00". */
export function formatKickoff(iso: string): string {
  return new Date(iso).toLocaleString(LOCALE, {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export const PHASE_TYPES: { value: PhaseType; label: string }[] = [
  { value: "GROUP_STAGE", label: "Fase de grupos" },
  { value: "ROUND_OF_16", label: "Octavos de final" },
  { value: "QUARTER_FINAL", label: "Cuartos de final" },
  { value: "SEMI_FINAL", label: "Semifinal" },
  { value: "THIRD_PLACE", label: "Tercer puesto" },
  { value: "FINAL", label: "Final" },
];

export function phaseTypeLabel(type: PhaseType): string {
  return PHASE_TYPES.find((phase) => phase.value === type)?.label ?? type;
}

/** A phase's own name when it has one ("Semifinal ida"), else its type. */
export function phaseLabel(phase: { name: string | null; type: PhaseType }): string {
  return phase.name || phaseTypeLabel(phase.type);
}

/** The short code shown next to a full team name, when it differs. */
export function teamCode(team: Pick<TeamRef, "name" | "shortName">): string | null {
  return team.shortName && team.shortName !== team.name ? team.shortName : null;
}

export function hasResult(match: Match): match is Match & { homeScore: number; awayScore: number } {
  return match.homeScore !== null && match.awayScore !== null;
}

/** Can still be played or scored: neither finished nor cancelled. */
export function isPendingMatch(match: Match): boolean {
  return match.status === "SCHEDULED" || match.status === "IN_PROGRESS" || match.status === "POSTPONED";
}

/** The matches played in one group. */
export function matchesInGroup(matches: Match[], groupId: string): Match[] {
  return matches.filter((match) => match.groupId === groupId);
}

/** Earliest kickoff first. */
export function byKickoff(a: Match, b: Match): number {
  return new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
}

/** Statuses that accept a result (admin): docs/contracts.md, PUT /api/matches/{id}/result. */
export function acceptsResult(match: Match): boolean {
  return (
    match.status === "SCHEDULED" ||
    match.status === "IN_PROGRESS" ||
    match.status === "FINISHED"
  );
}
