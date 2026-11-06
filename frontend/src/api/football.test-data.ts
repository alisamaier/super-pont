import type {
  CompetitionView,
  MatchView,
  PageView,
  PhaseView,
  RoundView,
  SeasonView,
  TeamView,
} from "./types";

function id(value: number) {
  return `00000000-0000-4000-8000-${String(value).padStart(12, "0")}`;
}
export const competitions: CompetitionView[] = [
  { id: id(1), countryId: id(90), code: "LEAGUE", name: "Liga de test" },
  { id: id(2), countryId: id(91), code: "CUP", name: "Cupa de test" },
];
export const seasons: SeasonView[] = [
  {
    id: id(10),
    competitionId: id(1),
    name: "2026/27",
    startDate: "2026-07-01",
    endDate: "2027-06-30",
  },
  {
    id: id(11),
    competitionId: id(1),
    name: "2025/26",
    startDate: "2025-07-01",
    endDate: "2026-06-30",
  },
  {
    id: id(12),
    competitionId: id(2),
    name: "Cupa 2026",
    startDate: "2026-07-01",
    endDate: "2027-06-30",
  },
];
const phases: PhaseView[] = [
  {
    id: id(20),
    seasonId: id(10),
    code: "GROUP",
    name: "Grupă",
    format: "LEAGUE",
    position: 1,
    winPoints: 3,
    drawPoints: 1,
    lossPoints: 0,
  },
  {
    id: id(21),
    seasonId: id(10),
    code: "FINAL",
    name: "Finale",
    format: "KNOCKOUT",
    position: 2,
    winPoints: 0,
    drawPoints: 0,
    lossPoints: 0,
  },
  {
    id: id(22),
    seasonId: id(11),
    code: "ARCHIVE",
    name: "Sezon trecut",
    format: "LEAGUE",
    position: 1,
    winPoints: 3,
    drawPoints: 1,
    lossPoints: 0,
  },
  {
    id: id(23),
    seasonId: id(12),
    code: "CUP",
    name: "Turul cupei",
    format: "KNOCKOUT",
    position: 1,
    winPoints: 0,
    drawPoints: 0,
    lossPoints: 0,
  },
];
export const rounds: RoundView[] = phases.map((phase, index) => ({
  id: id(30 + index),
  seasonId: phase.seasonId,
  phaseId: phase.id,
  number: 1,
  name: "Etapa 1",
}));

function team(value: number, name: string, shortName: string): TeamView {
  return { id: id(value), countryId: id(90), name, shortName };
}
function match(
  value: number,
  round: RoundView,
  home: string,
  away: string,
): MatchView {
  return {
    id: id(value),
    roundId: round.id,
    seasonId: round.seasonId,
    homeTeam: team(value + 100, home, home.slice(0, 3)),
    awayTeam: team(value + 200, away, away.slice(0, 3)),
    kickoff: `2026-11-${value === 42 ? "07" : "06"}T18:00:00Z`,
    venue: "Arena de test",
    status: "SCHEDULED",
    homeScore: null,
    awayScore: null,
  };
}
export const matches = [
  match(40, rounds[0], "Nord", "Sud"),
  match(41, rounds[0], "Est", "Vest"),
  match(42, rounds[0], "Atlas", "Vulturii"),
  match(43, rounds[1], "Finalista A", "Finalista B"),
  match(44, rounds[2], "Echipa din arhivă", "Oaspeți din arhivă"),
  match(45, rounds[3], "Echipa de cupă", "Oaspeți de cupă"),
];

export function page<T>(items: T[], pageNumber = 0, size = 100): PageView<T> {
  return {
    items: items.slice(pageNumber * size, (pageNumber + 1) * size),
    page: pageNumber,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  };
}

export function footballResponse(input: RequestInfo | URL): Response {
  const url = new URL(String(input));
  const query = url.searchParams;
  const paged = <T>(items: T[]) =>
    Response.json(
      page(
        items,
        Number(query.get("page") ?? 0),
        Number(query.get("size") ?? 100),
      ),
    );
  if (url.pathname === "/api/competitions") return paged(competitions);
  if (url.pathname === "/api/seasons")
    return paged(
      seasons.filter(
        (season) => season.competitionId === query.get("competitionId"),
      ),
    );
  if (url.pathname === "/api/rounds")
    return paged(
      rounds.filter((round) => round.seasonId === query.get("seasonId")),
    );
  if (url.pathname === "/api/matches")
    return paged(
      matches.filter((match) => match.roundId === query.get("roundId")),
    );
  const phasePath = /^\/api\/seasons\/([^/]+)\/phases$/.exec(url.pathname);
  if (phasePath)
    return Response.json(
      phases.filter((phase) => phase.seasonId === phasePath[1]),
    );
  return Response.json({}, { status: 404 });
}
