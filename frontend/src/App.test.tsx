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
import type { Predictions } from "./types";

const fixtures = matches.slice(0, 3).map(toFixture);
const fetcher = vi.fn<typeof fetch>();
const storageKey = "super-pont:predictions:api-v1";
const account = { id: "fan-id", username: "fan", email: "fan@example.test", role: "USER" };
let serverPredictions: Predictions;
let signedIn: boolean;
let loadStatus: number;
let writeStatus: number;
const predictionCalls = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-11-01T18:00:00Z"));
  window.history.replaceState(null, "", "/");
  localStorage.clear();
  serverPredictions = {};
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
      return Response.json(page(Object.entries(serverPredictions).map(([matchId, score]) => ({
        matchId, homeScore: score.home, awayScore: score.away,
      }))));
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
    return Response.json({ matchId, homeScore, awayScore });
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

describe("backend schedule integration", () => {
  it("shows loading while fetching the catalogue and renders API fixtures", async () => {
    render(<App />);
    expect(screen.getByText("Se încarcă competițiile…")).toBeTruthy();
    await screen.findByRole("article", { name: "Nord – Sud" });
    expect(screen.getAllByRole("article")).toHaveLength(3);
    expect((screen.getByLabelText("Sezon") as HTMLSelectElement).value).toBe(
      seasons[0].id,
    );
    expect(
      screen.getByRole("option", { name: "Finale · Etapa 1" }),
    ).toBeTruthy();
  });

  it("preserves UUID-keyed predictions through round, season and competition changes", async () => {
    await renderRound();
    fill(0, "01", "0");
    fireEvent.click(
      card(0).getByRole("button", { name: "Salvează pronosticul" }),
    );
    await report();
    fill(1, "09", "");
    fireEvent.change(screen.getByLabelText("Etapă"), {
      target: { value: rounds[1].id },
    });
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
    fireEvent.change(screen.getByLabelText("Sezon"), {
      target: { value: seasons[1].id },
    });
    await screen.findByRole("article", {
      name: "Echipa din arhivă – Oaspeți din arhivă",
    });
    fireEvent.change(screen.getByLabelText("Competiție"), {
      target: { value: competitions[1].id },
    });
    await screen.findByRole("article", {
      name: "Echipa de cupă – Oaspeți de cupă",
    });
    fireEvent.change(screen.getByLabelText("Competiție"), {
      target: { value: competitions[0].id },
    });
    await screen.findByRole("article", { name: "Nord – Sud" });
    expect(
      (card(0).getByLabelText("Scor Nord") as HTMLInputElement).value,
    ).toBe("1");
    expect((card(1).getByLabelText("Scor Est") as HTMLInputElement).value).toBe("09");
    expect(saved()).toEqual({
      [matches[0].id]: { home: 1, away: 0 },
      [matches[3].id]: { home: 3, away: 2 },
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
    fireEvent.change(screen.getByLabelText("Etapă"), {
      target: { value: rounds[1].id },
    });
    expect(screen.queryAllByRole("article")).toHaveLength(0);
    fireEvent.change(screen.getByLabelText("Etapă"), {
      target: { value: rounds[0].id },
    });
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
