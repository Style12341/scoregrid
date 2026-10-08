import { teamCode } from "@/features/tournaments/format";
import type { Team } from "@/features/tournaments/types/tournament";
import { cn } from "@/lib/utils";

const chipBase = "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm font-medium";

function Label({ team }: { team: Team }) {
  const code = teamCode(team);
  return (
    <>
      {team.name}
      {code && <span className="text-xs font-semibold opacity-70">{code}</span>}
    </>
  );
}

/** A team already in a tournament or group. */
export function TeamChip({ team }: { team: Team }) {
  return (
    <span className={cn(chipBase, "border-border bg-muted")}>
      <Label team={team} />
    </span>
  );
}

/** A team that can be picked; aria-pressed tells assistive tech it toggles. */
export function SelectableTeamChip({
  team,
  selected,
  onToggle,
}: {
  team: Team;
  selected: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onToggle}
      className={cn(
        chipBase,
        "transition-colors",
        selected
          ? "border-primary bg-primary text-primary-foreground"
          : "border-border bg-card hover:border-primary/50",
      )}
    >
      <Label team={team} />
    </button>
  );
}
