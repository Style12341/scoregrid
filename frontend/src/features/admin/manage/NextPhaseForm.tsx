import type { FormEvent } from "react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FormField } from "@/components/common/FormField";
import { formatKickoff, phaseTypeLabel } from "@/features/tournaments/format";
import type { ProposedMatch } from "@/features/tournaments/knockout";
import type { Match, Team } from "@/features/tournaments/types/tournament";
import { BatchOutcome, CreateMatchesButton } from "./MatchBatch";
import { HOURS_BETWEEN_STAGGERED_KICKOFFS } from "./kickoff";
import type { BatchResult } from "./matchCreation";
import type { NextPhaseProposalForm, TeamSide } from "./useNextPhaseProposal";

/** Everything the form renders: the proposal, plus the running batch and its submit. */
export interface NextPhaseFormModel extends NextPhaseProposalForm {
  batch: {
    running: boolean;
    progress: { handled: number; total: number } | null;
    result: BatchResult | null;
  };
  onSubmit: (event: FormEvent) => void;
}

/** After a draw the score does not say who went through: the admin does. */
function AdvancingTeamPicks({
  matches,
  picks,
  onPick,
}: {
  matches: Match[];
  picks: Record<string, string>;
  onPick: (matchId: string, teamId: string) => void;
}) {
  if (matches.length === 0) return null;

  return (
    <fieldset className="flex flex-col gap-3 rounded-md border border-border px-3.5 py-3">
      <legend className="px-1 text-[13px] font-bold text-muted-foreground">¿Quién pasó?</legend>
      {matches.map((match) => (
        <FormField
          key={match.id}
          label={`${match.homeTeam.name} ${match.homeScore} – ${match.awayScore} ${match.awayTeam.name}`}
        >
          {(field) => (
            <Select value={picks[match.id] ?? ""} onValueChange={(teamId) => onPick(match.id, teamId)}>
              <SelectTrigger id={field.id} className="h-11 w-full">
                <SelectValue placeholder="Elegí el equipo que pasó" />
              </SelectTrigger>
              <SelectContent>
                {[match.homeTeam, match.awayTeam].map((team) => (
                  <SelectItem key={team.id} value={team.id}>
                    {team.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </FormField>
      ))}
    </fieldset>
  );
}

function TeamSelect({
  label,
  value,
  teams,
  onChange,
}: {
  label: string;
  value: string;
  teams: Team[];
  onChange: (teamId: string) => void;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger aria-label={label} className="h-11 w-full min-w-0">
        <SelectValue placeholder="Elegí un equipo" />
      </SelectTrigger>
      <SelectContent>
        {teams.map((team) => (
          <SelectItem key={team.id} value={team.id}>
            {team.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/** One proposed match: its phase and kickoff, and both teams, editable. */
function ProposedMatchRow({
  number,
  proposal,
  kickoff,
  teams,
  onChange,
}: {
  number: number;
  proposal: ProposedMatch;
  kickoff: Date | null;
  teams: Team[];
  onChange: (side: TeamSide, teamId: string) => void;
}) {
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border bg-muted p-3">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="font-bold">{phaseTypeLabel(proposal.phaseType)}</span>
        {kickoff && (
          <span className="text-xs text-muted-foreground">{formatKickoff(kickoff.toISOString())}</span>
        )}
      </div>
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <TeamSelect
          label={`Local del partido ${number}`}
          value={proposal.home?.id ?? ""}
          teams={teams}
          onChange={(teamId) => onChange("home", teamId)}
        />
        <span className="text-sm font-bold text-muted-foreground">vs</span>
        <TeamSelect
          label={`Visitante del partido ${number}`}
          value={proposal.away?.id ?? ""}
          teams={teams}
          onChange={(teamId) => onChange("away", teamId)}
        />
      </div>
    </li>
  );
}

/** The editable proposal for the next knockout phase. Renders only; the dialog owns the state. */
export function NextPhaseForm({ model }: { model: NextPhaseFormModel }) {
  return (
    <form onSubmit={model.onSubmit} className="flex flex-col gap-4" noValidate>
      <AdvancingTeamPicks matches={model.undecided} picks={model.picks} onPick={model.onPick} />

      <FormField
        label="Primer partido"
        hint={`Los siguientes se juegan cada ${HOURS_BETWEEN_STAGGERED_KICKOFFS} horas.`}
        required
      >
        {(field) => (
          <Input
            {...field}
            type="datetime-local"
            value={model.firstKickoff}
            onChange={(event) => model.onFirstKickoffChange(event.target.value)}
          />
        )}
      </FormField>

      {model.offersThirdPlace && (
        <label className="flex items-center gap-2 text-sm font-semibold">
          <input
            type="checkbox"
            className="size-4 accent-primary"
            checked={model.includeThirdPlace}
            onChange={(event) => model.onIncludeThirdPlaceChange(event.target.checked)}
          />
          Incluir el partido por el tercer puesto
        </label>
      )}

      {model.proposals.length === 0 ? (
        <p className="rounded-md bg-muted px-3.5 py-3 text-sm text-muted-foreground">
          No queda ningún partido por crear en esta fase.
        </p>
      ) : (
        <ol className="flex flex-col gap-2">
          {model.proposals.map((proposal, index) => (
            <ProposedMatchRow
              key={`${proposal.phaseType}-${index}`}
              number={index + 1}
              proposal={proposal}
              kickoff={model.kickoffs[index]}
              teams={model.teams}
              onChange={(side, teamId) => model.onTeamChange(index, side, teamId)}
            />
          ))}
        </ol>
      )}

      {model.error && (
        <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
          {model.error}
        </p>
      )}
      <BatchOutcome result={model.batch.result} />

      <CreateMatchesButton
        count={model.proposals.length}
        running={model.batch.running}
        progress={model.batch.progress}
      />
    </form>
  );
}
