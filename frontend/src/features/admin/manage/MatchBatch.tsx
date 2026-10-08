import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { pluralize } from "@/features/tournaments/format";
import { summarizeBatch, type BatchResult } from "./matchCreation";

/**
 * What a batch that did not complete left behind: counts, why it stopped, and
 * each refusal. Promises a safe retry only when the data on screen refreshed.
 */
export function BatchOutcome({ result, staleData }: { result: BatchResult | null; staleData: boolean }) {
  if (!result && !staleData) return null;

  return (
    <div role="alert" className="flex flex-col gap-1 rounded-md bg-destructive/10 px-3.5 py-3 text-sm text-destructive">
      {result && (
        <p className="font-bold">
          {result.total > 0 ? summarizeBatch(result) : "No se creó ningún partido"}
        </p>
      )}
      {result?.stoppedBecause && <p>Se frenó la creación: {result.stoppedBecause}</p>}
      <p>
        {staleData
          ? "No pudimos actualizar los datos. Recargá la página antes de reintentar."
          : result?.stoppedBecause
            ? "Volvé a intentarlo cuando el servicio responda: solo se crean los partidos que faltan."
            : "Si reintentás, solo se crean los partidos que faltan."}
      </p>
      {result && result.failures.length > 0 && (
        <ul className="list-disc pl-5">
          {result.failures.map((failure) => (
            <li key={failure.label}>
              {failure.label}: {failure.reason}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** The submit button of a batch dialog: how many it creates, then how far it got. */
export function CreateMatchesButton({
  count,
  running,
  progress,
  staleData,
}: {
  count: number;
  running: boolean;
  progress: { handled: number; total: number } | null;
  /** The data the count was planned from did not refresh: retrying could duplicate. */
  staleData: boolean;
}) {
  return (
    <Button type="submit" disabled={running || staleData || count === 0} aria-busy={running || undefined}>
      {running && <Loader2 className="animate-spin" aria-hidden="true" />}
      {!running
        ? `Crear ${pluralize(count, "partido")}`
        : progress
          ? `Creando ${progress.handled} de ${progress.total}…`
          : "Preparando…"}
    </Button>
  );
}
