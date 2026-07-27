/**
 * Number formatting for the ranking screens.
 *
 * Spanish (Argentina) locale: decimal comma, thousands point. Rule 10 in
 * AGENTS.md — code is English, everything the participant reads is Spanish,
 * and that includes how a number looks.
 */
const LOCALE = "es-AR";

const percentFormatter = new Intl.NumberFormat(LOCALE, {
  style: "percent",
  maximumFractionDigits: 0,
});

const decimalFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

const integerFormatter = new Intl.NumberFormat(LOCALE);

/** `accuracy` arrives as 0..1 from the contract, not as 0..100. */
export function formatAccuracy(accuracy: number): string {
  return percentFormatter.format(accuracy);
}

export function formatAverage(value: number): string {
  return decimalFormatter.format(value);
}

export function formatCount(value: number): string {
  return integerFormatter.format(value);
}
