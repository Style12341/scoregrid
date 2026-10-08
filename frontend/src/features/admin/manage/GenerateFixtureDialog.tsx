import { useState, type FormEvent } from "react";
import { CalendarPlus } from "lucide-react";
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
import { pluralize } from "@/features/tournaments/format";
import type { Group, Match, Team } from "@/features/tournaments/types/tournament";
import { FixturePreview } from "./FixturePreview";
import { BatchOutcome, CreateMatchesButton } from "./MatchBatch";
import { dateAtDefaultHour, defaultKickoff, isFutureKickoff, isValidKickoff, suggestedKickoff } from "./kickoff";
import { useMatchCreation, type MatchToCreate } from "./matchCreation";

/** One round a day unless the admin spaces them out. */
const DEFAULT_DAYS_BETWEEN_ROUNDS = 1;

/** What is wrong with the two inputs, in the admin's words, or null. */
function inputError(firstKickoff: string, daysBetweenRounds: string): string | null {
  if (!isValidKickoff(firstKickoff)) return "Elegí el día y la hora de la primera fecha.";
  if (!isFutureKickoff(firstKickoff)) return "La primera fecha tiene que ser en el futuro.";
  const days = Number(daysBetweenRounds);
  if (!Number.isInteger(days) || days < 1) return "Los días entre fechas tienen que ser un número entero, 1 o más.";
  return null;
}

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
 * creates the matches that do not exist yet. The plan always comes from the
 * current matches, so running it again after a partial failure creates only
 * what is missing.
 */
export function GenerateFixtureDialog({
  tournamentId,
  tournamentStartDate,
  group,
  teams,
  groupMatches,
  onCreated,
}: {
  tournamentId: string;
  /** The fixture starts no earlier than the tournament. */
  tournamentStartDate: string | null;
  group: Group;
  teams: Team[];
  groupMatches: Match[];
  onCreated: () => Promise<boolean>;
}) {
  const [open, setOpen] = useState(false);
  const [firstKickoff, setFirstKickoff] = useState(defaultKickoff);
  const [daysBetweenRounds, setDaysBetweenRounds] = useState(String(DEFAULT_DAYS_BETWEEN_ROUNDS));
  const creation = useMatchCreation({ tournamentId, onCreated, onAllCreated: () => setOpen(false) });

  const problem = inputError(firstKickoff, daysBetweenRounds);
  const plan = problem
    ? []
    : planRoundRobin(teams, groupMatches, new Date(firstKickoff), Number(daysBetweenRounds));

  function handleOpenChange(next: boolean) {
    // Closing mid-batch would hide how it ended.
    if (creation.running) return;
    if (next) {
      setFirstKickoff(suggestedKickoff(dateAtDefaultHour(tournamentStartDate)));
      setDaysBetweenRounds(String(DEFAULT_DAYS_BETWEEN_ROUNDS));
      creation.reset();
    }
    setOpen(next);
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (problem || plan.length === 0 || creation.staleData) return;
    void creation.run(
      async () => plan.map((match) => toMatchToCreate(group.id, match)),
      (created) => ({
        title: "Fixture generado",
        description: `Se crearon ${pluralize(created, "partido")} en ${group.name}.`,
      }),
    );
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">
          <CalendarPlus aria-hidden="true" />
          Generar fixture
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto" showCloseButton={!creation.running}>
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

          {problem ? (
            <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {problem}
            </p>
          ) : (
            <FixturePreview plan={plan} />
          )}
          <BatchOutcome result={creation.result} staleData={creation.staleData} />

          <CreateMatchesButton
            count={plan.length}
            running={creation.running}
            progress={creation.progress}
            staleData={creation.staleData}
          />
        </form>
      </DialogContent>
    </Dialog>
  );
}
