import { isUuid } from "../lib/uuid";
import { getJson } from "./football";
import { invalidResponse, isRecord, readPage } from "./page";
import { matchStatuses, type MatchStatus, type PageView } from "./types";

interface PublicMatch {
  matchId: string;
  roundId: string;
  roundNumber: number;
  roundName: string;
  phaseName: string;
  homeTeam: string;
  awayTeam: string;
  kickoff: string;
  status: MatchStatus;
}

interface EvaluatedResult {
  homeScore: number;
  awayScore: number;
  points: number;
}

export type PublicPrediction = PublicMatch & (
  | { visibility: "HIDDEN"; result: null; predictedScore?: never }
  | { visibility: "REVEALED"; predictedScore: { home: number; away: number }; result: EvaluatedResult | null }
);

export interface PublicUserPredictions {
  displayName: string;
  competitionId: string;
  competitionName: string;
  seasonId: string;
  seasonName: string;
  totalPoints: number;
  serverTime: string;
  predictions: PageView<PublicPrediction>;
}

function integer(value: unknown, max = Number.MAX_SAFE_INTEGER): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 && value <= max;
}

function timestamp(value: unknown): value is string {
  return typeof value === "string" && Number.isFinite(Date.parse(value));
}

function isPublicPrediction(value: unknown): value is PublicPrediction {
  if (
    !isRecord(value) || !isUuid(value.matchId) || !isUuid(value.roundId) ||
    !integer(value.roundNumber) || value.roundNumber < 1 ||
    typeof value.roundName !== "string" || typeof value.phaseName !== "string" ||
    typeof value.homeTeam !== "string" || typeof value.awayTeam !== "string" ||
    !timestamp(value.kickoff) || !matchStatuses.includes(value.status as MatchStatus)
  ) return false;
  if (value.visibility === "HIDDEN") return !("predictedScore" in value) && value.result === null;
  if (
    value.visibility !== "REVEALED" || !isRecord(value.predictedScore) ||
    !integer(value.predictedScore.home, 99) || !integer(value.predictedScore.away, 99)
  ) return false;
  return value.result === null || (
    value.status === "FINISHED" && isRecord(value.result) &&
    integer(value.result.homeScore) && integer(value.result.awayScore) && integer(value.result.points, 10)
  );
}

export async function getPublicPredictions(userId: string, page: number, signal: AbortSignal): Promise<PublicUserPredictions> {
  const data = await getJson(
    `/leaderboard/superliga/users/${encodeURIComponent(userId)}/predictions?page=${page}&size=20`, signal,
  );
  if (
    !isRecord(data) || typeof data.displayName !== "string" ||
    !isUuid(data.competitionId) || typeof data.competitionName !== "string" ||
    !isUuid(data.seasonId) || typeof data.seasonName !== "string" ||
    !integer(data.totalPoints) || !timestamp(data.serverTime)
  ) throw new Error(invalidResponse);
  return {
    displayName: data.displayName,
    competitionId: data.competitionId,
    competitionName: data.competitionName,
    seasonId: data.seasonId,
    seasonName: data.seasonName,
    totalPoints: data.totalPoints,
    serverTime: data.serverTime,
    predictions: readPage(data.predictions, page, isPublicPrediction),
  };
}
