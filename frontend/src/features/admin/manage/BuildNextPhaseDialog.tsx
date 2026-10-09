import { useState, type FormEvent } from "react";
import { GitBranchPlus } from "lucide-react";
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
import { pluralize } from "@/features/tournaments/format";
import type { KnockoutSource } from "@/features/tournaments/knockout";
import type {
  Group,
  Match,
  Phase,
  Team,
} from "@/features/tournaments/types/tournament";
import { useMatchCreation } from "./matchCreation";
import { NextPhaseForm } from "./NextPhaseForm";
import { ensurePhases } from "./phaseCreation";
import { sourceLabel, useNextPhaseProposal } from "./useNextPhaseProposal";

/**
 * Container for "Armar siguiente fase": opens the proposal, creates the phase
 * if missing and then its matches. NextPhaseForm renders; useNextPhaseProposal
 * holds the proposal; useMatchCreation runs the batch.
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
  onCreated: () => Promise<boolean>;
}) {
  const [open, setOpen] = useState(false);
  const proposal = useNextPhaseProposal({
    source,
    groups,
    phases,
    matches,
    teams,
  });
  const creation = useMatchCreation({
    tournamentId,
    onCreated,
    onAllCreated: () => setOpen(false),
  });

  function handleOpenChange(next: boolean) {
    // Closing mid-batch would hide how it ended.
    if (creation.running) return;
    if (next) {
      proposal.start();
      creation.reset();
    }
    setOpen(next);
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (creation.staleData) return;
    const ready = proposal.readyProposals();
    if (!ready || ready.length === 0) return;
    const targetLabel = proposal.targetLabel;
    void creation.run(
      async () => {
        const phaseIdByType = await ensurePhases(
          tournamentId,
          phases,
          proposal.phaseTypesOf(ready),
        );
        return proposal.toMatchesToCreate(ready, phaseIdByType);
      },
      (created) => ({
        title: "Fase armada",
        description: `${targetLabel}: se crearon ${pluralize(created, "partido")}.`,
      }),
    );
  }

  function renderBody() {
    if (proposal.blocker) {
      return (
        <p className="rounded-md bg-muted px-3.5 py-3 text-sm">
          {proposal.blocker}
        </p>
      );
    }
    if (proposal.loadingTables)
      return <LoadingState label="Calculando las tablas…" />;
    if (proposal.loadError) {
      return (
        <ErrorState
          title="No se pudo armar la propuesta"
          description={proposal.loadError}
          onRetry={proposal.start}
        />
      );
    }
    return (
      <NextPhaseForm
        model={{ ...proposal.form, batch: creation, onSubmit: handleSubmit }}
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
      <DialogContent
        className="max-h-[90vh] overflow-y-auto sm:max-w-xl"
        showCloseButton={!creation.running}
      >
        <DialogHeader>
          <DialogTitle>Armar {proposal.targetLabel.toLowerCase()}</DialogTitle>
          <DialogDescription>
            Propuesta a partir de {sourceLabel(source)}. Puede cambiar los
            cruces antes de crearlos.
          </DialogDescription>
        </DialogHeader>
        {renderBody()}
      </DialogContent>
    </Dialog>
  );
}
