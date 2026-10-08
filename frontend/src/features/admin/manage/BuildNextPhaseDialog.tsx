import { useEffect, useState, type FormEvent } from "react";
import { GitBranchPlus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { ErrorState, LoadingState } from "@/components/common/states";
import { getGroupTeams } from "@/features/tournaments/api/tournaments";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { isPendingMatch, matchesInGroup, phaseTypeLabel } from "@/features/tournaments/format";
import {
  proposeFromGroups,
  proposeFromRound,
  undecidedMatches,
  type KnockoutSource,
  type ProposedMatch,
} from "@/features/tournaments/knockout";
import { computeStandings } from "@/features/tournaments/standings";
import type { Group, Match, Phase, Team, TeamRef } from "@/features/tournaments/types/tournament";
import { defaultKickoff, isFutureKickoff, staggeredKickoff } from "./kickoff";
import { useMatchCreation } from "./matchCreation";
import { NextPhaseForm, type TeamSide } from "./NextPhaseForm";
import { ensurePhases } from "./phaseCreation";

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

function sourceLabel(source: KnockoutSource): string {
  return source.kind === "groups" ? "la fase de grupos" : phaseTypeLabel(source.round);
}

/**
 * Proposes the next knockout phase from the results (the group tables, or the
 * winners of the latest round), lets the admin edit it, then creates the
 * phase if missing and its matches. Owns the state; NextPhaseForm renders it.
 */
export function BuildNextPhaseDialog({
  tournamentId,
  source,
  groups,
  phases,
  matches,
  teams,
  onCreated,
}: {
  tournamentId: string;
  source: KnockoutSource;
  groups: Group[];
  phases: Phase[];
  matches: Match[];
  teams: Team[];
  onCreated: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [picks, setPicks] = useState<Record<string, string>>({});
  const [proposals, setProposals] = useState<ProposedMatch[]>([]);
  const [includeThirdPlace, setIncludeThirdPlace] = useState(true);
  const [firstKickoff, setFirstKickoff] = useState(defaultKickoff);
  const [loadingTables, setLoadingTables] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const creation = useMatchCreation(tournamentId);

  const sourceMatches =
    source.kind === "round" ? source.matches : matches.filter((match) => match.groupId !== null);
  const pendingCount = sourceMatches.filter(isPendingMatch).length;
  const undecided = source.kind === "round" ? undecidedMatches(source.matches) : [];

  // The group tables need each group's teams, which the manage page does not load.
  useEffect(() => {
    if (!open || source.kind !== "groups" || pendingCount > 0) return;
    let cancelled = false;
    const orderedGroups = [...groups].sort((a, b) => a.displayOrder - b.displayOrder);
    setLoadingTables(true);
    setLoadError(null);
    Promise.all(orderedGroups.map((group) => getGroupTeams(group.id)))
      .then((teamsByGroup) => {
        if (cancelled) return;
        const tables = orderedGroups.map((group, index) =>
          computeStandings(teamsByGroup[index], matchesInGroup(matches, group.id)),
        );
        setProposals(proposeFromGroups(tables));
      })
      .catch((requestError) => {
        if (!cancelled) setLoadError(apiErrorMessage(requestError, "No pudimos cargar los grupos."));
      })
      .finally(() => {
        if (!cancelled) setLoadingTables(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, source.kind, pendingCount, groups, matches]);

  const visibleProposals = includeThirdPlace
    ? proposals
    : proposals.filter((proposal) => proposal.phaseType !== "THIRD_PLACE");
  const offersThirdPlace = proposals.some((proposal) => proposal.phaseType === "THIRD_PLACE");
  const validFirstKickoff = !Number.isNaN(new Date(firstKickoff).getTime());
  const kickoffs = visibleProposals.map((_, index) =>
    validFirstKickoff ? staggeredKickoff(firstKickoff, index) : null,
  );
  const targetLabel =
    proposals.length > 0 ? phaseTypeLabel(proposals[proposals.length - 1].phaseType) : "la siguiente fase";

  function handleOpenChange(next: boolean) {
    // Closing mid-batch would hide how it ended.
    if (creation.creating) return;
    if (next) {
      setPicks({});
      setProposals(source.kind === "round" ? proposeFromRound(source, {}) : []);
      // The group tables load in the effect above; show that instead of an empty form.
      setLoadingTables(source.kind === "groups" && pendingCount === 0);
      setIncludeThirdPlace(true);
      setFirstKickoff(defaultKickoff());
      setError(null);
      creation.reset();
    }
    setOpen(next);
  }

  function handlePick(matchId: string, teamId: string) {
    if (source.kind !== "round") return;
    const nextPicks = { ...picks, [matchId]: teamId };
    setPicks(nextPicks);
    // Who advanced changed, so the pairings are proposed again.
    setProposals(proposeFromRound(source, nextPicks));
  }

  function handleProposalChange(index: number, side: TeamSide, teamId: string) {
    const changed = visibleProposals[index];
    const team = teams.find((candidate) => candidate.id === teamId) ?? null;
    setProposals((current) =>
      current.map((proposal) => (proposal === changed ? { ...proposal, [side]: team } : proposal)),
    );
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const problem = proposalError(visibleProposals, firstKickoff);
    if (problem) {
      setError(problem);
      return;
    }
    const complete = visibleProposals.filter(isComplete);
    const phaseTypes = [...new Set(complete.map((proposal) => proposal.phaseType))];

    let phaseIdByType: Record<string, string>;
    try {
      phaseIdByType = await ensurePhases(tournamentId, phases, phaseTypes);
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "No se pudo crear la fase. Volvé a intentarlo."));
      onCreated();
      return;
    }

    const failures = await creation.create(
      complete.map((proposal, index) => ({
        input: {
          phaseId: phaseIdByType[proposal.phaseType],
          homeTeamId: proposal.home.id,
          awayTeamId: proposal.away.id,
          startTime: staggeredKickoff(firstKickoff, index).toISOString(),
        },
        label: `${phaseTypeLabel(proposal.phaseType)}: ${proposal.home.name} – ${proposal.away.name}`,
      })),
    );
    onCreated();
    if (failures.length === 0) {
      toast.success("Fase armada", {
        description: `${targetLabel}: se crearon ${complete.length} ${complete.length === 1 ? "partido" : "partidos"}.`,
      });
      setOpen(false);
    } else {
      toast.error("Algunos partidos no se crearon", {
        description: `Se crearon ${complete.length - failures.length} de ${complete.length}. El detalle está en el diálogo.`,
      });
    }
  }

  function renderBody() {
    if (pendingCount > 0) {
      return (
        <p className="rounded-md bg-muted px-3.5 py-3 text-sm">
          {pendingCount === 1
            ? `Falta terminar 1 partido de ${sourceLabel(source)}.`
            : `Faltan terminar ${pendingCount} partidos de ${sourceLabel(source)}.`}{" "}
          Cargá sus resultados o cancelalos para armar la fase siguiente.
        </p>
      );
    }
    if (loadingTables) return <LoadingState label="Calculando las tablas…" />;
    if (loadError) return <ErrorState title="No pudimos armar la propuesta" description={loadError} />;
    return (
      <NextPhaseForm
        undecided={undecided}
        picks={picks}
        onPick={handlePick}
        proposals={visibleProposals}
        kickoffs={kickoffs}
        teams={teams}
        onProposalChange={handleProposalChange}
        offersThirdPlace={offersThirdPlace}
        includeThirdPlace={includeThirdPlace}
        onIncludeThirdPlaceChange={setIncludeThirdPlace}
        firstKickoff={firstKickoff}
        onFirstKickoffChange={setFirstKickoff}
        error={error}
        failures={creation.failures}
        creating={creation.creating}
        handled={creation.handled}
        onSubmit={handleSubmit}
      />
    );
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm" variant="secondary">
          <GitBranchPlus aria-hidden="true" />
          Armar siguiente fase
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl" showCloseButton={!creation.creating}>
        <DialogHeader>
          <DialogTitle>Armar {targetLabel.toLowerCase()}</DialogTitle>
          <DialogDescription>
            Propuesta a partir de {sourceLabel(source)}. Podés cambiar los cruces antes de crearlos.
          </DialogDescription>
        </DialogHeader>
        {renderBody()}
      </DialogContent>
    </Dialog>
  );
}
