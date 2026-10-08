import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { teamCode } from "../format";
import { computeStandings, type StandingRow } from "../standings";
import type { Match, TeamRef } from "../types/tournament";

const COLUMNS: { key: Exclude<keyof StandingRow, "team">; abbr: string; title: string }[] = [
  { key: "played", abbr: "PJ", title: "Partidos jugados" },
  { key: "won", abbr: "G", title: "Ganados" },
  { key: "drawn", abbr: "E", title: "Empatados" },
  { key: "lost", abbr: "P", title: "Perdidos" },
  { key: "goalsFor", abbr: "GF", title: "Goles a favor" },
  { key: "goalsAgainst", abbr: "GC", title: "Goles en contra" },
  { key: "goalDifference", abbr: "DG", title: "Diferencia de gol" },
];

function signed(value: number): string {
  return value > 0 ? `+${value}` : String(value);
}

/** The table of one group: its teams ordered by points, from its finished matches. */
export function GroupStandings({
  groupName,
  teams,
  matches,
}: {
  groupName: string;
  teams: TeamRef[];
  /** The group's matches; only FINISHED ones count. */
  matches: Match[];
}) {
  const rows = computeStandings(teams, matches);

  return (
    <Table>
      <caption className="sr-only">Tabla de posiciones de {groupName}</caption>
      <TableHeader>
        <TableRow>
          <TableHead className="w-8 px-2 text-right">#</TableHead>
          <TableHead className="px-2">Equipo</TableHead>
          {COLUMNS.map((column) => (
            <TableHead key={column.key} className="px-2 text-right">
              <abbr title={column.title} className="no-underline">
                {column.abbr}
              </abbr>
            </TableHead>
          ))}
          <TableHead className="px-2 text-right">
            <abbr title="Puntos" className="no-underline">
              Pts
            </abbr>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row, index) => {
          const code = teamCode(row.team);
          return (
            <TableRow key={row.team.id}>
              <TableCell className="px-2 text-right tabular-nums text-muted-foreground">
                {index + 1}
              </TableCell>
              <TableCell className="px-2 font-semibold">
                {row.team.name}
                {code && (
                  <span className="ml-1.5 text-xs font-semibold text-muted-foreground">{code}</span>
                )}
              </TableCell>
              {COLUMNS.map((column) => (
                <TableCell key={column.key} className="px-2 text-right tabular-nums">
                  {column.key === "goalDifference" ? signed(row.goalDifference) : row[column.key]}
                </TableCell>
              ))}
              <TableCell className="px-2 text-right font-extrabold tabular-nums">
                {row.points}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
