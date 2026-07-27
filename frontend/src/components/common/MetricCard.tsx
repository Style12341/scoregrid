import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";

/** Mock reference: .metric-card — muted label, big number, coloured note. */
export function MetricCard({
  label,
  value,
  note,
  tone = "success",
  className,
}: {
  label: string;
  value: ReactNode;
  note?: string;
  tone?: "success" | "muted" | "danger";
  className?: string;
}) {
  return (
    <Card className={cn("gap-0 px-5", className)}>
      <h3 className="mb-2.5 text-[13px] font-semibold text-muted-foreground">{label}</h3>
      <div className="text-3xl font-extrabold">{value}</div>
      {note && (
        <p
          className={cn(
            "mt-2 text-[13px] font-semibold",
            tone === "success" && "text-success",
            tone === "muted" && "text-muted-foreground",
            tone === "danger" && "text-destructive",
          )}
        >
          {note}
        </p>
      )}
    </Card>
  );
}

/** Mock reference: .mini-stat — the small boxed counters on a tournament card. */
export function MiniStat({ value, label }: { value: ReactNode; label: string }) {
  return (
    <div className="min-w-[90px] rounded-md border border-border bg-muted px-3 py-2.5">
      <strong className="block text-[17px]">{value}</strong>
      <span className="text-xs text-muted-foreground">{label}</span>
    </div>
  );
}
