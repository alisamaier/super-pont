import type { MatchStatus } from "./api/types";

export interface Team {
  id: string;
  name: string;
  shortName: string;
  initials: string;
  color: string;
  accent: string;
}

export interface Fixture {
  id: string;
  kickoff: string;
  home: Team;
  away: Team;
  day: string;
  date: string;
  time: string;
  venue: string;
  status: MatchStatus;
  homeScore: number | null;
  awayScore: number | null;
}

export interface Prediction {
  home: number;
  away: number;
}
export interface ScoreDraft {
  home: string;
  away: string;
}
export type Predictions = Record<string, Prediction>;
