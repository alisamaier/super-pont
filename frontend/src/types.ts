import type { MatchStatus } from "./api/types";

export interface Team {
  id: string;
  name: string;
  shortName: string;
  initials: string;
  color: string;
  accent: string;
  logoUrl?: string | null;
  logoCredit?: string | null;
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
export interface PredictionResult extends Prediction {
  points: number;
}
export interface SavedPrediction extends Prediction {
  result: PredictionResult | null;
}
export interface ScoreDraft {
  home: string;
  away: string;
}
export type Predictions = Record<string, Prediction>;
export type SavedPredictions = Record<string, SavedPrediction>;
