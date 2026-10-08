import { useState, type FormEvent } from "react";
import { CalendarPlus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { FormField } from "@/components/common/FormField";
import { planRoundRobin, type PlannedMatch } from "@/features/tournaments/fixture";
import type { Group, Match, Team } from "@/features/tournaments/types/tournament";
import { FixturePreview } from "./FixturePreview";
import { MatchCreationFailures } from "./MatchCreationFailures";
import { defaultKickoff, isFutureKickoff } from "./kickoff";
import { useMatchCreation, type MatchToCreate } from "./matchCreation";

/** One round a day unless the admin spaces them out. */
const DEFAULT_DAYS_BETWEEN_ROUNDS = 1;

function toMatchToCreate(groupId: string, match: PlannedMatch): MatchToCreate {
  return {
    input: {
      groupId,
      homeTeamId: match.home.id,
      awayTeamId: match.away.id,
      startTime: match.startTime.toISOString(),
    },
    label: `${match.home.name} – ${match.away.name}`,
  };
}

/**
 * Plans the group's round robin (every team against every other once) and
 * creates the matches that do not exist yet.
 */
export function GenerateFixtureDialog({
  tournamentId,
  group,
  teams,
  groupMatches,
  onCreated,
}: {
  tournamentId: string;
  group: Group;
  teams: Team[];
  groupMatches: Match[];
  onCreated: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [firstKickoff, setFirstKickoff] = useState(defaultKickoff);
  const [daysBetweenRounds, setDaysBetweenRounds] = useState(String(DEFAULT_DAYS_BETWEEN_ROUNDS));
  const [error, setError] = useState<string | null>(null);
  const creation = useMatchCreation(tournamentId);

  const days = Number(daysBetweenRounds);
  const validDays = Number.isInteger(days) && days >= 1;
  const validKickoff = !Number.isNaN(new Date(firstKickoff).getTime());
  const plan =
    validDays && validKickoff ? planRoundRobin(teams, groupMatches, new Date(firstKickoff), days) : [];

  function handleOpenChange(next: boolean) {
    // Closing mid-batch would hide how it ended.
    if (creation.creating) return;
    if (next) {
      setFirstKickoff(defaultKickoff());
      setDaysBetweenRounds(String(DEFAULT_DAYS_BETWEEN_ROUNDS));
      setError(null);
      creation.reset();
    }
    setOpen(next);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!isFutureKickoff(firstKickoff)) {
      setError("La primera fecha tiene que ser en el futuro.");
      return;
    }
    if (!validDays) {
      setError("Los días entre fechas tienen que ser un número entero, 1 o más.");
      return;
    }

    const failures = await creation.create(plan.map((match) => toMatchToCreate(group.id, match)));
    onCreated();
    const created = plan.length - failures.length;
    if (failures.length === 0) {
      toast.success("Fixture generado", {
        description: `Se crearon ${created} partidos en ${group.name}.`,
      });
      setOpen(false);
    } else {
      toast.error("Algunos partidos no se crearon", {
        description: `Se crearon ${created} de ${plan.length}. El detalle está en el diálogo.`,
      });
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">
          <CalendarPlus aria-hidden="true" />
          Generar fixture
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto" showCloseButton={!creation.creating}>
        <DialogHeader>
          <DialogTitle>Generar fixture de {group.name}</DialogTitle>
          <DialogDescription>
            Todos contra todos, a una rueda. Los cruces que ya tienen partido se saltean.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-[1fr_auto]">
            <FormField label="Primera fecha" required>
              {(field) => (
                <Input
                  {...field}
                  type="datetime-local"
                  value={firstKickoff}
                  onChange={(event) => setFirstKickoff(event.target.value)}
                />
              )}
            </FormField>
            <FormField label="Días entre fechas" required>
              {(field) => (
                <Input
                  {...field}
                  type="number"
                  min={1}
                  step={1}
                  className="sm:w-32"
                  value={daysBetweenRounds}
                  onChange={(event) => setDaysBetweenRounds(event.target.value)}
                />
              )}
            </FormField>
          </div>

          <FixturePreview plan={plan} />

          {error && (
            <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {error}
            </p>
          )}
          <MatchCreationFailures failures={creation.failures} />

          <Button type="submit" disabled={creation.creating || plan.length === 0}>
            {creation.creating
              ? `Creando ${creation.handled} de ${plan.length}…`
              : `Crear ${plan.length} ${plan.length === 1 ? "partido" : "partidos"}`}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
