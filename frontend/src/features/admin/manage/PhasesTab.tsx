import { useState, type FormEvent } from "react";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FormField } from "@/components/common/FormField";
import { EmptyState } from "@/components/common/states";
import { createPhase } from "@/features/tournaments/api/tournaments";
import type { Group, Match, Phase, PhaseType, Team, Tournament } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { PHASE_TYPES, phaseLabel, phaseTypeLabel } from "@/features/tournaments/format";
import { findKnockoutSource } from "@/features/tournaments/knockout";
import { BuildNextPhaseDialog } from "./BuildNextPhaseDialog";
import { isConfigurable } from "./status";

function CreatePhaseDialog({
  tournamentId,
  nextOrder,
  onSaved,
}: {
  tournamentId: string;
  nextOrder: number;
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [phaseType, setPhaseType] = useState<PhaseType>("FINAL");
  const [phaseName, setPhaseName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const phase = await createPhase(tournamentId, {
        type: phaseType,
        name: phaseName.trim() || undefined,
        displayOrder: nextOrder,
      });
      setOpen(false);
      setPhaseType("FINAL");
      setPhaseName("");
      toast.success("Fase creada", {
        description: `${phaseLabel(phase)} ya está disponible para asignarle partidos.`,
      });
      onSaved();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "No se pudo crear la fase. Volvé a intentarlo."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="secondary">
          <Plus aria-hidden="true" />
          Crear fase
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Crear fase</DialogTitle>
          <DialogDescription>
            Las fases ordenan la etapa eliminatoria. Los partidos se asignan a un grupo o a una fase.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <FormField label="Tipo de fase" required>
            {(field) => (
              <Select value={phaseType} onValueChange={(value) => setPhaseType(value as PhaseType)}>
                <SelectTrigger
                  id={field.id}
                  aria-describedby={field["aria-describedby"]}
                  className="h-11 w-full"
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PHASE_TYPES.map((type) => (
                    <SelectItem key={type.value} value={type.value}>
                      {type.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </FormField>

          <FormField label="Nombre" hint="Opcional. Por ejemplo, “Semifinal ida”.">
            {(field) => (
              <Input
                {...field}
                value={phaseName}
                onChange={(event) => setPhaseName(event.target.value)}
                placeholder={phaseTypeLabel(phaseType)}
              />
            )}
          </FormField>

          {error && (
            <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" disabled={submitting}>
            {submitting ? "Creando…" : "Crear fase"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function PhasesTab({
  tournament,
  phases,
  groups,
  matches,
  tournamentTeams,
  onChanged,
}: {
  tournament: Tournament;
  phases: Phase[];
  groups: Group[];
  matches: Match[];
  tournamentTeams: Team[];
  onChanged: () => void;
}) {
  const knockoutSource = findKnockoutSource(phases, matches, groups.length > 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Fases</CardTitle>
        <CardDescription>Octavos, cuartos, semifinales y final.</CardDescription>
        {isConfigurable(tournament.status) && (
          <CardAction className="flex flex-wrap justify-end gap-2">
            {knockoutSource && (
              <BuildNextPhaseDialog
                tournamentId={tournament.id}
                source={knockoutSource}
                groups={groups}
                phases={phases}
                matches={matches}
                teams={tournamentTeams}
                onCreated={onChanged}
              />
            )}
            <CreatePhaseDialog
              tournamentId={tournament.id}
              nextOrder={phases.length + 1}
              onSaved={onChanged}
            />
          </CardAction>
        )}
      </CardHeader>
      <CardContent>
        {phases.length === 0 ? (
          <EmptyState
            title="Todavía no hay fases"
            description="Creá fases para organizar la etapa eliminatoria."
          />
        ) : (
          <ol className="flex flex-col gap-2">
            {phases.map((phase) => (
              <li
                key={phase.id}
                className="flex items-center justify-between rounded-lg border border-border bg-muted px-4 py-3"
              >
                <span className="text-sm font-bold">{phaseLabel(phase)}</span>
                {phase.name && (
                  <span className="text-xs text-muted-foreground">{phaseTypeLabel(phase.type)}</span>
                )}
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}
