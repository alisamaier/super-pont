import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import App from "./App";
import { competitions, footballResponse, page } from "./api/football.test-data";
import type { MatchStatus, MatchView } from "./api/types";

interface ProviderTeam { code: string; name: string; display_name: string; score?: number }
interface ProviderMatch { code: string; round: string; start_at: string; venue?: string; status_code: string; home_team: ProviderTeam; away_team: ProviderTeam }
const source: { data: ProviderMatch[] } = JSON.parse(readFileSync(resolve(process.cwd(), "../backend/src/test/resources/sportstats/superliga-real-response.json"), "utf8"));
const uid = (value: number) => `80000000-0000-4000-8000-${String(value).padStart(12, "0")}`;
const competition = { id: uid(1), countryId: uid(90), code: "SPORTSTATS-SUPERLIGA", name: "SuperLiga" };
const season = { id: uid(2), competitionId: competition.id, name: "2026/2027", startDate: "2026-07-01", endDate: "2027-06-30" };
const phase = { id: uid(3), seasonId: season.id, code: "dx2:REGULAR", name: "Sezon regulat", position: 100, format: "LEAGUE", winPoints: 3, drawPoints: 1, lossPoints: 0 };
const rounds = [16, 17].map(number => ({ id: uid(number), seasonId: season.id, phaseId: phase.id, name: `Etapa ${number}`, number }));
const teamCodes = [...new Set(source.data.flatMap(row => [row.home_team.code, row.away_team.code]))];
const team = (input: ProviderTeam) => ({ id: uid(100 + teamCodes.indexOf(input.code)), countryId: uid(90), name: input.display_name, shortName: input.display_name.slice(0, 50) });
const imported: MatchView[] = source.data.map((row, index) => ({ id: uid(1000 + index), seasonId: season.id,
  roundId: uid(Number(row.round.split(" ")[1])), homeTeam: team(row.home_team), awayTeam: team(row.away_team),
  kickoff: row.start_at, venue: row.venue ?? null, status: row.status_code === "final" ? "FINISHED" : "SCHEDULED",
  homeScore: row.home_team.score ?? null, awayScore: row.away_team.score ?? null }));
const future = imported[5];
const fetcher = vi.fn<typeof fetch>();
let saved: Record<string, { homeScore: number; awayScore: number }>;
let statusOverride: MatchStatus | undefined;

beforeEach(() => {
  window.history.replaceState(null, "", "/");
  vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-11-22T12:00:00Z"));
  saved = {}; statusOverride = undefined;
  fetcher.mockReset().mockImplementation(async (input, init) => {
    const url = new URL(String(input));
    if (url.pathname === "/api/users/me") return Response.json({ id: uid(999), displayName: "Fan", email: "fan@example.test", role: "USER" });
    if (url.pathname === "/api/auth/csrf") return Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf" });
    if (url.pathname === "/api/predictions/me") return Response.json(page(Object.entries(saved).map(([matchId, value]) => ({ matchId, ...value, result: null }))));
    if (url.pathname.endsWith("/prediction")) {
      expect(new Headers(init?.headers).get("X-CSRF-TOKEN")).toBe("csrf");
      const matchId = url.pathname.split("/")[3];
      saved[matchId] = JSON.parse(String(init?.body));
      return Response.json({ matchId, ...saved[matchId], result: null }, { status: 201 });
    }
    if (url.pathname === "/api/competitions") return Response.json(page([competition, ...competitions]));
    if (url.pathname === "/api/seasons" && url.searchParams.get("competitionId") === competition.id) return Response.json(page([season]));
    if (url.pathname === `/api/seasons/${season.id}/phases`) return Response.json([phase]);
    if (url.pathname === "/api/rounds" && url.searchParams.get("seasonId") === season.id) return Response.json(page(rounds));
    if (url.pathname === "/api/matches" && rounds.some(round => round.id === url.searchParams.get("roundId"))) {
      return Response.json(page(imported.filter(match => match.roundId === url.searchParams.get("roundId"))
        .map(match => match.id === future.id && statusOverride ? { ...match, status: statusOverride, homeScore: 3, awayScore: 3 } : match)));
    }
    return footballResponse(input);
  });
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("uses the imported current season, keeps round/banner/cards together and saves a future prediction with existing badges", async () => {
  render(<App />);
  const article = await screen.findByRole("article", { name: "UTA Arad – Univ. Craiova" });
  expect(document.querySelector(".league-label")?.textContent).toBe("SuperLiga");
  expect(document.querySelector(".round-banner h1")?.textContent).toBe("Etapa 16");
  expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "Etapă" }).value).toBe(rounds[0].id);
  expect(screen.queryByRole("combobox", { name: /sezon/i })).toBeNull();
  expect(article.querySelector(".match-meta")?.textContent).not.toContain("0:0");
  expect(within(screen.getByRole("article", { name: "FC Botoșani – FC Voluntari" })).getByText("3:3")).toBeTruthy();
  expect(article.querySelectorAll(".team-badge img")).toHaveLength(0);
  const localTime = new Intl.DateTimeFormat("ro-RO", { hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(new Date(future.kickoff));
  expect(article.querySelector(".match-meta")?.textContent).toContain(localTime);
  fireEvent.change(within(article).getByLabelText("Scor UTA Arad"), { target: { value: "2" } });
  fireEvent.change(within(article).getByLabelText("Scor Univ. Craiova"), { target: { value: "1" } });
  fireEvent.click(within(article).getByRole("button", { name: "Salvează pronosticul" }));
  await waitFor(() => expect(saved[future.id]).toEqual({ homeScore: 2, awayScore: 1 }));
  fireEvent.change(screen.getByRole("combobox", { name: "Etapă" }), { target: { value: rounds[1].id } });
  await screen.findByRole("article", { name: "U. Cluj – Sepsi Sf. Gheorghe" });
  expect(document.querySelector(".round-banner h1")?.textContent).toBe("Etapa 17");
  expect(screen.queryByRole("article", { name: "UTA Arad – Univ. Craiova" })).toBeNull();
  fireEvent.click(screen.getByRole("tab", { name: "Liga de test" }));
  await waitFor(() => expect(document.querySelector(".league-label")?.textContent).toBe("Liga de test"));
  expect(screen.queryByRole("article", { name: "U. Cluj – Sepsi Sf. Gheorghe" })).toBeNull();
  expect(fetcher.mock.calls.every(([url]) => !String(url).includes("sportstatsapi.com"))).toBe(true);
});

it("never displays a final score on scheduled, suspended or unconfirmed matches", async () => {
  for (const [status, label] of [["SCHEDULED", "De jucat"], ["SUSPENDED", "Întrerupt"], ["UNKNOWN", "Statut neconfirmat"]] as const) {
    statusOverride = status;
    const app = render(<App />);
    const article = await screen.findByRole("article", { name: "UTA Arad – Univ. Craiova" });
    expect(within(article).getByText(label)).toBeTruthy();
    expect(within(article).queryByText("3:3")).toBeNull();
    app.unmount();
  }
});
