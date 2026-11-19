import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import App from "./App";
import {
  competitions,
  footballResponse,
  matches,
  page,
  rounds,
  seasons,
} from "./api/football.test-data";
import { toFixture } from "./lib/fixtures";
import type { Predictions, PredictionResult } from "./types";

const fixtures = matches.slice(0, 3).map(toFixture);
const fetcher = vi.fn<typeof fetch>();
const storageKey = "super-pont:predictions:api-v1";
const account = { id: "20000000-0000-0000-0000-000000000002", displayName: "fan", email: "fan@example.test", role: "USER" };
let serverPredictions: Predictions;
let serverResults: Record<string, PredictionResult>;
let signedIn: boolean;
let loadStatus: number;
let writeStatus: number;
const predictionCalls = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-11-01T18:00:00Z"));
  window.history.replaceState(null, "", "/");
  localStorage.clear();
  serverPredictions = {};
  serverResults = {};
  signedIn = true;
  loadStatus = 200;
  writeStatus = 200;
  fetcher
    .mockReset()
    .mockImplementation(async (input) => footballResponse(input));
  predictionCalls.mockReset().mockImplementation(async (input, init) => {
    const url = new URL(String(input));
    expect(init?.credentials).toBe("include");
    if (url.pathname === "/api/predictions/me") {
      if (loadStatus !== 200) return new Response(null, { status: loadStatus });
      return Response.json(page(Object.entries(serverPredictions).map(([matchId, score]) => {
        const result = serverResults[matchId];
        return {
          matchId,
          homeScore: score.home,
          awayScore: score.away,
          result: result ? { homeScore: result.home, awayScore: result.away, points: result.points } : null,
        };
      })));
    }
    expect(new Headers(init?.headers).get("X-CSRF-TOKEN")).toBe("test-csrf");
    if (writeStatus !== 200) return new Response(null, { status: writeStatus });
    const matchId = url.pathname.split("/")[3];
    if (init?.method === "DELETE") {
      delete serverPredictions[matchId];
      return new Response(null, { status: 204 });
    }
    const { homeScore, awayScore, ...rest } = JSON.parse(String(init?.body));
    expect(rest).toEqual({});
    serverPredictions[matchId] = { home: homeScore, away: awayScore };
    return Response.json({ matchId, homeScore, awayScore, result: null });
  });
  vi.stubGlobal("fetch", async (input: RequestInfo | URL, init?: RequestInit) => {
    const { pathname } = new URL(String(input));
    if (pathname === "/api/users/me")
      return signedIn ? Response.json(account) : new Response(null, { status: 401 });
    if (pathname === "/api/auth/csrf")
      return Response.json({ headerName: "X-CSRF-TOKEN", token: "test-csrf" });
    if (pathname === "/api/auth/logout") {
      signedIn = false;
      return new Response(null, { status: 204 });
    }
    if (pathname === "/api/predictions/me" || pathname.endsWith("/prediction"))
      return predictionCalls(input, init);
    return fetcher(input, init);
  });
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function card(index: number) {
  const fixture = fixtures[index];
  return within(
    screen.getByRole("article", {
      name: `${fixture.home.name} – ${fixture.away.name}`,
    }),
  );
}
function fill(index: number, home: string, away: string) {
  fireEvent.change(
    card(index).getByLabelText(`Scor ${fixtures[index].home.name}`),
    { target: { value: home } },
  );
  fireEvent.change(
    card(index).getByLabelText(`Scor ${fixtures[index].away.name}`),
    { target: { value: away } },
  );
}
function saved() {
  return serverPredictions;
}
async function report() {
  return within(await screen.findByRole("region", { name: "Ultima salvare" }));
}

describe("prediction validation in the application", () => {
  it("saves 0 and 99 individually, normalizes leading zeros, and reloads them", async () => {
    const view = await renderRound();
    fill(0, "00", "099");
    fireEvent.click(
      card(0).getByRole("button", { name: "Salvează pronosticul" }),
    );
    expect((await report()).getByText("Salvate (1)")).toBeTruthy();
    expect(saved()).toEqual({ [fixtures[0].id]: { home: 0, away: 99 } });
    expect(
      (
        card(0).getByLabelText(
          `Scor ${fixtures[0].away.name}`,
        ) as HTMLInputElement
      ).value,
    ).toBe("99");
    view.unmount();
    await renderRound();
    expect(
      (
        card(0).getByLabelText(
          `Scor ${fixtures[0].home.name}`,
        ) as HTMLInputElement
      ).value,
    ).toBe("0");
    expect(
      (
        card(0).getByLabelText(
          `Scor ${fixtures[0].away.name}`,
        ) as HTMLInputElement
      ).value,
    ).toBe("99");
  });

  it.each(["100", "-1", "1.5", "1.0", "1e1", "abc"])(
    "shows inline errors and rejects individual input %j",
    async (score) => {
      await renderRound();
      fill(0, score, "0");
      const input = card(0).getByLabelText(`Scor ${fixtures[0].home.name}`);
      expect(input.getAttribute("aria-invalid")).toBe("true");
      expect(
        document.getElementById(input.getAttribute("aria-describedby")!),
      ).toBeTruthy();
      fireEvent.click(
        card(0).getByRole("button", { name: "Salvează pronosticul" }),
      );
      const result = await report();
      expect(saved()).toEqual({});
      expect(result.getByText("Salvate (0)")).toBeTruthy();
      expect(result.getByText("Respinse (1)")).toBeTruthy();
      expect(
        result.getByText(
          `${fixtures[0].home.name} – ${fixtures[0].away.name}`,
        ),
      ).toBeTruthy();
    },
  );

  it("shows required errors for an empty individual submission", async () => {
    await renderRound();
    fireEvent.click(
      card(0).getByRole("button", { name: "Salvează pronosticul" }),
    );
    await report();
    expect(card(0).getByText("Gazde: Introdu un scor.")).toBeTruthy();
    expect(card(0).getByText("Oaspeți: Introdu un scor.")).toBeTruthy();
    expect(saved()).toEqual({});
  });

  it("bulk saves valid drafts, names rejected matches, and keeps rejected drafts editable", async () => {
    await renderRound();
    fill(0, "0", "99");
    fill(1, "100", "1");
    fill(2, "01", "");
    fireEvent.click(screen.getByRole("button", { name: /Salvează toate/ }));
    const result = await report();
    expect(saved()).toEqual({ [fixtures[0].id]: { home: 0, away: 99 } });
    expect(result.getByText("Salvate (1)")).toBeTruthy();
    expect(result.getByText("Respinse (2)")).toBeTruthy();
    for (const index of [0, 1, 2])
      expect(
        result.getByText(
          `${fixtures[index].home.name} – ${fixtures[index].away.name}`,
        ),
      ).toBeTruthy();
    expect(card(2).getByText("Oaspeți: Introdu un scor.")).toBeTruthy();
    expect(
      (
        card(1).getByLabelText(
          `Scor ${fixtures[1].home.name}`,
        ) as HTMLInputElement
      ).value,
    ).toBe("100");
    fill(1, "99", "01");
    fill(2, "01", "0");
    fireEvent.click(screen.getByRole("button", { name: /Salvează toate/ }));
    await result.findByText("Salvate (2)");
    expect(saved()).toEqual({
      [fixtures[0].id]: { home: 0, away: 99 },
      [fixtures[1].id]: { home: 99, away: 1 },
      [fixtures[2].id]: { home: 1, away: 0 },
    });
    expect(result.queryByText(/Respinse/)).toBeNull();
  });

  it("keeps bulk save available when every attempted draft is invalid", async () => {
    await renderRound();
    fill(0, "1e1", "0");
    expect(
      (
        screen.getByRole("button", {
          name: /Salvează toate/,
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: /Salvează toate/ }));
    const result = await report();
    expect(result.getByText("Salvate (0)")).toBeTruthy();
    expect(result.getByText("Respinse (1)")).toBeTruthy();
    expect(saved()).toEqual({});
  });

  it("preserves an existing saved score when its edited draft is rejected", async () => {
    serverPredictions[fixtures[0].id] = { home: 2, away: 1 };
    await renderRound();
    fill(0, "100", "1");
    fill(1, "0", "99");
    fireEvent.click(screen.getByRole("button", { name: /Salvează toate/ }));
    expect((await report()).getByText("Respinse (1)")).toBeTruthy();
    expect(saved()).toEqual({
      [fixtures[0].id]: { home: 2, away: 1 },
      [fixtures[1].id]: { home: 0, away: 99 },
    });
  });

  it("reports prediction load errors and lets the user retry", async () => {
    loadStatus = 503;
    serverPredictions[fixtures[0].id] = { home: 0, away: 99 };
    await renderRound();
    expect((await screen.findByRole("alert")).textContent).toContain("HTTP 503");
    expect((card(0).getByLabelText("Scor Nord") as HTMLInputElement).disabled).toBe(true);
    loadStatus = 200;
    fireEvent.click(screen.getByRole("button", { name: "Reîncarcă pronosticurile" }));
    await card(0).findByRole("button", { name: "Pronostic salvat" });
    expect((card(0).getByLabelText("Scor Sud") as HTMLInputElement).value).toBe("99");
  });

  it("reports failed writes alongside invalid drafts and keeps both available to retry", async () => {
    await renderRound();
    writeStatus = 503;
    fill(0, "99", "0");
    fill(1, "100", "1");
    fireEvent.click(screen.getByRole("button", { name: /Salvează toate/ }));
    const result = await report();
    expect(result.getByText("Salvate (0)")).toBeTruthy();
    expect(result.getByText("Respinse (2)")).toBeTruthy();
    expect(result.getByText(/HTTP 503/)).toBeTruthy();
    expect(saved()).toEqual({});
    expect((card(0).getByLabelText("Scor Nord") as HTMLInputElement).value).toBe("99");
  });

  it("updates and deletes a saved prediction only after API confirmation", async () => {
    serverPredictions[fixtures[0].id] = { home: 2, away: 1 };
    await renderRound();
    fill(0, "4", "3");
    fireEvent.click(card(0).getByRole("button", { name: "Actualizează pronosticul" }));
    await card(0).findByRole("button", { name: "Pronostic salvat" });
    expect(saved()[fixtures[0].id]).toEqual({ home: 4, away: 3 });
    const remove = card(0).getByRole("button", { name: "Șterge pronosticul Nord – Sud" });
    writeStatus = 503;
    fireEvent.click(remove);
    expect((await card(0).findByRole("alert")).textContent).toContain("HTTP 503");
    expect(saved()[fixtures[0].id]).toEqual({ home: 4, away: 3 });
    writeStatus = 200;
    fireEvent.click(remove);
    await screen.findByText("Pronosticul a fost șters.");
    expect(saved()).toEqual({});
    expect((card(0).getByLabelText("Scor Nord") as HTMLInputElement).value).toBe("");
  });

  it("keeps saved scores visible and disables editing and deletion at kickoff", async () => {
    vi.mocked(Date.now).mockReturnValue(Date.parse(fixtures[0].kickoff));
    serverPredictions[fixtures[0].id] = { home: 2, away: 1 };
    await renderRound();
    await card(0).findByRole("button", { name: "Pronostic salvat" });
    expect((card(0).getByLabelText("Scor Nord") as HTMLInputElement).disabled).toBe(true);
    expect((card(0).getByRole("button", { name: "Șterge pronosticul Nord – Sud" }) as HTMLButtonElement).disabled).toBe(true);
    expect((card(1).getByRole("button", { name: "Pronosticuri închise" }) as HTMLButtonElement).disabled).toBe(true);
    expect((card(2).getByLabelText("Scor Atlas") as HTMLInputElement).disabled).toBe(false);
  });

  it("clears account scores and drafts on sign-out and disables guest editing", async () => {
    serverPredictions[fixtures[0].id] = { home: 2, away: 1 };
    await renderRound();
    fill(1, "6", "");
    fireEvent.click(screen.getByText(account.displayName).closest("summary")!);
    fireEvent.click(screen.getByRole("button", { name: "Deconectare" }));
    await screen.findByText(/pentru a completa și salva pronosticuri/);
    for (const [index, name] of [[0, "Nord"], [1, "Est"]] as const) {
      const input = card(index).getByLabelText(`Scor ${name}`) as HTMLInputElement;
      expect(input.value).toBe("");
      expect(input.disabled).toBe(true);
    }
  });
});

async function renderRound() {
  const view = render(<App />);
  await screen.findByRole("article", { name: "Nord – Sud" });
  await waitFor(() => {
    expect(screen.queryByText("Se încarcă pronosticurile…")).toBeNull();
  });
  return view;
}

it("shows pending, final and zero-point predictions in the ticket and refreshes a corrected result", async () => {
  const [first, second, third] = fixtures;
  serverPredictions = {
    [first.id]: { home: 0, away: 0 },
    [second.id]: { home: 4, away: 4 },
    [third.id]: { home: 1, away: 1 },
  };
  serverResults = {
    [first.id]: { home: 0, away: 0, points: 10 },
    [second.id]: { home: 5, away: 0, points: 0 },
  };
  await renderRound();
  const ticket = within(screen.getByRole("complementary", { name: "Rezumatul pronosticurilor" }));
  expect(await ticket.findByText("Scor final 0–0")).toBeTruthy();
  expect(ticket.getByText("10 puncte", { exact: true })).toBeTruthy();
  expect(ticket.getByText("Scor final 5–0")).toBeTruthy();
  expect(ticket.getByText("0 puncte", { exact: true })).toBeTruthy();
  expect(ticket.getByText("În așteptare")).toBeTruthy();

  serverResults[first.id] = { home: 1, away: 1, points: 8 };
  fireEvent(window, new Event("focus"));
  expect(await ticket.findByText("Scor final 1–1")).toBeTruthy();
  expect(ticket.getByText("8 puncte", { exact: true })).toBeTruthy();
  expect(ticket.queryByText("10 puncte", { exact: true })).toBeNull();
});

describe("season leaderboard", () => {
  const rankings = [
    { rank: 1, userId: fixtures[0].id, displayName: "Ana", totalPoints: 18 },
    { rank: 2, userId: fixtures[1].id, displayName: "Mara", totalPoints: 13 },
    { rank: 2, userId: account.id, displayName: "Radu", totalPoints: 13 },
    { rank: 4, userId: fixtures[2].id, displayName: "Vlad", totalPoints: 0 },
  ];

  it("opens from navigation and highlights only the signed-in user's row", async () => {
    fetcher.mockImplementation(async (input) =>
      new URL(String(input)).pathname === "/api/leaderboard/superliga"
        ? Response.json(page(rankings, 0, 20))
        : footballResponse(input),
    );
    await renderRound();
    fireEvent.click(screen.getByRole("link", { name: "Clasament" }));
    expect(window.location.pathname).toBe("/clasament");
    const table = await screen.findByRole("table", { name: "Clasament SuperLiga" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows.map((row) => within(row).getAllByRole("cell")[0].textContent)).toEqual(["1", "2", "2", "4"]);
    expect(rows.map((row) => within(row).getAllByRole("cell")[1].textContent)).toEqual(["18", "13", "13", "0"]);
    expect(rows.filter((row) => row.getAttribute("aria-current") === "true")).toEqual([rows[2]]);
    expect(within(rows[2]).getByText("Tu")).toBeTruthy();
  });

  it("shows loading on a direct visit, then a retryable error and the empty state", async () => {
    window.history.replaceState(null, "", "/clasament");
    let failure = true;
    fetcher.mockImplementation(async (input) => {
      if (new URL(String(input)).pathname !== "/api/leaderboard/superliga") return footballResponse(input);
      return failure ? new Response(null, { status: 503 }) : Response.json(page([], 0, 20));
    });
    render(<App />);
    expect(screen.getByText("Se verifică sesiunea…")).toBeTruthy();
    expect((await screen.findByRole("alert")).textContent).toContain("Clasamentul nu este disponibil momentan.");
    expect(screen.getByRole("alert").textContent).not.toMatch(/HTTP|API|503/);
    failure = false;
    fireEvent.click(screen.getByRole("button", { name: "Încearcă din nou" }));
    expect(await screen.findByText("Încă nu există un clasament pentru sezonul curent.")).toBeTruthy();
  });
});

describe("rules navigation", () => {
  it.each([false, true])("opens the scoring deep link with signedIn=%s even when the schedule fails", async (authenticated) => {
    signedIn = authenticated;
    window.history.replaceState(null, "", "/regulament#punctaj");
    fetcher.mockResolvedValue(new Response(null, { status: 503 }));
    render(<App />);
    expect(screen.getByRole("heading", { name: "Regulament" })).toBeTruthy();
    expect(document.activeElement?.id).toBe("punctaj");
    expect(screen.getAllByRole("main")).toHaveLength(1);
    const navigation = within(screen.getByRole("navigation", { name: "Navigare principală" }));
    expect(navigation.getByRole("link", { name: "Regulament" }).getAttribute("aria-current")).toBe("page");
    expect(navigation.getByRole("button", { name: "Meciuri" }).getAttribute("aria-pressed")).toBe("false");
    if (authenticated) expect(await screen.findByText(account.displayName)).toBeTruthy();
    else expect(await screen.findByRole("link", { name: "Intră în cont" })).toBeTruthy();
    expect(within(screen.getByRole("table")).getAllByRole("row")).toHaveLength(8);
  });

  it("links from saved predictions to scoring and preserves drafts when returning", async () => {
    await renderRound();
    fill(0, "2", "1");
    fireEvent.click(screen.getByRole("button", { name: /Pronosticurile mele/ }));
    const help = screen.getByRole("link", { name: "Cum se calculează punctele?" });
    expect(help.getAttribute("href")).toBe("/regulament#punctaj");
    fireEvent.click(help);
    expect(window.location.pathname + window.location.hash).toBe("/regulament#punctaj");
    expect(document.activeElement?.id).toBe("punctaj");
    fireEvent.click(screen.getByRole("button", { name: "Meciuri" }));
    expect(card(0).getByRole<HTMLInputElement>("textbox", { name: "Scor Nord" }).value).toBe("2");
    expect(card(0).getByRole<HTMLInputElement>("textbox", { name: "Scor Sud" }).value).toBe("1");
    expect(window.location.hash).toBe("");
  });

  it("links from the leaderboard and handles back navigation and native hash changes", async () => {
    fetcher.mockImplementation(async (input) => new URL(String(input)).pathname === "/api/leaderboard/superliga"
      ? Response.json(page([])) : footballResponse(input));
    window.history.replaceState(null, "", "/clasament");
    render(<App />);
    fireEvent.click(await screen.findByRole("link", { name: "Cum se calculează punctele?" }));
    expect(document.activeElement?.id).toBe("punctaj");
    window.history.replaceState(null, "", "/clasament");
    fireEvent.popState(window);
    expect(screen.getByRole("heading", { name: "Clasament" })).toBeTruthy();
    fireEvent.click(screen.getByRole("link", { name: "Regulament" }));
    expect(document.activeElement?.id).toBe("regulament");
    window.history.replaceState(null, "", "/regulament#punctaj");
    fireEvent(window, new HashChangeEvent("hashchange"));
    expect(document.activeElement?.id).toBe("punctaj");
  });
});

describe("backend schedule integration", () => {
  it("shows loading while fetching the catalogue and renders API fixtures", async () => {
    fetcher.mockImplementation(async (input) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/seasons" && url.searchParams.get("competitionId") === competitions[0].id) {
        const futureSeason = {
          ...seasons[0],
          id: "20000000-0000-0000-0000-000000000009",
          startDate: "2027-07-01",
          endDate: "2028-06-30",
        };
        return Response.json(page([futureSeason, seasons[0], seasons[1]]));
      }
      return footballResponse(input);
    });
    render(<App />);
    expect(screen.getByText("Se încarcă competițiile…")).toBeTruthy();
    await screen.findByRole("article", { name: "Nord – Sud" });
    expect(screen.getAllByRole("article")).toHaveLength(3);
    expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "Etapă" }).value).toBe(rounds[0].id);
    expect(screen.getByRole("option", { name: "Etapa 16" })).toBeTruthy();
    expect(screen.getByRole("option", { name: "Etapa 17" })).toBeTruthy();
    expect(screen.queryByText(/Demo round 16|Demo clasament/)).toBeNull();
    expect(within(screen.getByRole("complementary", { name: "Rezumatul pronosticurilor" }))
      .getByText("Etapa 16", { exact: true })).toBeTruthy();
    expect(screen.queryByLabelText("Sezon")).toBeNull();
    expect(screen.getByRole<HTMLButtonElement>("button", { name: "Etapa anterioară" }).disabled).toBe(true);
    expect(screen.getByRole<HTMLButtonElement>("button", { name: "Etapa următoare" }).disabled).toBe(false);
  });

  it("reopens saved matchdays and preserves UUID-keyed predictions when switching competitions", async () => {
    serverPredictions[matches[5].id] = { home: 2, away: 2 };
    await renderRound();
    fill(0, "01", "0");
    fireEvent.click(
      card(0).getByRole("button", { name: "Salvează pronosticul" }),
    );
    await report();
    fill(1, "09", "");
    const previous = screen.getByRole<HTMLButtonElement>("button", { name: "Etapa anterioară" });
    const next = screen.getByRole<HTMLButtonElement>("button", { name: "Etapa următoare" });
    fireEvent.change(screen.getByRole("combobox", { name: "Etapă" }), { target: { value: rounds[1].id } });
    const final = within(
      await screen.findByRole("article", { name: "Finalista A – Finalista B" }),
    );
    expect(
      (final.getByLabelText("Scor Finalista A") as HTMLInputElement).value,
    ).toBe("");
    fireEvent.change(final.getByLabelText("Scor Finalista A"), {
      target: { value: "3" },
    });
    fireEvent.change(final.getByLabelText("Scor Finalista B"), {
      target: { value: "2" },
    });
    fireEvent.click(
      final.getByRole("button", { name: "Salvează pronosticul" }),
    );
    await final.findByRole("button", { name: "Pronostic salvat" });
    expect(next.disabled).toBe(true);
    expect(previous.disabled).toBe(false);
    await screen.findByRole("region", { name: "Etapa 17" });
    expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "Etapă" }).value).toBe(rounds[1].id);
    expect(within(screen.getByRole("complementary", { name: "Rezumatul pronosticurilor" }))
      .getByText("Etapa 17", { exact: true })).toBeTruthy();
    fireEvent.click(previous);
    await screen.findByRole("article", { name: "Nord – Sud" });
    expect(card(0).getByRole("button", { name: "Pronostic salvat" })).toBeTruthy();
    expect(previous.disabled).toBe(true);
    expect(next.disabled).toBe(false);
    fireEvent.click(next);
    const reopenedFinal = within(await screen.findByRole("article", { name: "Finalista A – Finalista B" }));
    expect(reopenedFinal.getByRole("button", { name: "Pronostic salvat" })).toBeTruthy();
    expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "Etapă" }).value).toBe(rounds[1].id);
    fireEvent.change(screen.getByRole("combobox", { name: "Etapă" }), { target: { value: rounds[0].id } });
    await screen.findByRole("article", { name: "Nord – Sud" });

    const league = screen.getByRole("tab", { name: competitions[0].name });
    const cup = screen.getByRole("tab", { name: competitions[1].name });
    league.focus();
    fireEvent.keyDown(league, { key: "ArrowRight" });
    expect(document.activeElement).toBe(cup);
    expect(league.getAttribute("aria-selected")).toBe("true");
    fireEvent.click(cup);
    await screen.findByRole("article", {
      name: "Echipa de cupă – Oaspeți de cupă",
    });
    const cupBanner = within(screen.getByRole("region", { name: "Etapa 3" }));
    expect(cupBanner.getByText("Cupa de test", { exact: true })).toBeTruthy();
    expect(cupBanner.getByText(/13 nov/)).toBeTruthy();
    expect(cup.getAttribute("aria-selected")).toBe("true");
    expect(league.getAttribute("aria-selected")).toBe("false");
    expect(previous.disabled).toBe(true);
    expect(next.disabled).toBe(true);
    expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "Etapă" }).value).toBe(rounds[3].id);
    expect(screen.queryByRole("article", { name: "Nord – Sud" })).toBeNull();
    const cupTicket = screen.getByRole("complementary", { name: "Rezumatul pronosticurilor" });
    expect(within(cupTicket).getByText("Cupa de test", { exact: true })).toBeTruthy();
    expect(cupTicket.textContent).not.toContain("Cupa 2026");
    expect(cupTicket.textContent).toContain("2:2");
    expect(cupTicket.textContent).not.toContain("1:0");

    fireEvent.click(league);
    await screen.findByRole("article", { name: "Nord – Sud" });
    const leagueBanner = within(screen.getByRole("region", { name: "Etapa 16" }));
    expect(leagueBanner.getByText("Liga de test", { exact: true })).toBeTruthy();
    expect(leagueBanner.queryByText(/2026\/27/)).toBeNull();
    expect(leagueBanner.getByText(/6 nov.*7 nov/)).toBeTruthy();
    expect(league.getAttribute("aria-selected")).toBe("true");
    expect(cup.getAttribute("aria-selected")).toBe("false");
    expect(previous.disabled).toBe(true);
    expect(next.disabled).toBe(false);
    expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "Etapă" }).value).toBe(rounds[0].id);
    const leagueTicket = screen.getByRole("complementary", { name: "Rezumatul pronosticurilor" });
    expect(within(leagueTicket).getByText("Liga de test", { exact: true })).toBeTruthy();
    expect(leagueTicket.textContent).toContain("1:0");
    expect(leagueTicket.textContent).not.toContain("2:2");
    expect(
      (card(0).getByLabelText("Scor Nord") as HTMLInputElement).value,
    ).toBe("1");
    expect((card(1).getByLabelText("Scor Est") as HTMLInputElement).value).toBe("09");
    expect(saved()).toEqual({
      [matches[0].id]: { home: 1, away: 0 },
      [matches[3].id]: { home: 3, away: 2 },
      [matches[5].id]: { home: 2, away: 2 },
    });
  });

  it("reports an API failure, then retries successfully", async () => {
    fetcher.mockResolvedValueOnce(new Response("Unavailable", { status: 503 }));
    render(<App />);
    expect((await screen.findByRole("alert")).textContent).toContain(
      "HTTP 503",
    );
    fireEvent.click(screen.getByRole("button", { name: "Reîncearcă" }));
    await screen.findByRole("article", { name: "Nord – Sud" });
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("shows an empty catalogue without rendering demo matches", async () => {
    fetcher.mockResolvedValueOnce(Response.json(page([])));
    render(<App />);
    expect(
      await screen.findByText("Nu sunt competiții disponibile."),
    ).toBeTruthy();
    expect(screen.queryAllByRole("article")).toHaveLength(0);
  });

  it("shows a lone competition in the banner without a tab row", async () => {
    fetcher.mockResolvedValueOnce(Response.json(page([competitions[0]])));
    await renderRound();
    const banner = within(screen.getByRole("region", { name: "Etapa 16" }));
    expect(banner.getByText("Liga de test")).toBeTruthy();
    expect(screen.queryByRole("tablist")).toBeNull();
  });

  it("keeps both controls disabled when the competition has no current season", async () => {
    vi.mocked(Date.now).mockReturnValue(Date.parse("2027-07-01T00:00:00Z"));
    render(<App />);
    await screen.findByText("Această competiție nu are un sezon în desfășurare.");
    expect(screen.getByRole<HTMLButtonElement>("button", { name: "Etapa anterioară" }).disabled).toBe(true);
    expect(screen.getByRole<HTMLButtonElement>("button", { name: "Etapa următoare" }).disabled).toBe(true);
    expect(screen.queryByRole("article")).toBeNull();
  });

  it("clears the previous competition while loading and ignores a late season response", async () => {
    await renderRound();
    let finish!: (response: Response) => void;
    let signal: AbortSignal | null | undefined;
    fetcher.mockImplementation((input, init) => {
      const url = new URL(String(input));
      if (
        url.pathname === "/api/seasons" &&
        url.searchParams.get("competitionId") === competitions[1].id
      ) {
        signal = init?.signal;
        return new Promise<Response>((resolve) => {
          finish = resolve;
        });
      }
      return Promise.resolve(footballResponse(input));
    });
    const cup = screen.getByRole("tab", { name: competitions[1].name });
    fireEvent.click(cup);
    expect(cup.getAttribute("aria-selected")).toBe("true");
    expect(screen.getByText("Se încarcă sezoanele…")).toBeTruthy();
    const banner = within(screen.getByRole("region", { name: "Programul meciurilor" }));
    expect(banner.getByText("Cupa de test")).toBeTruthy();
    expect(banner.queryByText(/Liga de test/)).toBeNull();
    expect(screen.queryAllByRole("article")).toHaveLength(0);
    expect(screen.queryByRole("complementary", { name: "Rezumatul pronosticurilor" })).toBeNull();

    fireEvent.click(screen.getByRole("tab", { name: competitions[0].name }));
    expect(signal?.aborted).toBe(true);
    await act(async () => finish(Response.json(page([seasons[2]]))));
    expect(await screen.findByRole("article", { name: "Nord – Sud" })).toBeTruthy();
    expect(screen.queryByRole("article", { name: "Echipa de cupă – Oaspeți de cupă" })).toBeNull();
    expect(screen.getByRole("region", { name: "Etapa 16" })).toBeTruthy();
  });

  it("ignores an outdated match response after selecting another round", async () => {
    await renderRound();
    let finish!: (response: Response) => void;
    let signal: AbortSignal | null | undefined;
    fetcher.mockImplementation((input, init) => {
      const url = new URL(String(input));
      if (
        url.pathname === "/api/matches" &&
        url.searchParams.get("roundId") === rounds[1].id
      ) {
        signal = init?.signal;
        return new Promise<Response>((resolve) => {
          finish = resolve;
        });
      }
      return Promise.resolve(footballResponse(input));
    });
    fireEvent.click(screen.getByRole("button", { name: "Etapa următoare" }));
    expect(screen.queryAllByRole("article")).toHaveLength(0);
    expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "Etapă" }).value).toBe(rounds[1].id);
    expect(screen.getByRole("region", { name: "Etapa 17" }).getAttribute("aria-busy")).toBe("true");
    expect(screen.getByText("Se încarcă meciurile…")).toBeTruthy();
    expect(screen.queryByRole("complementary", { name: "Rezumatul pronosticurilor" })).toBeNull();
    expect(screen.queryByLabelText("Filtrează după zi")).toBeNull();
    expect(document.querySelector(".round-dates")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Etapa anterioară" }));
    expect(signal?.aborted).toBe(true);
    await act(async () => {
      finish(Response.json(page([matches[3]])));
    });
    expect(
      await screen.findByRole("article", { name: "Nord – Sud" }),
    ).toBeTruthy();
    expect(
      screen.queryByRole("article", { name: "Finalista A – Finalista B" }),
    ).toBeNull();
  });

  it("ignores an earlier response for the same matchday after rapid 16–17–16–17 navigation", async () => {
    await renderRound();
    const requests: { signal: AbortSignal | null | undefined; finish: (response: Response) => void }[] = [];
    fetcher.mockImplementation((input, init) => {
      const url = new URL(String(input));
      if (url.pathname === "/api/matches" && url.searchParams.get("roundId") === rounds[1].id) {
        return new Promise<Response>((finish) => requests.push({ signal: init?.signal, finish }));
      }
      return Promise.resolve(footballResponse(input));
    });
    const selector = screen.getByRole<HTMLSelectElement>("combobox", { name: "Etapă" });
    fireEvent.change(selector, { target: { value: rounds[1].id } });
    fireEvent.change(selector, { target: { value: rounds[0].id } });
    fireEvent.click(screen.getByRole("button", { name: "Etapa următoare" }));
    expect(requests).toHaveLength(2);
    expect(requests[0].signal?.aborted).toBe(true);
    await act(async () => requests[0].finish(Response.json(page([matches[3]]))));
    expect(selector.value).toBe(rounds[1].id);
    expect(screen.getByText("Se încarcă meciurile…")).toBeTruthy();
    expect(screen.queryAllByRole("article")).toHaveLength(0);
    await act(async () => requests[1].finish(Response.json(page([matches[3]]))));
    await screen.findByRole("article", { name: "Finalista A – Finalista B" });
    expect(screen.getAllByRole("article")).toHaveLength(1);
    expect(screen.getByRole("region", { name: "Etapa 17" }).getAttribute("aria-busy")).toBe("false");
    expect(within(screen.getByRole("complementary", { name: "Rezumatul pronosticurilor" }))
      .getByText("Etapa 17", { exact: true })).toBeTruthy();
    expect(screen.queryByLabelText("Filtrează după zi")).toBeNull();
  });

  it("ignores and preserves legacy predictions, including UUID-keyed browser saves", async () => {
    const legacyKey = "super-pont:predictions:demo-2026-round-16";
    const legacy = JSON.stringify({ m1: { home: 8, away: 7 } });
    localStorage.setItem(legacyKey, legacy);
    const oldScores = JSON.stringify({ [fixtures[0].id]: { home: 8, away: 7 } });
    localStorage.setItem(storageKey, oldScores);
    await renderRound();
    expect(
      (card(0).getByLabelText("Scor Nord") as HTMLInputElement).value,
    ).toBe("");
    fill(0, "1", "0");
    fireEvent.click(
      card(0).getByRole("button", { name: "Salvează pronosticul" }),
    );
    await report();
    expect(localStorage.getItem(legacyKey)).toBe(legacy);
    expect(localStorage.getItem(storageKey)).toBe(oldScores);
    expect(saved()).toEqual({ [matches[0].id]: { home: 1, away: 0 } });
  });
});
