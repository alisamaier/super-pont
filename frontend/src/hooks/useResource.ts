import { useCallback, useEffect, useState } from "react";

export function useResource<T>(
  key: string | undefined,
  load: (key: string, signal: AbortSignal) => Promise<T>,
) {
  const [attempt, setAttempt] = useState(0);
  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  const [result, setResult] = useState<{
    key: string;
    attempt: number;
    data?: T;
    error?: string;
  }>();

  useEffect(() => {
    if (!key) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      controller.abort();
      setResult({
        key,
        attempt,
        error: "API-ul nu a răspuns la timp. Încearcă din nou.",
      });
    }, 60_000);
    void load(key, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult({ key, attempt, data });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setResult({
            key,
            attempt,
            error:
              error instanceof Error
                ? error.message
                : "Datele nu au putut fi încărcate.",
          });
      })
      .finally(() => window.clearTimeout(timeout));
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [key, attempt, load]);

  const current =
    result?.key === key && result?.attempt === attempt ? result : undefined;
  return {
    data: current?.data,
    error: current?.error,
    loading: !!key && !current,
    retry,
  };
}
