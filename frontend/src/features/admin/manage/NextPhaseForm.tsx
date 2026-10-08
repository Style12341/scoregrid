import type { FormEvent } from "react";
import { Button } from "@/components/ui/button";
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
import { MatchCreationFailures } from "./MatchCreationFailures";
import { MINUTES_BETWEEN_STAGGERED_KICKOFFS } from "./kickoff";
import type { MatchCreationFailure } from "./matchCreation";

export type TeamSide = "home" | "away";

const MINUTES_PER_HOUR = 60;

/** After a draw or a cancellation the score does not say who went through: the admin does. */
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
          label={
            match.status === "CANCELLED"
              ? `${match.homeTeam.name} – ${match.awayTeam.name} (cancelado)`
              : `${match.homeTeam.name} ${match.homeScore} – ${match.awayScore} ${match.awayTeam.name}`
          }
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
export function NextPhaseForm({
  undecided,
  picks,
  onPick,
  proposals,
  kickoffs,
  teams,
  onProposalChange,
  offersThirdPlace,
  includeThirdPlace,
  onIncludeThirdPlaceChange,
  firstKickoff,
  onFirstKickoffChange,
  error,
  failures,
  creating,
  handled,
  onSubmit,
}: {
  undecided: Match[];
  picks: Record<string, string>;
  onPick: (matchId: string, teamId: string) => void;
  proposals: ProposedMatch[];
  /** One per proposal, null while the first kickoff is not a valid date. */
  kickoffs: (Date | null)[];
  teams: Team[];
  onProposalChange: (index: number, side: TeamSide, teamId: string) => void;
  offersThirdPlace: boolean;
  includeThirdPlace: boolean;
  onIncludeThirdPlaceChange: (include: boolean) => void;
  firstKickoff: string;
  onFirstKickoffChange: (value: string) => void;
  error: string | null;
  failures: MatchCreationFailure[];
  creating: boolean;
  handled: number;
  onSubmit: (event: FormEvent) => void;
}) {
  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      <AdvancingTeamPicks matches={undecided} picks={picks} onPick={onPick} />

      <FormField
        label="Primer partido"
        hint={`Los siguientes se juegan cada ${MINUTES_BETWEEN_STAGGERED_KICKOFFS / MINUTES_PER_HOUR} horas.`}
        required
      >
        {(field) => (
          <Input
            {...field}
            type="datetime-local"
            value={firstKickoff}
            onChange={(event) => onFirstKickoffChange(event.target.value)}
          />
        )}
      </FormField>

      {offersThirdPlace && (
        <label className="flex items-center gap-2 text-sm font-semibold">
          <input
            type="checkbox"
            className="size-4 accent-primary"
            checked={includeThirdPlace}
            onChange={(event) => onIncludeThirdPlaceChange(event.target.checked)}
          />
          Incluir el partido por el tercer puesto
        </label>
      )}

      <ol className="flex flex-col gap-2">
        {proposals.map((proposal, index) => (
          <ProposedMatchRow
            key={`${proposal.phaseType}-${index}`}
            number={index + 1}
            proposal={proposal}
            kickoff={kickoffs[index]}
            teams={teams}
            onChange={(side, teamId) => onProposalChange(index, side, teamId)}
          />
        ))}
      </ol>

      {error && (
        <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
          {error}
        </p>
      )}
      <MatchCreationFailures failures={failures} />

      <Button type="submit" disabled={creating || proposals.length === 0}>
        {creating
          ? `Creando ${handled} de ${proposals.length}…`
          : `Crear ${proposals.length} ${proposals.length === 1 ? "partido" : "partidos"}`}
      </Button>
    </form>
  );
}
