import { apiUrl } from "../config";
import { isUuid } from "../lib/uuid";
import {
  matchStatuses,
  type CompetitionView,
  type MatchView,
  type PageView,
  type PhaseView,
  type RoundView,
  type SeasonView,
  type TeamView,
} from "./types";

const invalidResponse = "Răspunsul API nu are formatul așteptat.";
const pageSize = 100;

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isCompetition(value: unknown): value is CompetitionView {
  return (
    record(value) &&
    isUuid(value.id) &&
    isUuid(value.countryId) &&
    typeof value.name === "string" &&
    typeof value.code === "string"
  );
}
function isSeason(value: unknown): value is SeasonView {
  return (
    record(value) &&
    isUuid(value.id) &&
    isUuid(value.competitionId) &&
    typeof value.name === "string" &&
    typeof value.startDate === "string" &&
    typeof value.endDate === "string"
  );
}
function isPhase(value: unknown): value is PhaseView {
  return (
    record(value) &&
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
    record(value) &&
    isUuid(value.id) &&
    isUuid(value.seasonId) &&
    isUuid(value.phaseId) &&
    typeof value.name === "string" &&
    Number.isInteger(value.number)
  );
}
function isTeam(value: unknown): value is TeamView {
  return (
    record(value) &&
    isUuid(value.id) &&
    isUuid(value.countryId) &&
    typeof value.name === "string" &&
    typeof value.shortName === "string"
  );
}
function isMatch(value: unknown): value is MatchView {
  return (
    record(value) &&
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

function readPage<T>(
  value: unknown,
  page: number,
  valid: (item: unknown) => item is T,
): PageView<T> {
  if (
    !record(value) ||
    !Array.isArray(value.items) ||
    !value.items.every(valid) ||
    value.page !== page ||
    typeof value.totalPages !== "number" ||
    !Number.isSafeInteger(value.totalPages) ||
    value.totalPages < 0 ||
    !Number.isInteger(value.size) ||
    !Number.isInteger(value.totalElements)
  ) {
    throw new Error(invalidResponse);
  }
  return value as unknown as PageView<T>;
}

async function list<T>(
  path: string,
  filters: Record<string, string>,
  valid: (item: unknown) => item is T,
  signal: AbortSignal,
): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0, totalPages = 1; page < totalPages; page++) {
    const query = new URLSearchParams({
      ...filters,
      page: String(page),
      size: String(pageSize),
    });
    const response = readPage(
      await getJson(`${path}?${query}`, signal),
      page,
      valid,
    );
    totalPages = response.totalPages;
    items.push(...response.items);
  }
  return items;
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
