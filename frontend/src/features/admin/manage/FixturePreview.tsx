import type { PlannedMatch } from "@/features/tournaments/fixture";
import { formatKickoff, pluralize } from "@/features/tournaments/format";

/** How many matches a planned fixture creates, over which dates, and its first round. */
export function FixturePreview({ plan }: { plan: PlannedMatch[] }) {
  if (plan.length === 0) {
    return (
      <p className="rounded-md bg-muted px-3.5 py-3 text-sm text-muted-foreground">
        Todos los cruces de este grupo ya tienen partido. No queda nada por
        generar.
      </p>
    );
  }

  const firstRound = plan.filter((match) => match.round === 1);
  const lastMatch = plan[plan.length - 1];

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted px-3.5 py-3 text-sm">
      <p>
        Se crearán <strong>{pluralize(plan.length, "partido")}</strong> en{" "}
        <strong>{pluralize(lastMatch.round, "fecha")}</strong>, hasta el{" "}
        {formatKickoff(lastMatch.startTime.toISOString())}.
      </p>
      <div>
        <p className="font-bold">
          Fecha 1, {formatKickoff(firstRound[0].startTime.toISOString())}
        </p>
        <ul className="mt-1 flex flex-col gap-0.5 text-muted-foreground">
          {firstRound.map((match) => (
            <li key={`${match.home.id}-${match.away.id}`}>
              {match.home.name} – {match.away.name}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
