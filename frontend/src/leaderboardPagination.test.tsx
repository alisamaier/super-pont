import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import App from "./App";
import { competitions, footballResponse, page, seasons } from "./api/football.test-data";
import { leaderboardPages } from "./lib/leaderboard";

const accountId = "30000000-0000-0000-0000-000000000016";
const rows = Array.from({ length: 45 }, (_, index) => ({
  userId: index === 20 ? accountId : `40000000-0000-0000-0000-${String(index + 1).padStart(12, "0")}`,
  displayName: `Participant ${index + 1}`,
  rank: index < 5 ? index + 1 : index === 44 ? 45 : 6,
  totalPoints: index < 5 ? 18 - index : index === 44 ? 0 : 1,
}));
const fetcher = vi.fn<typeof fetch>();
let count: number;
let failPage: number | undefined;
let intercept: ((url: URL, init?: RequestInit) => Promise<Response> | undefined) | undefined;

beforeEach(() => {
  window.history.replaceState(null, "", "/clasament");
  vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-11-19T12:00:00Z"));
  count = 45;
  failPage = undefined;
  intercept = undefined;
  fetcher.mockReset().mockImplementation(async (input, init) => {
    const url = new URL(String(input));
    const pending = intercept?.(url, init);
    if (pending) return pending;
    if (url.pathname === "/api/users/me") return Response.json({ id: accountId, displayName: "Participant 21", role: "USER" });
    if (url.pathname === "/api/predictions/me") return Response.json(page([]));
    if (url.pathname === "/api/leaderboard/superliga" || url.pathname === "/api/leaderboard") {
      expect(init?.credentials).toBe("include");
      expect(init?.cache).toBe("no-store");
      expect(url.searchParams.get("size")).toBe("20");
      const number = Number(url.searchParams.get("page"));
      if (number === failPage) return new Response(null, { status: 503 });
      return Response.json(page(rows.slice(0, count), number, 20));
    }
    if (/\/leaderboard\/(superliga\/)?users\/.+\/predictions$/.test(url.pathname)) {
      return Response.json({ displayName: "Participant 21", competitionId: competitions[1].id,
        competitionName: competitions[1].name, seasonId: seasons[2].id, seasonName: seasons[2].name,
        totalPoints: 1, serverTime: "2026-11-19T12:00:00Z", predictions: page([], 0, 20) });
    }
    return footballResponse(input);
  });
  vi.stubGlobal("fetch", fetcher);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

it("shows 20 entries, global ties, the last page, counts, and the signed-in highlight", async () => {
  render(<App />);
  const table = await screen.findByRole("table", { name: "Clasament SuperLiga" });
  expect(within(table).getAllByRole("row")).toHaveLength(21);
  expect(screen.getByText("1–20 din 45 de utilizatori")).toBeTruthy();
  expect(table.querySelector('[aria-current="true"]')).toBeNull();
  expect(screen.queryByRole("link", { name: "Înapoi" })).toBeNull();
  expect(within(table).getAllByRole("row")[20].textContent).toBe("6Participant 201");
  fireEvent.click(screen.getByRole("link", { name: "Înainte" }));
  const self = await screen.findByRole("link", { name: "Participant 21" });
  expect(self.closest("tr")?.getAttribute("aria-current")).toBe("true");
  expect(self.closest("tr")?.textContent).toBe("6Participant 21Tu1");
  expect(window.location.search).toBe("?page=1");
  expect(screen.getByText("21–40 din 45 de utilizatori")).toBeTruthy();
  fireEvent.click(screen.getByRole("link", { name: "Pagina 3" }));
  expect(await screen.findByText("41–45 din 45 de utilizatori")).toBeTruthy();
  expect(within(screen.getByRole("table")).getAllByRole("row")).toHaveLength(6);
  expect(screen.queryByRole("link", { name: "Înainte" })).toBeNull();
  expect(screen.getByRole("link", { name: "Pagina 3" }).getAttribute("aria-current")).toBe("page");
  fireEvent.click(screen.getByRole("link", { name: "Înapoi" }));
  expect(await screen.findByText("21–40 din 45 de utilizatori")).toBeTruthy();
});

it("restores the URL on refresh and returns from a user's predictions to the same page", async () => {
  window.history.replaceState(null, "", "/clasament?page=1");
  render(<App />);
  fireEvent.click(await screen.findByRole("link", { name: "Participant 21" }));
  expect(window.location.search).toBe("?page=1");
  await screen.findByRole("heading", { name: "Pronosticuri · Participant 21" });
  cleanup();
  render(<App />);
  fireEvent.click(await screen.findByRole("link", { name: "Înapoi la clasament" }));
  expect(await screen.findByText("21–40 din 45 de utilizatori")).toBeTruthy();
  expect(window.location.pathname + window.location.search).toBe("/clasament?page=1");
  cleanup();
  render(<App />);
  expect(await screen.findByText("21–40 din 45 de utilizatori")).toBeTruthy();
});

it("resets the page on competition change and preserves competition and season on return", async () => {
  window.history.replaceState(null, "", "/clasament?page=1");
  render(<App />);
  await screen.findByText("21–40 din 45 de utilizatori");
  fireEvent.change(screen.getByRole("combobox", { name: "Competiție clasament" }), { target: { value: competitions[1].id } });
  expect(await screen.findByRole("table", { name: `Clasament ${competitions[1].name}` })).toBeTruthy();
  expect(screen.getByText("1–20 din 45 de utilizatori")).toBeTruthy();
  expect(new URLSearchParams(window.location.search).has("page")).toBe(false);
  fireEvent.click(screen.getByRole("link", { name: "Pagina 2" }));
  fireEvent.click(await screen.findByRole("link", { name: "Participant 21" }));
  await screen.findByRole("heading", { name: "Pronosticuri · Participant 21" });
  expect(new URLSearchParams(window.location.search).get("competitionId")).toBe(competitions[1].id);
  expect(new URLSearchParams(window.location.search).get("seasonId")).toBe(seasons[2].id);
  fireEvent.click(screen.getByRole("link", { name: "Înapoi la clasament" }));
  expect(await screen.findByText("21–40 din 45 de utilizatori")).toBeTruthy();
  expect(screen.getByRole("table", { name: `Clasament ${competitions[1].name}` })).toBeTruthy();
});

it("redirects an unavailable page to the last page, including an empty leaderboard", async () => {
  window.history.replaceState(null, "", "/clasament?page=99");
  render(<App />);
  expect(await screen.findByText("41–45 din 45 de utilizatori")).toBeTruthy();
  expect(window.location.search).toBe("?page=2");
  count = 0;
  fireEvent(window, new Event("focus"));
  expect(await screen.findByText("Încă nu există un clasament pentru sezonul curent.")).toBeTruthy();
  expect(screen.getByText("0 din 0 de utilizatori")).toBeTruthy();
  expect(window.location.search).toBe("");
  expect(screen.queryByRole("navigation", { name: "Pagini de clasament" })).toBeNull();
});

it("shows a retryable error when switching pages", async () => {
  render(<App />);
  fireEvent.click(await screen.findByRole("link", { name: "Pagina 2" }));
  // Trigger a refresh of the selected page with a failed response.
  await screen.findByText("21–40 din 45 de utilizatori");
  failPage = 2;
  fireEvent.click(screen.getByRole("link", { name: "Pagina 3" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(screen.queryByRole("table")).toBeNull();
  failPage = undefined;
  fireEvent.click(screen.getByRole("button", { name: "Încearcă din nou" }));
  expect(await screen.findByText("41–45 din 45 de utilizatori")).toBeTruthy();
});

it("ignores a late response after another page is requested", async () => {
  let resolveOld!: (response: Response) => void;
  let oldSignal: AbortSignal | null | undefined;
  intercept = (url, init) => {
    if (url.pathname === "/api/leaderboard/superliga" && url.searchParams.get("page") === "1") {
      oldSignal = init?.signal;
      return new Promise<Response>((resolve) => { resolveOld = resolve; });
    }
  };
  render(<App />);
  fireEvent.click(await screen.findByRole("link", { name: "Pagina 2" }));
  expect(screen.getByText("Se încarcă clasamentul…")).toBeTruthy();
  act(() => {
    window.history.pushState(null, "", "/clasament?page=2");
    window.dispatchEvent(new PopStateEvent("popstate"));
  });
  expect(await screen.findByText("41–45 din 45 de utilizatori")).toBeTruthy();
  expect(oldSignal?.aborted).toBe(true);
  await act(async () => { resolveOld(Response.json(page(rows, 1, 20))); });
  expect(screen.getByText("41–45 din 45 de utilizatori")).toBeTruthy();
  expect(screen.queryByText("21–40 din 45 de utilizatori")).toBeNull();
});

it("uses a compact page range at both ends and in the middle", () => {
  expect(leaderboardPages(0, 3)).toEqual([0, 1, 2]);
  expect(leaderboardPages(0, 100)).toEqual([0, 1, 2, 3, 4, "end-gap", 99]);
  expect(leaderboardPages(49, 100)).toEqual([0, "start-gap", 48, 49, 50, "end-gap", 99]);
  expect(leaderboardPages(99, 100)).toEqual([0, "start-gap", 95, 96, 97, 98, 99]);
});
