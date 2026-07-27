import { api } from "@/lib/api";

/**
 * docs/contracts.md#score-service — both endpoints return a plain array.
 *
 * `position` is computed server-side at query time and is absolute, so entry 1
 * of page 2 is position 51, not 1. Never renumber from the array index.
 */
export interface TournamentRankingEntry {
  position: number;
  userId: string;
  username: string;
  points: number;
  hits: number;
  exactHits: number;
  predictionsScored: number;
  /** 0..1, already `hits / predictionsScored`, or 0 when nothing was scored. */
  accuracy: number;
}

export interface GlobalRankingEntry {
  position: number;
  userId: string;
  username: string;
  totalPoints: number;
  tournamentsPlayed: number;
  totalHits: number;
  exactHits: number;
  predictionsScored: number;
  accuracy: number;
  averagePointsPerTournament: number;
}

export const RANKING_PAGE_SIZE = 50;

export async function fetchTournamentRanking(
  tournamentId: string,
  page = 0,
  size = RANKING_PAGE_SIZE,
): Promise<TournamentRankingEntry[]> {
  const { data } = await api.get<TournamentRankingEntry[]>(
    `/api/rankings/tournament/${tournamentId}`,
    { params: { page, size } },
  );
  return data;
}

export async function fetchGlobalRanking(
  page = 0,
  size = RANKING_PAGE_SIZE,
): Promise<GlobalRankingEntry[]> {
  const { data } = await api.get<GlobalRankingEntry[]>("/api/rankings/global", {
    params: { page, size },
  });
  return data;
}
