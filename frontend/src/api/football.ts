import { apiUrl } from "../config";
import { isUuid } from "../lib/uuid";
import { invalidResponse, isRecord, readPages } from "./page";
import {
  matchStatuses,
  type CompetitionView,
  type MatchView,
  type PhaseView,
  type RoundView,
  type SeasonView,
  type TeamView,
} from "./types";

function isCompetition(value: unknown): value is CompetitionView {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    isUuid(value.countryId) &&
    typeof value.name === "string" &&
    typeof value.code === "string"
  );
}
function isSeason(value: unknown): value is SeasonView {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    isUuid(value.competitionId) &&
    typeof value.name === "string" &&
    typeof value.startDate === "string" &&
    typeof value.endDate === "string"
  );
}
function isPhase(value: unknown): value is PhaseView {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    isUuid(value.seasonId) &&
    typeof value.name === "string" &&
    typeof value.code === "string" &&
    (value.format === "LEAGUE" || value.format === "KNOCKOUT") &&
    [value.position, value.winPoints, value.drawPoints, value.lossPoints].every(
      Number.isInteger,
    )
  );
}
function isRound(value: unknown): value is RoundView {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    isUuid(value.seasonId) &&
    isUuid(value.phaseId) &&
    typeof value.name === "string" &&
    Number.isInteger(value.number)
  );
}
function isTeam(value: unknown): value is TeamView {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    isUuid(value.countryId) &&
    typeof value.name === "string" &&
    typeof value.shortName === "string"
  );
}
function isMatch(value: unknown): value is MatchView {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    isUuid(value.seasonId) &&
    isUuid(value.roundId) &&
    isTeam(value.homeTeam) &&
    isTeam(value.awayTeam) &&
    typeof value.kickoff === "string" &&
    Number.isFinite(Date.parse(value.kickoff)) &&
    (value.venue === null || typeof value.venue === "string") &&
    typeof value.status === "string" &&
    (matchStatuses as readonly string[]).includes(value.status) &&
    [value.homeScore, value.awayScore].every(
      (score) => score === null || Number.isInteger(score),
    )
  );
}

async function getJson(path: string, signal: AbortSignal): Promise<unknown> {
  let response: Response;
  try {
    response = await fetch(`${apiUrl}/api${path}`, {
      signal,
      headers: { Accept: "application/json" },
    });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new Error("Nu ne-am putut conecta la API. Încearcă din nou.");
  }
  if (!response.ok)
    throw new Error(
      `API-ul nu a putut încărca datele (HTTP ${response.status}).`,
    );
  try {
    return await response.json();
  } catch {
    throw new Error(invalidResponse);
  }
}

function list<T>(
  path: string,
  filters: Record<string, string>,
  valid: (item: unknown) => item is T,
  signal: AbortSignal,
): Promise<T[]> {
  return readPages(filters, (query) => getJson(`${path}?${query}`, signal), valid);
}

export const footballApi = {
  competitions: (signal: AbortSignal) =>
    list("/competitions", {}, isCompetition, signal),
  seasons: (competitionId: string, signal: AbortSignal) =>
    list("/seasons", { competitionId }, isSeason, signal),
  rounds: (seasonId: string, signal: AbortSignal) =>
    list("/rounds", { seasonId }, isRound, signal),
  async phases(seasonId: string, signal: AbortSignal): Promise<PhaseView[]> {
    const value = await getJson(
      `/seasons/${encodeURIComponent(seasonId)}/phases`,
      signal,
    );
    if (!Array.isArray(value) || !value.every(isPhase))
      throw new Error(invalidResponse);
    return value;
  },
  matches: (roundId: string, signal: AbortSignal) =>
    list("/matches", { roundId }, isMatch, signal),
};
