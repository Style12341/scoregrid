import { clsx } from "clsx";
import type { ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Merge Tailwind classes so a caller's className always wins over a component's
 * defaults. Without twMerge, `px-4` and `px-8` both land in the class list and
 * the winner is whichever CSS rule came last in the bundle.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
