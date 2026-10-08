/**
 * Kickoff values for `<input type="datetime-local">`, which reads and writes
 * local wall-clock time without an offset ("2026-10-09T21:00").
 */

/** Evening kickoff the date pickers start on. */
const DEFAULT_KICKOFF_HOUR = 21;
const MILLISECONDS_PER_MINUTE = 60_000;

export function toDateTimeLocal(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * MILLISECONDS_PER_MINUTE);
  return local.toISOString().slice(0, "YYYY-MM-DDTHH:mm".length);
}

/** Tomorrow at the default kickoff hour, local time. */
export function defaultKickoff(): string {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(DEFAULT_KICKOFF_HOUR, 0, 0, 0);
  return toDateTimeLocal(tomorrow);
}

/** Knockout matches created together are spaced out so they can be followed one after another. */
export const MINUTES_BETWEEN_STAGGERED_KICKOFFS = 120;

/** The kickoff of the `index`-th (0-based) of several matches starting at `first`. */
export function staggeredKickoff(first: string, index: number): Date {
  return new Date(
    new Date(first).getTime() + index * MINUTES_BETWEEN_STAGGERED_KICKOFFS * MILLISECONDS_PER_MINUTE,
  );
}

/** tournament-service only accepts a new match whose kickoff is in the future. */
export function isFutureKickoff(value: string): boolean {
  const kickoff = new Date(value).getTime();
  return !Number.isNaN(kickoff) && kickoff > Date.now();
}
