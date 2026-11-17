import { validateScore } from "../lib/predictions";
import { isUuid } from "../lib/uuid";
import type { Prediction, Predictions } from "../types";
import { csrfHeaders, sessionFetch } from "./auth";
import { invalidResponse, isRecord, readPages } from "./page";

interface PredictionView {
  matchId: string;
  homeScore: number;
  awayScore: number;
}

function isPrediction(value: unknown): value is PredictionView {
  return (
    isRecord(value) &&
    isUuid(value.matchId) &&
    [value.homeScore, value.awayScore].every(
      (score) => typeof score === "number" && !validateScore(score).error,
    )
  );
}

function toPrediction(value: PredictionView): Prediction {
  return { home: value.homeScore, away: value.awayScore };
}

async function request(path: string, init: RequestInit) {
  const response = await sessionFetch(path, init);
  if (response.ok) return response;
  switch (response.status) {
    case 400:
      throw new Error("Scorurile trebuie să fie numere întregi între 0 și 99.");
    case 401:
    case 403:
      throw new Error("Sesiunea a expirat. Intră din nou în cont.");
    case 404:
      throw new Error("Meciul nu mai este disponibil.");
    case 409: {
      const problem = await response.json().catch(() => null);
      throw new Error(
        problem?.detail === "Predictions are locked at kickoff"
          ? "Meciul a început. Pronosticul este blocat."
          : "Pronosticul nu a putut fi modificat. Încearcă din nou.",
      );
    }
    default:
      throw new Error(`Pronosticurile nu au putut fi sincronizate (HTTP ${response.status}).`);
  }
}

async function readJson(response: Response): Promise<unknown> {
  return response.json().catch(() => {
    throw new Error(invalidResponse);
  });
}

export const predictionApi = {
  async list(signal: AbortSignal): Promise<Predictions> {
    const items = await readPages(
      {},
      async (query) =>
        readJson(await request(`/predictions/me?${query}`, { signal })),
      isPrediction,
    );
    return Object.fromEntries(items.map((item) => [item.matchId, toPrediction(item)]));
  },
  async save(id: string, score: Prediction, csrf: Headers, signal: AbortSignal) {
    const headers = new Headers(csrf);
    headers.set("Content-Type", "application/json");
    const response = await request(`/matches/${id}/prediction`, {
      method: "PUT",
      headers,
      signal,
      body: JSON.stringify({ homeScore: score.home, awayScore: score.away }),
    });
    const item = await readJson(response);
    if (!isPrediction(item) || item.matchId !== id) throw new Error(invalidResponse);
    return toPrediction(item);
  },
  async remove(id: string, signal: AbortSignal) {
    await request(`/matches/${id}/prediction`, {
      method: "DELETE",
      headers: await csrfHeaders(signal),
      signal,
    });
  },
};
