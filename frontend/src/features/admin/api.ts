import { api } from "@/lib/api";
import type { Match } from "@/features/tournaments/types/tournament";

// Re-export Match type for backward compatibility (used by PredictionPage,
// AdminResultsPage, etc.). The two types are structurally identical.
export type MatchResult = Match;

// ── Matches (kept for backward compatibility) ─────────────────────────────

export async function getMatch(matchId: string): Promise<MatchResult> {
  const { data } = await api.get<MatchResult>(`/api/matches/${matchId}`);
  return data;
}

export async function submitResult(
  matchId: string,
  homeScore: number,
  awayScore: number,
): Promise<void> {
  await api.put(`/api/matches/${matchId}/result`, { homeScore, awayScore });
}

/** Get matches for a tournament, optionally filtered by status. */
export async function getMatches(
  tournamentId: string,
  status?: string,
): Promise<MatchResult[]> {
  const params: Record<string, string> = {};
  if (status) params.status = status;
  const { data } = await api.get<MatchResult[]>(
    `/api/tournaments/${tournamentId}/matches`,
    { params },
  );
  return data;
}
