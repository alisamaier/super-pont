export interface PageView<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CompetitionView {
  id: string;
  countryId: string;
  code: string;
  name: string;
}

export interface SeasonView {
  id: string;
  competitionId: string;
  name: string;
  startDate: string;
  endDate: string;
}

export interface PhaseView {
  id: string;
  seasonId: string;
  code: string;
  name: string;
  format: "LEAGUE" | "KNOCKOUT";
  position: number;
  winPoints: number;
  drawPoints: number;
  lossPoints: number;
}

export interface RoundView {
  id: string;
  seasonId: string;
  phaseId: string;
  number: number;
  name: string;
}

export interface TeamView {
  id: string;
  countryId: string;
  name: string;
  shortName: string;
}

export const matchStatuses = [
  "SCHEDULED",
  "LIVE",
  "FINISHED",
  "POSTPONED",
  "CANCELLED",
] as const;
export type MatchStatus = (typeof matchStatuses)[number];

export interface MatchView {
  id: string;
  seasonId: string;
  roundId: string;
  homeTeam: TeamView;
  awayTeam: TeamView;
  kickoff: string;
  venue: string | null;
  status: MatchStatus;
  homeScore: number | null;
  awayScore: number | null;
}
