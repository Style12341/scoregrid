import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useParams } from "react-router-dom";
import { usePageHeader } from "@/components/layout/page-header";
import { FormField } from "@/components/common/FormField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PredictionLockBadge, MatchStatusBadge } from "@/components/common/StatusBadge";
import { LoadingState, ErrorState } from "@/components/common/states";
import { Separator } from "@/components/ui/separator";
import { toApiError } from "@/lib/api";
import {
  createPrediction,
  updatePrediction,
  getMyPredictionForMatch,
  type Prediction,
} from "./api";
import { getMatch, type MatchResult } from "@/features/admin/api";

export function PredictionPage() {
  const { matchId } = useParams<{ matchId: string }>();
  usePageHeader("Pronosticar");

  const [match, setMatch] = useState<MatchResult | null>(null);
  const [existing, setExisting] = useState<Prediction | null>(null);
  const [homeScore, setHomeScore] = useState("");
  const [awayScore, setAwayScore] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (!matchId) return;
    Promise.all([getMatch(matchId), getMyPredictionForMatch(matchId)])
      .then(([m, p]) => {
        setMatch(m);
        if (p) {
          setExisting(p);
          setHomeScore(String(p.homeScore));
          setAwayScore(String(p.awayScore));
        }
      })
      .catch(() => setError("No se pudo cargar el partido."))
      .finally(() => setLoading(false));
  }, [matchId]);

  if (loading) return <LoadingState />;
  if (error && !match) return <ErrorState title="Error" description={error} />;
  if (!match) return <ErrorState title="No encontrado" description="El partido no existe." />;

  const isLocked = !match.predictionsOpen;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSuccess(false);

    const hs = parseInt(homeScore, 10);
    const as = parseInt(awayScore, 10);

    if (isNaN(hs) || isNaN(as) || hs < 0 || hs > 99 || as < 0 || as > 99) {
      setError("Ingresá goles válidos (0–99).");
      return;
    }

    setSubmitting(true);
    try {
      if (existing) {
        await updatePrediction(existing.id, { homeScore: hs, awayScore: as });
      } else {
        await createPrediction({ matchId: matchId!, homeScore: hs, awayScore: as });
      }
      setSuccess(true);
      if (!existing) {
        setExisting({ id: "", userId: "", tournamentId: match!.tournamentId, matchId: matchId!, predictionType: "EXACT_SCORE", homeScore: hs, awayScore: as, derivedOutcome: "", locked: false, createdAt: "", updatedAt: "" });
      }
    } catch (e) {
      const apiErr = toApiError(e);
      setError(apiErr?.message ?? "Ocurrió un error al guardar el pronóstico.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-lg">
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-xl">
              {match.homeTeam.shortName} vs {match.awayTeam.shortName}
            </CardTitle>
            <PredictionLockBadge open={match.predictionsOpen} />
          </div>
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <MatchStatusBadge status={match.status as never} />
            <span>
              {new Date(match.startTime).toLocaleDateString("es-AR", {
                day: "numeric",
                month: "short",
                hour: "2-digit",
                minute: "2-digit",
              })}
            </span>
          </div>
        </CardHeader>
        <Separator />
        <CardContent className="pt-6">
          {isLocked ? (
            <p className="text-sm text-muted-foreground">
              Los pronósticos para este partido están cerrados. Ya no se pueden modificar.
            </p>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
              <div className="grid grid-cols-2 gap-4">
                <FormField label="Goles local" required>
                  {(field) => (
                    <Input
                      {...field}
                      type="number"
                      min={0}
                      max={99}
                      value={homeScore}
                      onChange={(e) => setHomeScore(e.target.value)}
                      placeholder="0"
                    />
                  )}
                </FormField>

                <FormField label="Goles visitante" required>
                  {(field) => (
                    <Input
                      {...field}
                      type="number"
                      min={0}
                      max={99}
                      value={awayScore}
                      onChange={(e) => setAwayScore(e.target.value)}
                      placeholder="0"
                    />
                  )}
                </FormField>
              </div>

              {error && (
                <p role="alert" className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive">
                  {error}
                </p>
              )}

              {success && (
                <p className="rounded-md bg-success/10 px-3.5 py-3 text-sm font-bold text-success">
                  Pronóstico guardado correctamente.
                </p>
              )}

              <Button type="submit" size="block" disabled={submitting}>
                {submitting ? "Guardando…" : existing ? "Actualizar pronóstico" : "Enviar pronóstico"}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
