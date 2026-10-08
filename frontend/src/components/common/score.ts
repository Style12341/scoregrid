/** Goal limits from docs/contracts.md: scores are integers 0..99. */
export const MIN_SCORE = 0;
export const MAX_SCORE = 99;

/** Goals as typed: "" while empty, otherwise digits only. */
export function parseScore(value: string): number | null {
  if (value.trim() === "") return null;
  const score = Number(value);
  return Number.isInteger(score) && score >= MIN_SCORE && score <= MAX_SCORE ? score : null;
}
