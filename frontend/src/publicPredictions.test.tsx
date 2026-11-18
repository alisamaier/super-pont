import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import App from "./App";
import { UserPredictionsPage } from "./components/UserPredictionsPage";
import { footballResponse, page } from "./api/football.test-data";
import type { PublicPrediction, PublicUserPredictions } from "./api/publicPredictions";

const userId = "20000000-0000-0000-0000-000000000001";
const competitionId = "10000000-0000-0000-0000-000000000002";
const seasonId = "10000000-0000-0000-0000-000000000003";
const matchId = (value: number) => `20000000-0000-0000-0000-0000000000${value}`;
const base = {
  roundId: matchId(30),
  roundNumber: 17,
  roundName: "Etapa 17",
  phaseName: "Regular season",
  homeTeam: "FCSB",
  awayTeam: "Rapid",
  kickoff: "2026-11-02T18:00:00Z",
  status: "FINISHED" as const,
};
const hidden: PublicPrediction = {
  ...base,
  matchId: matchId(44),
  roundId: matchId(32),
  roundNumber: 18,
  roundName: "Etapa 18",
  homeTeam: "U Cluj",
  awayTeam: "Oțelul",
  kickoff: "2026-11-17T12:00:02Z",
  status: "SCHEDULED",
  visibility: "HIDDEN",
  result: null,
};
let data: PublicUserPredictions;
let publicStatus: number;
const fetcher = vi.fn<typeof fetch>();

beforeEach(() => {
  window.history.replaceState(null, "", "/");
  publicStatus = 200;
  data = {
    displayName: "Ana", competitionId, competitionName: "SuperLiga", seasonId,
    seasonName: "2026/2027", totalPoints: 18, serverTime: "2026-11-17T12:00:00Z",
    predictions: page([
      hidden,
      { ...base, matchId: matchId(40), visibility: "REVEALED", predictedScore: { home: 5, away: 0 },
        result: { homeScore: 5, awayScore: 0, points: 10 } },
      { ...base, matchId: matchId(41), homeTeam: "CFR", awayTeam: "Craiova", visibility: "REVEALED",
        predictedScore: { home: 4, away: 4 }, result: { homeScore: 5, awayScore: 0, points: 0 } },
      { ...base, matchId: matchId(42), homeTeam: "Dinamo", awayTeam: "Farul", status: "LIVE",
        visibility: "REVEALED", predictedScore: { home: 0, away: 0 }, result: null },
    ]),
  };
  fetcher.mockReset().mockImplementation(async (input) => {
    const url = new URL(String(input));
    if (url.pathname === "/api/users/me") return new Response(null, { status: 401 });
    if (url.pathname === "/api/leaderboard/superliga") {
      return Response.json({
        ...page([{ userId, rank: 1, displayName: "Ana", totalPoints: data.totalPoints }]),
        page: Number(url.searchParams.get("page") ?? 0),
        totalPages: 2,
      });
    }
    if (url.pathname === `/api/leaderboard/superliga/users/${userId}/predictions`) {
      return publicStatus === 200 ? Response.json(data) : new Response(null, { status: publicStatus });
    }
    return footballResponse(input);
  });
  vi.stubGlobal("fetch", fetcher);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

it("opens from the leaderboard, shows public scores and zero points, and returns to the same page", async () => {
  window.history.replaceState(null, "", "/clasament?page=1");
  render(<App />);
  fireEvent.click(await screen.findByRole("link", { name: "Ana" }));
  expect(window.location.pathname).toBe(`/clasament/utilizatori/${userId}`);
  expect(window.location.search).toBe("?page=1");
  expect(await screen.findByRole("heading", { name: "Pronosticuri · Ana" })).toBeTruthy();
  expect(screen.getByText("SuperLiga · 2026/2027")).toBeTruthy();
  const sections = screen.getAllByRole("region", { name: /Etapa 1[78] · Regular season/ });
  expect(sections.map((section) => section.getAttribute("aria-label"))).toEqual([
    "Etapa 18 · Regular season", "Etapa 17 · Regular season",
  ]);
  const future = within(screen.getByRole("article", { name: "U Cluj – Oțelul" }));
  expect(future.getByText("Disponibil după începerea meciului")).toBeTruthy();
  expect(future.queryByText(/Pronostic/)).toBeNull();
  expect(screen.getByText("0 puncte", { exact: true })).toBeTruthy();
  expect(screen.getByText("10 puncte", { exact: true })).toBeTruthy();
  const live = within(screen.getByRole("article", { name: "Dinamo – Farul" }));
  expect(live.getByText("0–0", { exact: true })).toBeTruthy();
  expect(live.getByText("În desfășurare")).toBeTruthy();
  expect(live.getByText("În așteptare")).toBeTruthy();

  const scored = data.predictions.items[1];
  if (scored.visibility !== "REVEALED") throw new Error("Expected evaluated prediction");
  scored.result = { homeScore: 4, awayScore: 0, points: 9 };
  data.totalPoints = 17;
  fireEvent(window, new Event("focus"));
  expect(await screen.findByText("Scor final 4–0")).toBeTruthy();
  expect(screen.getByText("9 puncte", { exact: true })).toBeTruthy();
  expect(screen.queryByText("10 puncte", { exact: true })).toBeNull();

  fireEvent.click(screen.getByRole("link", { name: "Înapoi la clasament" }));
  expect(window.location.pathname + window.location.search).toBe("/clasament?page=1");
  const table = await screen.findByRole("table", { name: "Clasament SuperLiga" });
  expect(within(table).getByRole("cell", { name: "17" })).toBeTruthy();
});

it("fetches again at the server deadline and reveals only the new API response", async () => {
  vi.useFakeTimers();
  data.predictions = page([hidden]);
  render(<UserPredictionsPage userId={userId} leaderboardPage={0} onNavigate={() => {}} />);
  await act(async () => { await vi.advanceTimersByTimeAsync(0); });
  expect(screen.getByText("Disponibil după începerea meciului")).toBeTruthy();
  data.predictions = page([{
    ...hidden, visibility: "REVEALED", predictedScore: { home: 3, away: 2 }, result: null,
  }]);
  data.serverTime = "2026-11-17T12:00:02.100Z";
  await act(async () => { await vi.advanceTimersByTimeAsync(2100); });
  expect(screen.getByText("3–2", { exact: true })).toBeTruthy();
  expect(screen.queryByText("Disponibil după începerea meciului")).toBeNull();
  expect(screen.getByText("În așteptare")).toBeTruthy();
});

it("shows an error and lets the user retry into the empty state", async () => {
  publicStatus = 503;
  render(<UserPredictionsPage userId={userId} leaderboardPage={0} onNavigate={() => {}} />);
  expect(screen.getByRole("status").textContent).toContain("Se încarcă");
  expect((await screen.findByRole("alert")).textContent).toContain("HTTP 503");
  data.predictions = page([]);
  publicStatus = 200;
  fireEvent.click(screen.getByRole("button", { name: "Încearcă din nou" }));
  expect(await screen.findByText("Niciun pronostic în acest sezon.")).toBeTruthy();
});
