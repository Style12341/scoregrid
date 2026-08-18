import { api } from "@/lib/api";
import type {
  Tournament,
  TournamentPage,
  CreateTournamentInput,
  UpdateTournamentInput,
  Group,
  CreateGroupInput,
  Phase,
  CreatePhaseInput,
  Match,
  CreateMatchInput,
  UpdateMatchInput,
  SetMatchResultInput,
  Team,
  CreateTeamInput,
  UpdateTeamInput,
  EnrolmentStatus,
} from "../types/tournament";
import { isNotFoundError } from "../errors";

// ── Tournaments ────────────────────────────────────────────────────────────

export async function listTournaments(
  status?: string,
  page = 0,
  size = 20,
): Promise<TournamentPage> {
  const params: Record<string, string | number> = { page, size };
  if (status) params.status = status;
  const { data } = await api.get<TournamentPage>("/api/tournaments", { params });
  return data;
}

export async function getTournament(id: string): Promise<Tournament> {
  const { data } = await api.get<Tournament>(`/api/tournaments/${id}`);
  return data;
}

export async function createTournament(
  input: CreateTournamentInput,
): Promise<Tournament> {
  const { data } = await api.post<Tournament>("/api/tournaments", input);
  return data;
}

export async function updateTournament(
  id: string,
  input: UpdateTournamentInput,
): Promise<Tournament> {
  const { data } = await api.put<Tournament>(`/api/tournaments/${id}`, input);
  return data;
}

export async function deleteTournament(id: string): Promise<void> {
  await api.delete(`/api/tournaments/${id}`);
}

export async function updateTournamentStatus(
  id: string,
  status: string,
): Promise<Tournament> {
  const { data } = await api.patch<Tournament>(
    `/api/tournaments/${id}/status`,
    { status },
  );
  return data;
}

export async function joinTournament(id: string): Promise<void> {
  await api.post(`/api/tournaments/${id}/join`);
}

export async function getEnrolment(
  tournamentId: string,
  userId: string,
): Promise<EnrolmentStatus | null> {
  try {
    const { data } = await api.get<EnrolmentStatus>(
      `/api/tournaments/${tournamentId}/participants/${userId}`,
    );
    return data;
  } catch (error) {
    if (isNotFoundError(error)) return null;
    throw error;
  }
}

// ── Groups ─────────────────────────────────────────────────────────────────

export async function listGroups(tournamentId: string): Promise<Group[]> {
  const { data } = await api.get<Group[]>(
    `/api/tournaments/${tournamentId}/groups`,
  );
  return data;
}

export async function createGroup(
  tournamentId: string,
  input: CreateGroupInput,
): Promise<Group> {
  const { data } = await api.post<Group>(
    `/api/tournaments/${tournamentId}/groups`,
    input,
  );
  return data;
}

export async function assignTeamsToGroup(
  groupId: string,
  teamIds: string[],
): Promise<Team[]> {
  const { data } = await api.post<Team[]>(`/api/groups/${groupId}/teams`, {
    teamIds,
  });
  return data;
}

export async function getGroupTeams(groupId: string): Promise<Team[]> {
  const { data } = await api.get<Team[]>(`/api/groups/${groupId}/teams`);
  return data;
}

// ── Phases ─────────────────────────────────────────────────────────────────

export async function listPhases(tournamentId: string): Promise<Phase[]> {
  const { data } = await api.get<Phase[]>(
    `/api/tournaments/${tournamentId}/phases`,
  );
  return data;
}

export async function createPhase(
  tournamentId: string,
  input: CreatePhaseInput,
): Promise<Phase> {
  const { data } = await api.post<Phase>(
    `/api/tournaments/${tournamentId}/phases`,
    input,
  );
  return data;
}

// ── Matches ────────────────────────────────────────────────────────────────

export async function listMatches(
  tournamentId: string,
  status?: string,
): Promise<Match[]> {
  const params: Record<string, string> = {};
  if (status) params.status = status;
  const { data } = await api.get<Match[]>(
    `/api/tournaments/${tournamentId}/matches`,
    { params },
  );
  return data;
}

export async function getMatch(matchId: string): Promise<Match> {
  const { data } = await api.get<Match>(`/api/matches/${matchId}`);
  return data;
}

export async function createMatch(
  tournamentId: string,
  input: CreateMatchInput,
): Promise<Match> {
  const { data } = await api.post<Match>(
    `/api/tournaments/${tournamentId}/matches`,
    input,
  );
  return data;
}

export async function updateMatch(
  matchId: string,
  input: UpdateMatchInput,
): Promise<Match> {
  const { data } = await api.put<Match>(`/api/matches/${matchId}`, input);
  return data;
}

export async function loadResult(
  matchId: string,
  input: SetMatchResultInput,
): Promise<void> {
  await api.put(`/api/matches/${matchId}/result`, input);
}

// ── Teams ──────────────────────────────────────────────────────────────────

export async function listTeams(): Promise<Team[]> {
  const { data } = await api.get<Team[]>("/api/teams");
  return data;
}

export async function createTeam(input: CreateTeamInput): Promise<Team> {
  const { data } = await api.post<Team>("/api/teams", input);
  return data;
}

export async function updateTeam(
  id: string,
  input: UpdateTeamInput,
): Promise<Team> {
  const { data } = await api.put<Team>(`/api/teams/${id}`, input);
  return data;
}

export async function assignTeamsToTournament(
  tournamentId: string,
  teamIds: string[],
): Promise<Team[]> {
  const { data } = await api.post<Team[]>(
    `/api/tournaments/${tournamentId}/teams`,
    { teamIds },
  );
  return data;
}

export async function getTournamentTeams(tournamentId: string): Promise<Team[]> {
  const { data } = await api.get<Team[]>(
    `/api/tournaments/${tournamentId}/teams`,
  );
  return data;
}
