/**
 * Kickoff values for `<input type="datetime-local">`, which reads and writes
 * local wall-clock time without an offset ("2026-10-09T21:00").
 */

/** Evening kickoff the date pickers start on. */
const DEFAULT_KICKOFF_HOUR = 21;
/** Knockout matches created together are spaced out so they can be followed one after another. */
export const HOURS_BETWEEN_STAGGERED_KICKOFFS = 2;

const MILLISECONDS_PER_MINUTE = 60_000;
const MILLISECONDS_PER_HOUR = 3_600_000;

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

/** The input holds a complete date and time. */
export function isValidKickoff(value: string): boolean {
  return !Number.isNaN(new Date(value).getTime());
}

/** tournament-service only accepts a new match whose kickoff is in the future. */
export function isFutureKickoff(value: string): boolean {
  return isValidKickoff(value) && new Date(value).getTime() > Date.now();
}

/** The kickoff of the `index`-th (0-based) of several matches starting at `first`. */
export function staggeredKickoff(first: string, index: number): Date {
  return new Date(new Date(first).getTime() + index * HOURS_BETWEEN_STAGGERED_KICKOFFS * MILLISECONDS_PER_HOUR);
}
