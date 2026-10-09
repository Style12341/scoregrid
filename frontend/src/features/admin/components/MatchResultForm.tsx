import { useState } from "react";
import type { FormEvent } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ScoreInput } from "@/components/common/ScoreInput";
import { parseScore } from "@/components/common/score";
import { loadResult } from "@/features/tournaments/api/tournaments";
import type { Match } from "@/features/tournaments/types/tournament";
import { apiErrorMessage } from "@/features/tournaments/errors";

/**
 * Inline score entry for one match: two compact goal fields and the action.
 *
 * Shared by "Cargar resultados" and the Partidos tab of the tournament manage
 * page, so loading a result works and reads the same in both places. A
 * finished match offers "Corregir resultado": re-submitting republishes the
 * event and the score service recomputes (it is idempotent).
 */
export function MatchResultForm({
  match,
  onSaved,
}: {
  match: Match;
  onSaved: () => void;
}) {
  const isCorrection = match.status === "FINISHED";
  const [homeScore, setHomeScore] = useState(
    match.homeScore !== null ? String(match.homeScore) : "",
  );
  const [awayScore, setAwayScore] = useState(
    match.awayScore !== null ? String(match.awayScore) : "",
  );
  const [submitting, setSubmitting] = useState(false);

  const home = parseScore(homeScore);
  const away = parseScore(awayScore);
  const unchanged =
    isCorrection && home === match.homeScore && away === match.awayScore;
  const matchLabel = `${match.homeTeam.name} – ${match.awayTeam.name}`;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (home === null || away === null) {
      toast.error("Faltan los goles", {
        description: "Ingrese los goles de ambos equipos, entre 0 y 99.",
      });
      return;
    }

    setSubmitting(true);
    try {
      await loadResult(match.id, { homeScore: home, awayScore: away });
      toast.success(
        isCorrection ? "Resultado corregido" : "Resultado cargado",
        {
          description: `${match.homeTeam.name} ${home} – ${away} ${match.awayTeam.name}. Puntajes recalculados.`,
        },
      );
      onSaved();
    } catch (error) {
      toast.error(
        isCorrection
          ? "No se pudo corregir el resultado"
          : "No se pudo cargar el resultado",
        {
          description: apiErrorMessage(
            error,
            "Vuelva a intentarlo en unos segundos.",
          ),
        },
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="flex items-center gap-2"
      noValidate
    >
      <ScoreInput
        size="sm"
        value={homeScore}
        onChange={setHomeScore}
        teamName={match.homeTeam.name}
        aria-label={`Goles de ${match.homeTeam.name}`}
        disabled={submitting}
      />
      <span className="text-muted-foreground" aria-hidden="true">
        –
      </span>
      <ScoreInput
        size="sm"
        value={awayScore}
        onChange={setAwayScore}
        teamName={match.awayTeam.name}
        aria-label={`Goles de ${match.awayTeam.name}`}
        disabled={submitting}
      />
      <Button
        type="submit"
        size="sm"
        variant={isCorrection ? "outline" : "success"}
        disabled={submitting || unchanged}
        aria-label={`${isCorrection ? "Corregir resultado" : "Cargar resultado"}: ${matchLabel}`}
      >
        {submitting
          ? "Guardando…"
          : isCorrection
            ? "Corregir resultado"
            : "Cargar resultado"}
      </Button>
    </form>
  );
}
