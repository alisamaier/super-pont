import { useState } from "react";
import { footballApi } from "../api/football";
import type { SeasonView } from "../api/types";
import { toFixture } from "../lib/fixtures";
import { useResource } from "./useResource";

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
async function loadFixtures(selection: string, signal: AbortSignal) {
  const [seasonId, roundId] = selection.split("/");
  const matches = await footballApi.matches(roundId, signal);
  if (matches.some((match) => match.seasonId !== seasonId || match.roundId !== roundId)) {
    throw new Error("Meciurile primite nu aparțin etapei selectate. Încearcă din nou.");
  }
  return { roundId, fixtures: matches.map(toFixture) };
}

export function useFootballSchedule() {
  const [competitionId, setCompetitionId] = useState("");
  const [roundId, setRoundId] = useState("");
  const competitions = useResource("competitions", loadCompetitions);
  const competition =
    competitions.data?.find((item) => item.id === competitionId) ??
    competitions.data?.[0];
  const seasons = useResource(competition?.id, footballApi.seasons);
  const today = new Date(Date.now()).toISOString().slice(0, 10);
  const season = seasons.data?.reduce<SeasonView | undefined>((latest, item) => {
    if (item.startDate > today || item.endDate < today) return latest;
    return !latest || item.startDate > latest.startDate ? item : latest;
  }, undefined);
  const schedule = useResource(season?.id, loadSchedule);
  const rounds = schedule.data?.rounds;
  const selectedRoundId = rounds?.find((item) => item.id === roundId)?.id ?? rounds?.[0]?.id;
  const roundIndex = rounds?.findIndex((item) => item.id === selectedRoundId) ?? -1;
  const round = rounds?.[roundIndex];
  const loadedMatches = useResource(
    season && selectedRoundId ? `${season.id}/${selectedRoundId}` : undefined, loadFixtures,
  );
  const matches = {
    ...loadedMatches,
    data: loadedMatches.data?.roundId === selectedRoundId ? loadedMatches.data?.fixtures : undefined,
  };
  return {
    competitions,
    competition,
    seasons,
    season,
    schedule,
    round,
    rounds,
    selectedRoundId,
    previousRound: rounds?.[roundIndex - 1],
    nextRound: rounds?.[roundIndex + 1],
    matches,
    selectCompetition(id: string) {
      setCompetitionId(id);
      setRoundId("");
    },
    selectRound(id: string) {
      if (rounds?.some((item) => item.id === id)) setRoundId(id);
    },
  };
}
