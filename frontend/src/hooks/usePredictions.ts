import { useEffect, useRef, useState } from "react";
import { csrfHeaders } from "../api/auth";
import { predictionApi } from "../api/predictions";
import { validateEntries } from "../lib/predictions";
import type { Fixture, Predictions, ScoreDraft } from "../types";

const lockedMessage = "Meciul a început. Pronosticul este blocat.";

export function predictionError(error: unknown) {
  if (error instanceof Error && !["AbortError", "TimeoutError", "TypeError"].includes(error.name)) {
    return error.message;
  }
  return "Sincronizarea pronosticurilor a eșuat. Încearcă din nou.";
}

export function usePredictions(userId: string | undefined, fixtures: readonly Fixture[]) {
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<{
    userId: string;
    attempt: number;
    signal: AbortSignal;
    data?: Predictions;
    error?: string;
  }>();
  const inFlight = useRef<AbortSignal | null>(null);
  const [pending, setPending] = useState<AbortSignal | null>(null);
  const [clock, setClock] = useState(Date.now);
  const now = Date.now();
  const kickoffs = new Map(fixtures.map((fixture) => [fixture.id, Date.parse(fixture.kickoff)]));
  const closed = new Set<string>();
  let nextKickoff = Infinity;
  for (const [id, kickoff] of kickoffs) {
    if (kickoff <= now) closed.add(id);
    else nextKickoff = Math.min(nextKickoff, kickoff);
  }

  useEffect(() => {
    if (!Number.isFinite(nextKickoff)) return;
    const timer = window.setTimeout(
      () => setClock(Date.now()),
      Math.min(2_147_483_647, Math.max(0, nextKickoff - Math.max(clock, Date.now()))),
    );
    return () => window.clearTimeout(timer);
  }, [clock, nextKickoff]);

  useEffect(() => {
    if (!userId) return;
    const controller = new AbortController();
    const { signal } = controller;
    const scope = { userId, attempt, signal };
    void predictionApi.list(AbortSignal.any([signal, AbortSignal.timeout(30_000)]))
      .then((data) => {
        if (!signal.aborted) setLoaded({ ...scope, data });
      })
      .catch((error: unknown) => {
        if (!signal.aborted) setLoaded({ ...scope, error: predictionError(error) });
      });
    return () => controller.abort();
  }, [userId, attempt]);

  const current = loaded && loaded.userId === userId && loaded.attempt === attempt && !loaded.signal.aborted
    ? loaded
    : undefined;
  const saved = Object.fromEntries(
    Object.entries(current?.data ?? {}).filter(([id]) => kickoffs.has(id)),
  );

  async function write<T>(action: (signal: AbortSignal) => Promise<T>) {
    const scope = current?.signal;
    if (!current?.data || !scope || scope.aborted || inFlight.current === scope) return;
    inFlight.current = scope;
    setPending(scope);
    try {
      const result = await action(AbortSignal.any([scope, AbortSignal.timeout(30_000)]));
      if (!scope.aborted) return result;
    } catch (error) {
      if (!scope.aborted) throw error;
    } finally {
      if (inFlight.current === scope) {
        inFlight.current = null;
        setPending(null);
      }
    }
  }

  function updateSaved(update: (data: Predictions) => Predictions) {
    setLoaded((previous) =>
      previous?.data && previous.signal === current?.signal
        ? { ...previous, data: update(previous.data) }
        : previous,
    );
  }

  function save(entries: Record<string, ScoreDraft>) {
    return write(async (signal) => {
      const result = validateEntries(entries, new Set(kickoffs.keys()));
      const valid = Object.entries(result.saved);
      result.saved = {};
      let headers: Promise<Headers> | undefined;
      for (const [id, score] of valid) {
        if (kickoffs.get(id)! <= Date.now()) {
          result.rejected[id] = lockedMessage;
          continue;
        }
        try {
          signal.throwIfAborted();
          headers ??= csrfHeaders(signal);
          result.saved[id] = await predictionApi.save(id, score, await headers, signal);
        } catch (error) {
          result.rejected[id] = predictionError(error);
        }
      }
      updateSaved((data) => ({ ...data, ...result.saved }));
      return result;
    });
  }

  function remove(id: string) {
    return write(async (signal) => {
      if (!kickoffs.has(id)) return false;
      if (kickoffs.get(id)! <= Date.now()) throw new Error(lockedMessage);
      await predictionApi.remove(id, signal);
      updateSaved((data) => {
        const next = { ...data };
        delete next[id];
        return next;
      });
      return true;
    });
  }

  return {
    saved,
    save,
    remove,
    closed,
    ready: !!current?.data,
    busy: pending !== null && pending === current?.signal,
    loading: !!userId && !current,
    loadError: current?.error,
    retry: () => setAttempt((value) => value + 1),
  };
}
