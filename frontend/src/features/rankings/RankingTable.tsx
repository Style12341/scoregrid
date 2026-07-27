import type { ReactNode } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

/**
 * Shared shell for both ranking screens.
 *
 * Feature-local on purpose: it is not a design-system primitive, and putting it
 * in components/ would mean Streams B and C inherit a table shaped entirely
 * around rankings.
 */

/** Mock reference: the top three carry medal colours; everyone else is plain. */
function PositionCell({ position }: { position: number }) {
  const medal =
    position === 1
      ? "bg-[#fbbf24] text-[#78350f]"
      : position === 2
        ? "bg-[#cbd5e1] text-[#334155]"
        : position === 3
          ? "bg-[#d97706]/80 text-white"
          : "bg-muted text-muted-foreground";

  return (
    <span
      className={cn(
        "inline-grid size-7 place-items-center rounded-full text-[13px] font-extrabold",
        medal,
      )}
    >
      {position}
    </span>
  );
}

export interface RankingColumn<T> {
  key: string;
  header: string;
  /** Right-aligned by default — these are all numbers except the player. */
  align?: "left" | "right";
  render: (entry: T) => ReactNode;
}

export function RankingTable<T extends { position: number; userId: string; username: string }>({
  entries,
  columns,
  currentUserId,
  caption,
}: {
  entries: T[];
  columns: RankingColumn<T>[];
  /** Highlights the signed-in player's row so they can find themselves. */
  currentUserId?: string;
  caption: string;
}) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <caption className="sr-only">{caption}</caption>
        <TableHeader>
          <TableRow>
            <TableHead className="w-16">#</TableHead>
            <TableHead>Participante</TableHead>
            {columns.map((column) => (
              <TableHead
                key={column.key}
                className={column.align === "left" ? undefined : "text-right"}
              >
                {column.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>

        <TableBody>
          {entries.map((entry) => {
            const isCurrentUser = entry.userId === currentUserId;

            return (
              <TableRow
                key={entry.userId}
                className={cn(isCurrentUser && "bg-primary/5 font-bold")}
              >
                <TableCell>
                  <PositionCell position={entry.position} />
                </TableCell>
                <TableCell className="font-semibold">
                  {entry.username}
                  {isCurrentUser && (
                    <span className="ml-2 rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-bold text-primary">
                      Vos
                    </span>
                  )}
                </TableCell>
                {columns.map((column) => (
                  <TableCell
                    key={column.key}
                    className={column.align === "left" ? undefined : "text-right tabular-nums"}
                  >
                    {column.render(entry)}
                  </TableCell>
                ))}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
