import { useEffect, useState } from "react";
import { footballApi } from "../api/football";
import { toFixture } from "../lib/fixtures";

function useResource<T>(
  key: string | undefined,
  load: (key: string, signal: AbortSignal) => Promise<T>,
) {
  const [attempt, setAttempt] = useState(0);
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
    retry: () => setAttempt((value) => value + 1),
  };
}

const loadCompetitions = (_key: string, signal: AbortSignal) =>
  footballApi.competitions(signal);
async function loadSchedule(seasonId: string, signal: AbortSignal) {
  const [rounds, phases] = await Promise.all([
    footballApi.rounds(seasonId, signal),
    footballApi.phases(seasonId, signal),
  ]);
  const positions = new Map(phases.map((phase) => [phase.id, phase.position]));
  rounds.sort(
    (a, b) =>
      (positions.get(a.phaseId) ?? 0) - (positions.get(b.phaseId) ?? 0) ||
      a.number - b.number,
  );
  return { rounds, phases };
}
async function loadFixtures(roundId: string, signal: AbortSignal) {
  const matches = await footballApi.matches(roundId, signal);
  return matches.map(toFixture);
}

export function useFootballSchedule() {
  const [competitionId, setCompetitionId] = useState("");
  const [seasonId, setSeasonId] = useState("");
  const [roundId, setRoundId] = useState("");
  const competitions = useResource("competitions", loadCompetitions);
  const competition =
    competitions.data?.find((item) => item.id === competitionId) ??
    competitions.data?.[0];
  const seasons = useResource(competition?.id, footballApi.seasons);
  const season =
    seasons.data?.find((item) => item.id === seasonId) ??
    seasons.data?.reduce(
      (latest, item) => (item.startDate > latest.startDate ? item : latest),
      seasons.data[0],
    );
  const schedule = useResource(season?.id, loadSchedule);
  const round =
    schedule.data?.rounds.find((item) => item.id === roundId) ??
    schedule.data?.rounds[0];
  const matches = useResource(round?.id, loadFixtures);
  return {
    competitions,
    competition,
    seasons,
    season,
    schedule,
    round,
    matches,
    phase: schedule.data?.phases.find((item) => item.id === round?.phaseId),
    selectCompetition(id: string) {
      setCompetitionId(id);
      setSeasonId("");
      setRoundId("");
    },
    selectSeason(id: string) {
      setSeasonId(id);
      setRoundId("");
    },
    selectRound: setRoundId,
  };
}
