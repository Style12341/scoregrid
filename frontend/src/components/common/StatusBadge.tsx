import { Badge } from "@/components/ui/badge";

/** docs/contracts.md — status ∈ DRAFT | ACTIVE | FINISHED | CANCELLED */
export type TournamentStatus = "DRAFT" | "ACTIVE" | "FINISHED" | "CANCELLED";

/** docs/contracts.md — status ∈ SCHEDULED | IN_PROGRESS | FINISHED | POSTPONED | CANCELLED */
export type MatchStatus =
  | "SCHEDULED"
  | "IN_PROGRESS"
  | "FINISHED"
  | "POSTPONED"
  | "CANCELLED";

type BadgeVariant = "active" | "draft" | "finished" | "closed" | "secondary";

const tournamentStatuses: Record<
  TournamentStatus,
  { label: string; variant: BadgeVariant }
> = {
  DRAFT: { label: "Borrador", variant: "draft" },
  ACTIVE: { label: "Activo", variant: "active" },
  FINISHED: { label: "Finalizado", variant: "finished" },
  CANCELLED: { label: "Cancelado", variant: "closed" },
};

const matchStatuses: Record<MatchStatus, { label: string; variant: BadgeVariant }> = {
  SCHEDULED: { label: "Programado", variant: "secondary" },
  IN_PROGRESS: { label: "En juego", variant: "active" },
  FINISHED: { label: "Finalizado", variant: "finished" },
  POSTPONED: { label: "Pospuesto", variant: "draft" },
  CANCELLED: { label: "Cancelado", variant: "closed" },
};

/**
 * One place that turns a contract status into a colour and a Spanish label.
 *
 * Centralised deliberately: three streams render these badges, and three
 * independent translations of "FINISHED" is how a UI starts looking untended.
 */
export function TournamentStatusBadge({ status }: { status: TournamentStatus }) {
  const { label, variant } = tournamentStatuses[status];
  return <Badge variant={variant}>{label}</Badge>;
}

export function MatchStatusBadge({ status }: { status: MatchStatus }) {
  const { label, variant } = matchStatuses[status];
  return <Badge variant={variant}>{label}</Badge>;
}

/** predictionsOpen from the match payload — the kickoff lock, server-computed. */
export function PredictionLockBadge({ open }: { open: boolean }) {
  return open ? (
    <Badge variant="active">Pronósticos abiertos</Badge>
  ) : (
    <Badge variant="closed">Pronósticos cerrados</Badge>
  );
}
