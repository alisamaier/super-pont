import type { Prediction, Predictions, ScoreDraft } from "../types";
import { isUuid } from "./uuid";

export const SCORE_MIN = 0;
export const SCORE_MAX = 99;
export const SCORE_REQUIRED = "Introdu un scor.";
export const SCORE_FORMAT =
  "Folosește doar cifre (0–99), fără semn, zecimale sau exponenți.";
export const SCORE_RANGE = "Scorul trebuie să fie între 0 și 99.";

export interface SaveResult {
  saved: Predictions;
  rejected: Record<string, string>;
}

export function validateScore(value: unknown): {
  value: number | null;
  error: string | null;
} {
  if (value === "" || value === undefined || value === null)
    return { value: null, error: SCORE_REQUIRED };
  if (typeof value === "string" && !/^[0-9]+$/.test(value))
    return { value: null, error: SCORE_FORMAT };
  if (typeof value !== "string" && typeof value !== "number")
    return { value: null, error: SCORE_FORMAT };
  const score = Number(value);
  if (!Number.isInteger(score) || Object.is(score, -0))
    return { value: null, error: SCORE_FORMAT };
  if (score < SCORE_MIN || score > SCORE_MAX)
    return { value: null, error: SCORE_RANGE };
  return { value: score, error: null };
}

export function validatePrediction(value: unknown) {
  const candidate =
    value && typeof value === "object" && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : {};
  const home = validateScore(candidate.home);
  const away = validateScore(candidate.away);
  const prediction: Prediction | null =
    home.error || away.error ? null : { home: home.value!, away: away.value! };
  return { prediction, errors: { home: home.error, away: away.error } };
}

export function validationMessage(errors: {
  home: string | null;
  away: string | null;
}) {
  return [
    errors.home && `Gazde: ${errors.home}`,
    errors.away && `Oaspeți: ${errors.away}`,
  ]
    .filter(Boolean)
    .join(" ");
}

// Saves and storage loading use the same normalization and rejection rules.
export function validateEntries(
  entries: Record<string, unknown>,
  fixtureIds: ReadonlySet<string>,
): SaveResult {
  const result: SaveResult = { saved: {}, rejected: {} };
  for (const [id, draft] of Object.entries(entries)) {
    if (!fixtureIds.has(id)) {
      result.rejected[id] = "Meci necunoscut.";
      continue;
    }
    const { prediction, errors } = validatePrediction(draft);
    if (prediction) result.saved[id] = prediction;
    else result.rejected[id] = validationMessage(errors);
  }
  return result;
}

export function readStoredPredictions(
  raw: string | null,
  fixtureIds?: ReadonlySet<string>,
): SaveResult {
  if (raw === null) return { saved: {}, rejected: {} };
  const value: unknown = JSON.parse(raw);
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new Error("Format de stocare invalid.");
  return validateEntries(
    value as Record<string, unknown>,
    fixtureIds ?? new Set(Object.keys(value).filter(isUuid)),
  );
}

export function pendingDrafts(
  drafts: Record<string, ScoreDraft>,
  saved: Predictions,
) {
  return Object.fromEntries(
    Object.entries(drafts).filter(([id, draft]) => {
      if (!saved[id] && draft.home === "" && draft.away === "") return false;
      const { prediction } = validatePrediction(draft);
      return (
        !prediction ||
        !saved[id] ||
        prediction.home !== saved[id].home ||
        prediction.away !== saved[id].away
      );
    }),
  );
}
