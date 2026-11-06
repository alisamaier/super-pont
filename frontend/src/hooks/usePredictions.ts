import { useState } from "react";
import type { Fixture, Predictions, ScoreDraft } from "../types";
import {
  readStoredPredictions,
  validateEntries,
  type SaveResult,
} from "../lib/predictions";

export const STORAGE_KEY = "super-pont:predictions:api-v1";

export function usePredictions(fixtures: readonly Fixture[]) {
  const fixtureIds = new Set(fixtures.map((fixture) => fixture.id));
  const [initial] = useState(() => {
    try {
      return {
        ...readStoredPredictions(localStorage.getItem(STORAGE_KEY)),
        error: "",
      };
    } catch {
      return {
        saved: {} as Predictions,
        rejected: {} as Record<string, string>,
        error:
          "Pronosticurile salvate nu au putut fi citite pe acest dispozitiv.",
      };
    }
  });
  const [allSaved, setAllSaved] = useState(initial.saved);
  const [rejected, setRejected] = useState(initial.rejected);
  const [storageError, setStorageError] = useState(initial.error);
  const saved = Object.fromEntries(
    Object.entries(allSaved).filter(([id]) => fixtureIds.has(id)),
  );
  const issues = fixtures.flatMap((fixture) =>
    rejected[fixture.id]
      ? [
          `${fixture.home.shortName} – ${fixture.away.shortName}: ${rejected[fixture.id]}`,
        ]
      : [],
  );
  const error =
    storageError ||
    (issues.length
      ? `Pronosticuri respinse la încărcare: ${issues.join("; ")}`
      : "");

  function persist(next: Predictions): boolean {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      setAllSaved(next);
      setRejected({});
      setStorageError("");
      return true;
    } catch {
      setStorageError(
        "Salvarea nu a reușit. Verifică dacă browserul permite stocarea locală și încearcă din nou.",
      );
      return false;
    }
  }

  function save(entries: Record<string, ScoreDraft>): SaveResult {
    const result = validateEntries(entries, fixtureIds);
    if (
      Object.keys(result.saved).length &&
      !persist({ ...allSaved, ...result.saved })
    ) {
      for (const id of Object.keys(result.saved))
        result.rejected[id] =
          "Salvarea pe acest dispozitiv nu a reușit. Încearcă din nou.";
      result.saved = {};
    }
    return result;
  }

  function remove(id: string) {
    if (!fixtureIds.has(id)) return false;
    const next = { ...allSaved };
    delete next[id];
    return persist(next);
  }

  return { saved, save, remove, error };
}
