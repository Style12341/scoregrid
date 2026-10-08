import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { deleteTournament, updateTournamentStatus } from "@/features/tournaments/api/tournaments";
import type { Match, Tournament, TournamentStatus } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";
import { isPendingMatch } from "@/features/tournaments/format";

/**
 * The status transitions a tournament allows, each behind a confirmation that
 * states its consequence. Only the transitions valid for the current status
 * are rendered: DRAFT → ACTIVE → FINISHED, and DRAFT/ACTIVE → CANCELLED.
 * Deleting is allowed only while DRAFT (docs/contracts.md).
 */
export function TournamentLifecycleActions({
  tournament,
  matches,
  onChanged,
}: {
  tournament: Tournament;
  matches: Match[];
  onChanged: () => void;
}) {
  const navigate = useNavigate();
  const { status, name } = tournament;
  // A tournament cannot finish while any match is pending (docs/contracts.md).
  const pending = matches.filter(isPendingMatch).length;

  async function transition(next: TournamentStatus, done: string, failed: string) {
    try {
      await updateTournamentStatus(tournament.id, next);
      toast.success(done, { description: name });
      onChanged();
    } catch (error) {
      toast.error(failed, {
        description: apiErrorMessage(error, "Volvé a intentarlo en unos segundos."),
      });
      throw error;
    }
  }

  async function remove() {
    try {
      await deleteTournament(tournament.id);
      toast.success("Torneo eliminado", { description: name });
      navigate("/admin");
    } catch (error) {
      toast.error("No se pudo eliminar el torneo", {
        description: apiErrorMessage(error, "Volvé a intentarlo en unos segundos."),
      });
      throw error;
    }
  }

  return (
    <>
      {status === "DRAFT" && (
        <ConfirmDialog
          trigger={<Button variant="success">Activar torneo</Button>}
          title={`¿Activar ${name}?`}
          description={
            <>
              <p>Los participantes van a poder verlo, inscribirse y pronosticar los partidos.</p>
              <p>Un torneo activo ya no vuelve a borrador ni se puede eliminar.</p>
            </>
          }
          confirmLabel="Activar torneo"
          pendingLabel="Activando…"
          onConfirm={() => transition("ACTIVE", "Torneo activado", "No se pudo activar el torneo")}
        />
      )}

      {status === "ACTIVE" && (
        <ConfirmDialog
          trigger={<Button>Finalizar torneo</Button>}
          title={`¿Finalizar ${name}?`}
          description={
            <>
              {pending > 0 && (
                <p className="rounded-md bg-destructive/10 px-3.5 py-3 font-bold text-destructive">
                  {pending === 1
                    ? "Queda 1 partido sin terminar (programado, en juego o pospuesto)."
                    : `Quedan ${pending} partidos sin terminar (programados, en juego o pospuestos).`}{" "}
                  Cargá sus resultados o cancelalos antes de finalizar.
                </p>
              )}
              <p>
                Se cierran los pronósticos de todos los partidos y el torneo pasa a Finalizado.
              </p>
              <p>No se puede deshacer.</p>
            </>
          }
          confirmLabel="Finalizar torneo"
          pendingLabel="Finalizando…"
          confirmDisabled={pending > 0}
          onConfirm={() =>
            transition("FINISHED", "Torneo finalizado", "No se pudo finalizar el torneo")
          }
        />
      )}

      {(status === "DRAFT" || status === "ACTIVE") && (
        <ConfirmDialog
          trigger={<Button variant="outline">Cancelar torneo</Button>}
          title={`¿Cancelar ${name}?`}
          description={
            <>
              <p>
                El torneo pasa a Cancelado y nadie más puede inscribirse ni pronosticar.
              </p>
              <p>No se puede deshacer.</p>
            </>
          }
          confirmLabel="Cancelar torneo"
          pendingLabel="Cancelando…"
          destructive
          onConfirm={() =>
            transition("CANCELLED", "Torneo cancelado", "No se pudo cancelar el torneo")
          }
        />
      )}

      {status === "DRAFT" && (
        <ConfirmDialog
          trigger={
            <Button variant="ghost" className="text-destructive hover:text-destructive">
              Eliminar torneo
            </Button>
          }
          title={`¿Eliminar ${name}?`}
          description={
            <>
              <p>Se borra el torneo de forma permanente.</p>
              <p>No se puede deshacer.</p>
            </>
          }
          confirmLabel="Eliminar torneo"
          pendingLabel="Eliminando…"
          destructive
          onConfirm={remove}
        />
      )}
    </>
  );
}
