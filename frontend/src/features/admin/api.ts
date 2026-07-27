import { api } from "@/lib/api";

export interface MatchResult {
  id: string;
  tournamentId: string;
  homeTeam: { id: string; name: string; shortName: string };
  awayTeam: { id: string; name: string; shortName: string };
  startTime: string;
  status: string;
  homeScore: number | null;
  awayScore: number | null;
  predictionsOpen: boolean;
}

export async function getMatch(matchId: string): Promise<MatchResult> {
  const { data } = await api.get<MatchResult>(`/api/matches/${matchId}`);
  return data;
}

export async function submitResult(
  matchId: string,
  homeScore: number,
  awayScore: number
): Promise<void> {
  await api.put(`/api/matches/${matchId}/result`, { homeScore, awayScore });
}

export async function getMatches(status?: string): Promise<MatchResult[]> {
  const params: Record<string, string> = {};
  if (status) params.status = status;
  const { data } = await api.get<MatchResult[]>("/api/tournaments/.../matches", { params });
  return data;
}
