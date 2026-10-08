import type { MatchCreationFailure } from "./matchCreation";

/** Which matches of a batch were not created, and why. */
export function MatchCreationFailures({ failures }: { failures: MatchCreationFailure[] }) {
  if (failures.length === 0) return null;

  return (
    <div role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm text-destructive">
      <p className="font-bold">
        {failures.length === 1
          ? "No se pudo crear 1 partido:"
          : `No se pudieron crear ${failures.length} partidos:`}
      </p>
      <ul className="mt-1 list-disc pl-5">
        {failures.map((failure) => (
          <li key={failure.label}>
            {failure.label}: {failure.reason}
          </li>
        ))}
      </ul>
    </div>
  );
}
