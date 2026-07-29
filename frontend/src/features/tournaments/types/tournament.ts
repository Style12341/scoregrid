/** docs/contracts.md — tournament, group, phase, match, team shapes. */

import type { MatchStatus } from "@/components/common/StatusBadge";

// ── Tournament ────────────────────────────────────────────────────────────

export const TOURNAMENT_STATUS = {
  DRAFT: "DRAFT",
  ACTIVE: "ACTIVE",
  FINISHED: "FINISHED",
  CANCELLED: "CANCELLED",
} as const;

export type TournamentStatus =
  (typeof TOURNAMENT_STATUS)[keyof typeof TOURNAMENT_STATUS];

export interface Tournament {
  id: string;
  name: string;
  description: string;
  status: TournamentStatus;
  startDate: string;
  endDate: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface TournamentPage {
  content: Tournament[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface CreateTournamentInput {
  name: string;
  description?: string;
  startDate: string;
  endDate: string;
}

export interface UpdateTournamentInput {
  name?: string;
  description?: string;
  startDate?: string;
  endDate?: string;
}

// ── Group ─────────────────────────────────────────────────────────────────

export interface Group {
  id: string;
  tournamentId: string;
  name: string;
  displayOrder: number;
}

export interface CreateGroupInput {
  name: string;
  displayOrder?: number;
}

// ── Phase ─────────────────────────────────────────────────────────────────

export const PHASE_TYPE = {
  GROUP_STAGE: "GROUP_STAGE",
  ROUND_OF_16: "ROUND_OF_16",
  QUARTER_FINAL: "QUARTER_FINAL",
  SEMI_FINAL: "SEMI_FINAL",
  THIRD_PLACE: "THIRD_PLACE",
  FINAL: "FINAL",
} as const;

export type PhaseType = (typeof PHASE_TYPE)[keyof typeof PHASE_TYPE];

export interface Phase {
  id: string;
  tournamentId: string;
  name: string | null;
  type: PhaseType;
  displayOrder: number;
}

export interface CreatePhaseInput {
  type: PhaseType;
  name?: string;
  displayOrder?: number;
}

// ── Match ─────────────────────────────────────────────────────────────────

export interface TeamRef {
  id: string;
  name: string;
  shortName: string;
}

export interface Match {
  id: string;
  tournamentId: string;
  groupId: string | null;
  phaseId: string | null;
  homeTeam: TeamRef;
  awayTeam: TeamRef;
  startTime: string;
  status: MatchStatus;
  homeScore: number | null;
  awayScore: number | null;
  predictionsOpen: boolean;
}

export interface CreateMatchInput {
  groupId?: string;
  phaseId?: string;
  homeTeamId: string;
  awayTeamId: string;
  startTime: string;
}

export interface UpdateMatchInput {
  groupId?: string;
  phaseId?: string;
  homeTeamId?: string;
  awayTeamId?: string;
  startTime?: string;
  status?: MatchStatus;
}

export interface SetMatchResultInput {
  homeScore: number;
  awayScore: number;
}

// ── Team ──────────────────────────────────────────────────────────────────

export interface Team {
  id: string;
  name: string;
  shortName: string;
  country: string;
  logoUrl: string;
}

export interface CreateTeamInput {
  name: string;
  shortName: string;
  country: string;
  logoUrl?: string;
}

// ── Enrolment ─────────────────────────────────────────────────────────────

export interface EnrolmentStatus {
  userId: string;
  tournamentId: string;
  joinedAt: string;
}
