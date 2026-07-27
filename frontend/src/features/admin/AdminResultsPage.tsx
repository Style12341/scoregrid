import { useState } from "react";
import type { FormEvent } from "react";
import { usePageHeader } from "@/components/layout/page-header";
import { FormField } from "@/components/common/FormField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { submitResult } from "./api";

export function AdminResultsPage() {
  usePageHeader("Cargar resultados");

  const [matchId, setMatchId] = useState("");
  const [homeScore, setHomeScore] = useState("");
  const [awayScore, setAwayScore] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    const hs = parseInt(homeScore, 10);
    const as = parseInt(awayScore, 10);

    if (!matchId.trim()) {
      setError("Ingresá el ID del partido.");
      return;
    }
    if (isNaN(hs) || isNaN(as) || hs < 0 || hs > 99 || as < 0 || as > 99) {
      setError("Ingresá goles válidos (0–99).");
      return;
    }

    setSubmitting(true);
    try {
      await submitResult(matchId.trim(), hs, as);
      setSuccess(`Resultado ${hs}–${as} cargado para el partido ${matchId}.`);
      setHomeScore("");
      setAwayScore("");
      setMatchId("");
    } catch (e) {
      setError("No se pudo cargar el resultado. Verificá el ID del partido.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-lg">
      <Card>
        <CardHeader>
          <CardTitle>Cargar resultado de partido</CardTitle>
          <p className="text-sm text-muted-foreground">
            Al cargar un resultado, se dispara automáticamente la puntuación de todos los
            pronósticos de ese partido.
          </p>
        </CardHeader>
        <Separator />
        <CardContent className="pt-6">
          <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
            <FormField label="ID del partido" required>
              {(field) => (
                <Input
                  {...field}
                  type="text"
                  value={matchId}
                  onChange={(e) => setMatchId(e.target.value)}
                  placeholder="Ej: 99"
                />
              )}
            </FormField>

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
                {success}
              </p>
            )}

            <Button type="submit" size="block" disabled={submitting}>
              {submitting ? "Cargando…" : "Cargar resultado"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
