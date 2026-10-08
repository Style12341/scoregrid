import { useState, type FormEvent, type ReactNode } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/common/FormField";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { createTournament, updateTournament } from "@/features/tournaments/api/tournaments";
import type { CreateTournamentInput, Tournament } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";

/**
 * Create or edit a tournament. Used from the admin list (create, edit icon)
 * and from the manage page header.
 */
export function TournamentFormDialog({
  tournament,
  onSaved,
  trigger,
}: {
  tournament?: Tournament;
  onSaved: (tournament: Tournament) => void;
  trigger: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(tournament?.name ?? "");
  const [description, setDescription] = useState(tournament?.description ?? "");
  const [startDate, setStartDate] = useState(
    tournament?.startDate ? tournament.startDate.split("T")[0] : "",
  );
  const [endDate, setEndDate] = useState(
    tournament?.endDate ? tournament.endDate.split("T")[0] : "",
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const editing = !!tournament;

  function handleOpenChange(next: boolean) {
    // Reopening an edit dialog starts from the saved values, not abandoned edits.
    if (next && tournament) {
      setName(tournament.name);
      setDescription(tournament.description ?? "");
      setStartDate(tournament.startDate ? tournament.startDate.split("T")[0] : "");
      setEndDate(tournament.endDate ? tournament.endDate.split("T")[0] : "");
    }
    if (next) setError(null);
    setOpen(next);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim() || !startDate || !endDate) {
      setError("Completá el nombre y las dos fechas.");
      return;
    }
    if (endDate < startDate) {
      setError("La fecha de fin no puede ser anterior a la de inicio.");
      return;
    }

    const input: CreateTournamentInput = {
      name: name.trim(),
      description: description.trim() || undefined,
      startDate: startDate,
      endDate: endDate,
    };

    setSubmitting(true);
    try {
      const saved =
        editing && tournament
          ? await updateTournament(tournament.id, input)
          : await createTournament(input);
      setOpen(false);
      if (!editing) {
        setName("");
        setDescription("");
        setStartDate("");
        setEndDate("");
      }
      toast.success(editing ? "Cambios guardados" : "Torneo creado", {
        description: editing
          ? `Actualizamos ${saved.name}.`
          : `${saved.name} quedó en borrador. Sumale equipos y partidos antes de activarlo.`,
      });
      onSaved(saved);
    } catch (e) {
      setError(apiErrorMessage(e, "No se pudo guardar el torneo."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {editing ? "Editar torneo" : "Crear torneo"}
          </DialogTitle>
          <DialogDescription>
            {editing
              ? "Cambiá el nombre, la descripción o las fechas."
              : "El torneo se crea en borrador: nadie lo ve hasta que lo actives."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <FormField label="Nombre" required>
            {(field) => (
              <Input
                {...field}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Copa Oficina 2026"
              />
            )}
          </FormField>

          <FormField label="Descripción">
            {(field) => (
              <Input
                {...field}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Torneo interno"
              />
            )}
          </FormField>

          <div className="grid grid-cols-2 gap-4">
            <FormField label="Fecha de inicio" required>
              {(field) => (
                <Input
                  {...field}
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                />
              )}
            </FormField>
            <FormField label="Fecha de fin" required>
              {(field) => (
                <Input
                  {...field}
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                />
              )}
            </FormField>
          </div>

          {error && (
            <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" disabled={submitting}>
            {submitting ? "Guardando…" : editing ? "Guardar cambios" : "Crear torneo"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
