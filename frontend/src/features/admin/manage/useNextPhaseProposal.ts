import { useRef, useState } from "react";
import { getGroupTeams } from "@/features/tournaments/api/tournaments";
import { requestErrorMessage } from "@/features/tournaments/errors";
import { isPendingMatch, matchesInGroup, phaseTypeLabel, pluralize } from "@/features/tournaments/format";
import {
  proposeFromGroups,
  proposeFromRound,
  sourceMatches,
  undecidedMatches,
  withoutExistingMatches,
  type KnockoutSource,
  type ProposedMatch,
} from "@/features/tournaments/knockout";
import { computeStandings } from "@/features/tournaments/standings";
import type { Group, Match, Phase, PhaseType, Team, TeamRef } from "@/features/tournaments/types/tournament";
import {
  dayAfterLatest,
  defaultKickoff,
  isFutureKickoff,
  isValidKickoff,
  staggeredKickoff,
  suggestedKickoff,
} from "./kickoff";
import type { MatchToCreate } from "./matchCreation";

export type TeamSide = "home" | "away";

type CompleteProposal = ProposedMatch & { home: TeamRef; away: TeamRef };

function isComplete(proposal: ProposedMatch): proposal is CompleteProposal {
  return proposal.home !== null && proposal.away !== null;
}

/** What is wrong with the proposal, in the admin's words, or null when it can be created. */
function proposalError(proposals: ProposedMatch[], firstKickoff: string): string | null {
  if (!proposals.every(isComplete)) return "Elegí los dos equipos de cada partido.";
  if (proposals.some((proposal) => proposal.home.id === proposal.away.id)) {
    return "Un equipo no puede jugar contra sí mismo.";
  }
  const teamIds = proposals.flatMap((proposal) => [proposal.home.id, proposal.away.id]);
  if (new Set(teamIds).size !== teamIds.length) return "Cada equipo puede jugar un solo partido de la fase.";
  if (!isFutureKickoff(firstKickoff)) return "El primer partido tiene que ser en el futuro.";
  return null;
}

export function sourceLabel(source: KnockoutSource): string {
  return source.kind === "groups" ? "la fase de grupos" : phaseTypeLabel(source.round);
}

/** Why the next phase cannot be proposed yet, or null. */
function blockerFor(source: KnockoutSource, matches: Match[]): string | null {
  const deciding = sourceMatches(source, matches);
  const pending = deciding.filter(isPendingMatch).length;
  if (pending > 0) {
    return `${pending === 1 ? "Falta" : "Faltan"} terminar ${pluralize(pending, "partido")} de ${sourceLabel(source)}. Cargá sus resultados o cancelalos para armar la fase siguiente.`;
  }
  if (source.kind === "groups" && !deciding.some((match) => match.status === "FINISHED")) {
    return "Cargá resultados de los grupos primero: la propuesta sale de las tablas.";
  }
  return null;
}

/**
 * State and rules of the "Armar siguiente fase" proposal: who went through
 * after a draw, the editable pairings, the kickoff and the third-place option.
 * The pairings shown always leave out what already exists in the target phase,
 * so after a batch that stopped half-way only the missing matches remain, with
 * the admin's edits intact.
 */
export function useNextPhaseProposal({
  source,
  groups,
  phases,
  matches,
  teams,
}: {
  source: KnockoutSource;
  groups: Group[];
  phases: Phase[];
  matches: Match[];
  teams: Team[];
}) {
  const [picks, setPicks] = useState<Record<string, string>>({});
  const [proposals, setProposals] = useState<ProposedMatch[]>([]);
  const [includeThirdPlace, setIncludeThirdPlace] = useState(true);
  const [firstKickoff, setFirstKickoff] = useState(defaultKickoff);
  const [loadingTables, setLoadingTables] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Ignores the answer of a load started for an earlier opening.
  const loadRequest = useRef(0);

  const blocker = blockerFor(source, matches);
  const remaining = withoutExistingMatches(proposals, phases, matches);
  const visible = includeThirdPlace
    ? remaining
    : remaining.filter((proposal) => proposal.phaseType !== "THIRD_PLACE");
  const lastProposal = proposals[proposals.length - 1];
  const targetLabel = lastProposal ? phaseTypeLabel(lastProposal.phaseType) : "la siguiente fase";

  async function loadGroupProposal() {
    const request = ++loadRequest.current;
    const orderedGroups = [...groups].sort((a, b) => a.displayOrder - b.displayOrder);
    setLoadingTables(true);
    try {
      const teamsByGroup = await Promise.all(orderedGroups.map((group) => getGroupTeams(group.id)));
      if (request !== loadRequest.current) return;
      const tables = orderedGroups.map((group, index) =>
        computeStandings(teamsByGroup[index], matchesInGroup(matches, group.id)),
      );
      setProposals(proposeFromGroups(tables));
    } catch (requestError) {
      if (request === loadRequest.current) {
        setLoadError(requestErrorMessage(requestError, "No pudimos cargar los grupos."));
      }
    } finally {
      if (request === loadRequest.current) setLoadingTables(false);
    }
  }

  /** Starts over from the current results, as the dialog opens. */
  function start() {
    setPicks({});
    setProposals([]);
    setIncludeThirdPlace(true);
    // The new round is played after the one it comes from.
    setFirstKickoff(
      suggestedKickoff(dayAfterLatest(sourceMatches(source, matches).map((match) => match.startTime))),
    );
    setLoadError(null);
    setError(null);
    if (blocker) return;
    if (source.kind === "round") {
      setProposals(proposeFromRound(source, {}));
    } else {
      void loadGroupProposal();
    }
  }

  function pick(matchId: string, teamId: string) {
    if (source.kind !== "round") return;
    const nextPicks = { ...picks, [matchId]: teamId };
    setPicks(nextPicks);
    // Who went through changed, so the pairings are proposed again.
    setProposals(proposeFromRound(source, nextPicks));
  }

  /** `index` counts the pairings on screen. */
  function changeTeam(index: number, side: TeamSide, teamId: string) {
    const changed = visible[index];
    const team = teams.find((candidate) => candidate.id === teamId) ?? null;
    setProposals((current) =>
      current.map((proposal) => (proposal === changed ? { ...proposal, [side]: team } : proposal)),
    );
  }

  /** The pairings on screen, once they pass every rule; null (with the reason on screen) when not. */
  function readyProposals(): CompleteProposal[] | null {
    const problem = proposalError(visible, firstKickoff);
    setError(problem);
    return problem ? null : visible.filter(isComplete);
  }

  function toMatchesToCreate(ready: CompleteProposal[], phaseIdByType: Record<string, string>): MatchToCreate[] {
    return ready.map((proposal, index) => ({
      input: {
        phaseId: phaseIdByType[proposal.phaseType],
        homeTeamId: proposal.home.id,
        awayTeamId: proposal.away.id,
        startTime: staggeredKickoff(firstKickoff, index).toISOString(),
      },
      label: `${phaseTypeLabel(proposal.phaseType)}: ${proposal.home.name} – ${proposal.away.name}`,
    }));
  }

  return {
    blocker,
    loadingTables,
    loadError,
    targetLabel,
    start,
    readyProposals,
    toMatchesToCreate,
    phaseTypesOf: (ready: CompleteProposal[]): PhaseType[] => [
      ...new Set(ready.map((proposal) => proposal.phaseType)),
    ],
    form: {
      undecided: source.kind === "round" ? undecidedMatches(source.matches) : [],
      picks,
      proposals: visible,
      kickoffs: visible.map((_, index) =>
        isValidKickoff(firstKickoff) ? staggeredKickoff(firstKickoff, index) : null,
      ),
      teams,
      offersThirdPlace: remaining.some((proposal) => proposal.phaseType === "THIRD_PLACE"),
      includeThirdPlace,
      firstKickoff,
      error,
      onPick: pick,
      onTeamChange: changeTeam,
      onIncludeThirdPlaceChange: setIncludeThirdPlace,
      onFirstKickoffChange: setFirstKickoff,
    },
  };
}

export type NextPhaseProposalForm = ReturnType<typeof useNextPhaseProposal>["form"];
