import { AlertTriangle, Inbox, Loader2 } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

function StateShell({
  icon,
  title,
  description,
  action,
  className,
  tone = "muted",
}: {
  icon: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
  tone?: "muted" | "danger";
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-border bg-card px-6 py-14 text-center",
        className,
      )}
    >
      <div
        className={cn(
          "grid size-12 place-items-center rounded-full",
          tone === "danger"
            ? "bg-destructive/10 text-destructive"
            : "bg-muted text-muted-foreground",
        )}
      >
        {icon}
      </div>
      <p className="text-base font-bold">{title}</p>
      {description && (
        <p className="max-w-md text-sm text-muted-foreground">{description}</p>
      )}
      {action}
    </div>
  );
}

/** Nothing to show, and that is normal — no results, empty list. */
export function EmptyState({
  title = "No hay nada por acá todavía",
  description,
  action,
  className,
}: {
  title?: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <StateShell
      icon={<Inbox className="size-6" />}
      title={title}
      description={description}
      action={action}
      className={className}
    />
  );
}

/**
 * Something failed. Always offer the retry — a dead end with no way forward is
 * the most common failure of an error state.
 */
export function ErrorState({
  title = "No pudimos cargar esta información",
  description = "Volvé a intentarlo. Si el problema persiste, es posible que el servicio no esté disponible.",
  onRetry,
  className,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <StateShell
      tone="danger"
      icon={<AlertTriangle className="size-6" />}
      title={title}
      description={description}
      className={className}
      action={
        onRetry && (
          <Button variant="secondary" onClick={onRetry}>
            Reintentar
          </Button>
        )
      }
    />
  );
}

/** In flight. role="status" so screen readers announce the wait. */
export function LoadingState({
  label = "Cargando…",
  className,
}: {
  label?: string;
  className?: string;
}) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-lg border border-border bg-card px-6 py-14 text-center",
        className,
      )}
    >
      <Loader2 className="size-6 animate-spin text-primary" aria-hidden="true" />
      <p className="text-sm font-bold text-muted-foreground">{label}</p>
    </div>
  );
}
