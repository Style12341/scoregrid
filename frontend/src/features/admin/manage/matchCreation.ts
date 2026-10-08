import { useState } from "react";
import { createMatch } from "@/features/tournaments/api/tournaments";
import { apiErrorMessage } from "@/features/tournaments/errors";
import type { CreateMatchInput } from "@/features/tournaments/types/tournament";

export interface MatchToCreate {
  input: CreateMatchInput;
  /** How the admin recognises it in a failure list, e.g. "Atlético Central – Ribera FC". */
  label: string;
}

export interface MatchCreationFailure {
  label: string;
  reason: string;
}

/**
 * Creates the matches one at a time through the regular endpoint, so every
 * backend rule still applies to each of them. A failure does not stop the
 * rest; the failures come back for the admin to read.
 */
export async function createMatchesInOrder(
  tournamentId: string,
  matches: MatchToCreate[],
  onProgress: (created: number) => void,
): Promise<MatchCreationFailure[]> {
  const failures: MatchCreationFailure[] = [];
  for (const [index, match] of matches.entries()) {
    try {
      await createMatch(tournamentId, match.input);
    } catch (error) {
      failures.push({ label: match.label, reason: apiErrorMessage(error, "No se pudo crear.") });
    }
    onProgress(index + 1);
  }
  return failures;
}

/** Progress and failures of one createMatchesInOrder run, for a dialog to render. */
export function useMatchCreation(tournamentId: string) {
  /** Matches handled so far in the running batch; null while idle. */
  const [handled, setHandled] = useState<number | null>(null);
  const [failures, setFailures] = useState<MatchCreationFailure[]>([]);

  async function create(matches: MatchToCreate[]): Promise<MatchCreationFailure[]> {
    setFailures([]);
    setHandled(0);
    try {
      const result = await createMatchesInOrder(tournamentId, matches, setHandled);
      setFailures(result);
      return result;
    } finally {
      setHandled(null);
    }
  }

  return {
    creating: handled !== null,
    handled: handled ?? 0,
    failures,
    create,
    reset: () => setFailures([]),
  };
}
