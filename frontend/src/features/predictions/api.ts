import { api } from "@/lib/api";

export interface Prediction {
  id: string;
  userId: string;
  tournamentId: string;
  matchId: string;
  predictionType: string;
  homeScore: number;
  awayScore: number;
  derivedOutcome: string;
  locked: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePredictionPayload {
  matchId: string;
  homeScore: number;
  awayScore: number;
}

export interface UpdatePredictionPayload {
  homeScore: number;
  awayScore: number;
}

export async function createPrediction(payload: CreatePredictionPayload): Promise<Prediction> {
  const { data } = await api.post<Prediction>("/api/predictions", payload);
  return data;
}

export async function updatePrediction(id: string, payload: UpdatePredictionPayload): Promise<Prediction> {
  const { data } = await api.put<Prediction>(`/api/predictions/${id}`, payload);
  return data;
}

export async function getMyPredictionForMatch(matchId: string): Promise<Prediction | null> {
  try {
    const { data } = await api.get<Prediction>(`/api/predictions/me/match/${matchId}`);
    return data;
  } catch {
    return null;
  }
}

export async function getMyPredictions(
  tournamentId?: string,
  page = 0,
  size = 20
): Promise<Prediction[]> {
  const params: Record<string, string | number> = { page, size };
  if (tournamentId) params.tournamentId = tournamentId;
  const { data } = await api.get<Prediction[]>("/api/predictions/me", { params });
  return data;
}
