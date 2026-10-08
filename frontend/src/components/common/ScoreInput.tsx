import { Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { MAX_SCORE, MIN_SCORE, parseScore } from "./score";

/**
 * Goals for one side of a match, 0–99 (docs/contracts.md).
 *
 * A text input with inputMode="numeric" rather than type="number": the browser
 * number input accepts "e" and "-", scrolls on wheel and hides its own steppers
 * inconsistently. Non-digits are dropped as the user types.
 *
 * `size="lg"` is the scoreboard used to predict, with −/+ steppers. `size="sm"`
 * is the compact field used in admin lists.
 *
 * The caller provides the accessible name through `id` + a <label>, or through
 * `aria-label`; the steppers name themselves after it via `teamName`.
 */
export function ScoreInput({
  id,
  value,
  onChange,
  teamName,
  size = "lg",
  disabled,
  "aria-label": ariaLabel,
  "aria-describedby": ariaDescribedBy,
  "aria-invalid": ariaInvalid,
}: {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  /** Used to name the steppers: "Sumar un gol a Argentina". */
  teamName: string;
  size?: "sm" | "lg";
  disabled?: boolean;
  "aria-label"?: string;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
}) {
  const current = parseScore(value);

  function step(delta: number) {
    const next = Math.min(MAX_SCORE, Math.max(MIN_SCORE, (current ?? 0) + delta));
    onChange(String(next));
  }

  const input = (
    <Input
      id={id}
      type="text"
      inputMode="numeric"
      pattern="[0-9]*"
      autoComplete="off"
      maxLength={2}
      placeholder="–"
      value={value}
      disabled={disabled}
      aria-label={ariaLabel}
      aria-describedby={ariaDescribedBy}
      aria-invalid={ariaInvalid}
      onChange={(event) => onChange(event.target.value.replace(/\D/g, "").slice(0, 2))}
      className={cn(
        "text-center font-extrabold tabular-nums",
        size === "lg" ? "h-16 w-20 text-3xl" : "h-9 w-14 px-2 text-base",
      )}
    />
  );

  if (size === "sm") return input;

  return (
    <div className="flex items-center gap-2">
      <Button
        type="button"
        variant="outline"
        size="icon"
        onClick={() => step(-1)}
        disabled={disabled || current === null || current <= MIN_SCORE}
        aria-label={`Restar un gol a ${teamName}`}
      >
        <Minus aria-hidden="true" />
      </Button>
      {input}
      <Button
        type="button"
        variant="outline"
        size="icon"
        onClick={() => step(1)}
        disabled={disabled || (current !== null && current >= MAX_SCORE)}
        aria-label={`Sumar un gol a ${teamName}`}
      >
        <Plus aria-hidden="true" />
      </Button>
    </div>
  );
}
