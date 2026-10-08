import { createPhase } from "@/features/tournaments/api/tournaments";
import type { Phase, PhaseType } from "@/features/tournaments/types/tournament";

/**
 * The phase id for each type, by type. An existing phase of that type is
 * reused (the first in display order); a missing one is created after the
 * last phase, named after its type.
 */
export async function ensurePhases(
  tournamentId: string,
  phases: Phase[],
  types: PhaseType[],
): Promise<Record<string, string>> {
  const phaseIdByType: Record<string, string> = {};
  let nextDisplayOrder = Math.max(0, ...phases.map((phase) => phase.displayOrder)) + 1;

  for (const type of types) {
    const existing = phases
      .filter((phase) => phase.type === type)
      .sort((a, b) => a.displayOrder - b.displayOrder)[0];
    if (existing) {
      phaseIdByType[type] = existing.id;
      continue;
    }
    const created = await createPhase(tournamentId, { type, displayOrder: nextDisplayOrder });
    nextDisplayOrder += 1;
    phaseIdByType[type] = created.id;
  }
  return phaseIdByType;
}
