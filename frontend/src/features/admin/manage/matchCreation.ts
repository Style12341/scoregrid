import { useRef, useState } from "react";
import { toast } from "sonner";
import { createMatch } from "@/features/tournaments/api/tournaments";
import { isServiceUnavailable, requestErrorMessage } from "@/features/tournaments/errors";
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

export interface BatchResult {
  total: number;
  created: number;
  /** Matches the backend refused; the batch went on after each. */
  failures: MatchCreationFailure[];
  /** Matches never sent because the batch stopped early. */
  notAttempted: number;
  /** Why the batch stopped early (no service, no network), or null when it ran to the end. */
  stoppedBecause: string | null;
}

/** "4 de 6 creados, 1 con error, 1 sin intentar". */
export function summarizeBatch(result: BatchResult): string {
  return [
    `${result.created} de ${result.total} creados`,
    result.failures.length > 0 ? `${result.failures.length} con error` : null,
    result.notAttempted > 0 ? `${result.notAttempted} sin intentar` : null,
  ]
    .filter(Boolean)
    .join(", ");
}

function isComplete(result: BatchResult): boolean {
  return result.created === result.total;
}

/**
 * Creates the matches one at a time through the regular endpoint, so every
 * backend rule still applies to each of them. A refused match does not stop
 * the rest. An unavailable service or network does: posting into an open
 * breaker would only fail the same way, so the rest are left unsent.
 */
export async function createMatchesInOrder(
  tournamentId: string,
  matches: MatchToCreate[],
  onProgress: (handled: number) => void,
): Promise<BatchResult> {
  const failures: MatchCreationFailure[] = [];
  let created = 0;
  for (const [index, match] of matches.entries()) {
    try {
      await createMatch(tournamentId, match.input);
      created += 1;
    } catch (error) {
      const reason = requestErrorMessage(error, "No se pudo crear.");
      failures.push({ label: match.label, reason });
      if (isServiceUnavailable(error)) {
        return {
          total: matches.length,
          created,
          failures,
          notAttempted: matches.length - index - 1,
          stoppedBecause: reason,
        };
      }
    }
    onProgress(index + 1);
  }
  return { total: matches.length, created, failures, notAttempted: 0, stoppedBecause: null };
}

interface ToastMessage {
  title: string;
  description: string;
}

/**
 * One batch of match creation for a dialog: ignores a second submit while a
 * batch runs, reports progress, refreshes the page data afterwards, toasts the
 * outcome, and keeps the details of a partial failure for the dialog to show.
 */
export function useMatchCreation({
  tournamentId,
  onCreated,
  onAllCreated,
}: {
  tournamentId: string;
  /** Reloads the page data after every batch; resolves to whether it succeeded. */
  onCreated: () => Promise<boolean>;
  /** Usually closes the dialog. */
  onAllCreated: () => void;
}) {
  // A ref, not state: two clicks in the same tick both see the old state.
  const inFlight = useRef(false);
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState<{ handled: number; total: number } | null>(null);
  const [result, setResult] = useState<BatchResult | null>(null);
  // The refresh after a batch goes through the same service that may have just
  // failed. Planning again from data that did not refresh would post matches
  // that already exist, so a failed refresh blocks retries until a page reload.
  const [staleData, setStaleData] = useState(false);

  /**
   * `prepare` says what to create and may first create what the matches need
   * (a phase). If it fails, nothing is sent.
   */
  async function run(prepare: () => Promise<MatchToCreate[]>, success: (created: number) => ToastMessage) {
    if (inFlight.current) return;
    inFlight.current = true;
    setRunning(true);
    setResult(null);
    try {
      const matches = await prepare();
      setProgress({ handled: 0, total: matches.length });
      const batch = await createMatchesInOrder(tournamentId, matches, (handled) =>
        setProgress({ handled, total: matches.length }),
      );
      const refreshed = await onCreated();
      if (isComplete(batch)) {
        const message = success(batch.created);
        toast.success(message.title, { description: message.description });
        onAllCreated();
        return;
      }
      setResult(batch);
      setStaleData(!refreshed);
      toast.error("Algunos partidos no se crearon", {
        description: `${summarizeBatch(batch)}. El detalle está en el diálogo.`,
      });
    } catch (error) {
      setStaleData(!(await onCreated()));
      setResult({
        total: 0,
        created: 0,
        failures: [],
        notAttempted: 0,
        stoppedBecause: requestErrorMessage(error, "No se pudo preparar la creación."),
      });
      toast.error("No se creó ningún partido", { description: "El detalle está en el diálogo." });
    } finally {
      inFlight.current = false;
      setRunning(false);
      setProgress(null);
    }
  }

  return {
    running,
    progress,
    result,
    staleData,
    run,
    // A stale flag outlives the dialog closing: only a page reload clears it.
    reset: () => setResult(null),
  };
}
