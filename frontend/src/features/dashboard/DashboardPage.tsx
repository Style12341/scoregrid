import { useCallback } from "react";
import { Link } from "react-router-dom";
import { ClipboardList, Globe2, Trophy } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useAuth } from "@/auth/AuthContext";
import { MetricCard } from "@/components/common/MetricCard";
import { PageTitle } from "@/components/common/PageTitle";
import { LoadingState } from "@/components/common/states";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { usePageHeader } from "@/components/layout/page-header";
import { fetchGlobalRanking } from "@/features/rankings/api";
import type { GlobalRankingEntry } from "@/features/rankings/api";
import { formatAccuracy, formatCount } from "@/features/rankings/format";
import { useRanking } from "@/features/rankings/useRanking";

function ShortcutCard({
  icon: Icon,
  title,
  description,
  to,
  cta,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  to: string;
  cta: string;
}) {
  return (
    <Card className="flex flex-col gap-3 px-5">
      <div className="grid size-10 place-items-center rounded-lg bg-primary/10 text-primary">
        <Icon className="size-5" aria-hidden="true" />
      </div>
      <div className="flex-1">
        <h3 className="text-base font-bold">{title}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      </div>
      <Button asChild variant="secondary" className="self-start">
        <Link to={to}>{cta}</Link>
      </Button>
    </Card>
  );
}

/**
 * The landing screen after login.
 *
 * Deliberately built to survive a partial system. The only data it fetches is
 * the global ranking; tournament-service is not up yet, so anything needing it
 * is a link rather than a number. When the ranking cannot be loaded, the page
 * still renders its shortcuts instead of collapsing into a full-page error —
 * a dashboard that disappears because one of five services is down is worse
 * than a dashboard with one section missing.
 */
export function DashboardPage() {
  const { user } = useAuth();
  usePageHeader("Panel principal", `Hola, ${user?.username ?? ""}.`);

  const load = useCallback(() => fetchGlobalRanking(), []);
  const { entries, status } = useRanking<GlobalRankingEntry>(load);

  const me = entries.find((entry) => entry.userId === user?.id);

  return (
    <div className="flex flex-col gap-6">
      <section>
        <PageTitle
          title="Tu resumen"
          action={
            <Button asChild variant="ghost">
              <Link to="/rankings/global">Ver ranking completo</Link>
            </Button>
          }
        />

        {status === "loading" && <LoadingState label="Cargando tu resumen…" />}

        {status === "ready" && me && (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              label="Posición global"
              value={`#${me.position}`}
              note={`De ${formatCount(entries.length)} participantes`}
              tone="muted"
            />
            <MetricCard
              label="Puntos totales"
              value={formatCount(me.totalPoints)}
              note={`${formatCount(me.exactHits)} resultados exactos`}
            />
            <MetricCard
              label="Efectividad"
              value={formatAccuracy(me.accuracy)}
              note={`${formatCount(me.totalHits)} de ${formatCount(me.predictionsScored)} pronósticos`}
              tone="muted"
            />
            <MetricCard
              label="Torneos jugados"
              value={formatCount(me.tournamentsPlayed)}
              note="Con al menos un pronóstico puntuado"
              tone="muted"
            />
          </div>
        )}

        {status === "ready" && !me && (
          <Card className="px-5">
            <p className="text-sm text-muted-foreground">
              Todavía no tenés pronósticos puntuados. Inscribite en un torneo y
              cargá tu primer pronóstico: en cuanto se cargue el resultado del
              partido, vas a ver tus puntos acá.
            </p>
          </Card>
        )}

        {status === "error" && (
          <Card className="px-5">
            <p className="text-sm text-muted-foreground">
              No pudimos cargar tus puntos en este momento. El resto del panel
              sigue disponible.
            </p>
          </Card>
        )}
      </section>

      <section>
        <PageTitle title="Accesos rápidos" />
        <div className="grid gap-4 md:grid-cols-3">
          <ShortcutCard
            icon={Trophy}
            title="Torneos"
            description="Mirá los torneos abiertos, inscribite y consultá el fixture."
            to="/tournaments"
            cta="Ver torneos"
          />
          <ShortcutCard
            icon={ClipboardList}
            title="Mis pronósticos"
            description="Revisá lo que pronosticaste y cuántos puntos sumó cada partido."
            to="/predictions"
            cta="Ver mis pronósticos"
          />
          <ShortcutCard
            icon={Globe2}
            title="Ranking global"
            description="Posiciones acumuladas de todos los torneos."
            to="/rankings/global"
            cta="Ver ranking"
          />
        </div>
      </section>
    </div>
  );
}
