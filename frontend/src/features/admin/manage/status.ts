import type { TournamentStatus } from "@/features/tournaments/types/tournament";

/** Teams, groups, phases and matches can be changed while DRAFT or ACTIVE. */
export function isConfigurable(status: TournamentStatus): boolean {
  return status === "DRAFT" || status === "ACTIVE";
}

export function toggleId(ids: string[], id: string): string[] {
  return ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id];
}
