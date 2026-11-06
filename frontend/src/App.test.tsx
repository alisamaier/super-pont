import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import App from "./App";
import { STORAGE_KEY } from "./hooks/usePredictions";
import {
  competitions,
  footballResponse,
  matches,
  page,
  rounds,
  seasons,
} from "./api/football.test-data";
import { toFixture } from "./lib/fixtures";

const fixtures = matches.slice(0, 3).map(toFixture);
const fetcher = vi.fn<typeof fetch>();

beforeEach(() => {
  localStorage.clear();
  fetcher
    .mockReset()
    .mockImplementation(async (input) => footballResponse(input));
  vi.stubGlobal("fetch", fetcher);
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
  return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}");
}
function report() {
  return within(screen.getByRole("region", { name: "Ultima salvare" }));
}

describe("prediction validation in the application", () => {
  it("saves 0 and 99 individually, normalizes leading zeros, and reloads them", async () => {
    const view = await renderRound();
    fill(0, "00", "099");
    fireEvent.click(
      card(0).getByRole("button", { name: "Salvează pronosticul" }),
    );
    expect(saved()).toEqual({ [fixtures[0].id]: { home: 0, away: 99 } });
    expect(
      (
        card(0).getByLabelText(
          `Scor ${fixtures[0].away.name}`,
        ) as HTMLInputElement
      ).value,
    ).toBe("99");
    expect(report().getByText("Salvate (1)")).toBeTruthy();
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
      expect(saved()).toEqual({});
      expect(report().getByText("Salvate (0)")).toBeTruthy();
      expect(report().getByText("Respinse (1)")).toBeTruthy();
      expect(
        report().getByText(
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
    expect(saved()).toEqual({ [fixtures[0].id]: { home: 0, away: 99 } });
    expect(report().getByText("Salvate (1)")).toBeTruthy();
    expect(report().getByText("Respinse (2)")).toBeTruthy();
    for (const index of [0, 1, 2])
      expect(
        report().getByText(
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
    expect(saved()).toEqual({
      [fixtures[0].id]: { home: 0, away: 99 },
      [fixtures[1].id]: { home: 99, away: 1 },
      [fixtures[2].id]: { home: 1, away: 0 },
    });
    expect(report().getByText("Salvate (2)")).toBeTruthy();
    expect(report().queryByText(/Respinse/)).toBeNull();
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
    expect(report().getByText("Salvate (0)")).toBeTruthy();
    expect(report().getByText("Respinse (1)")).toBeTruthy();
    expect(saved()).toEqual({});
  });

  it("preserves an existing saved score when its edited draft is rejected", async () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ [fixtures[0].id]: { home: 2, away: 1 } }),
    );
    await renderRound();
    fill(0, "100", "1");
    fill(1, "0", "99");
    fireEvent.click(screen.getByRole("button", { name: /Salvează toate/ }));
    expect(saved()).toEqual({
      [fixtures[0].id]: { home: 2, away: 1 },
      [fixtures[1].id]: { home: 0, away: 99 },
    });
    expect(report().getByText("Respinse (1)")).toBeTruthy();
  });

  it("validates and normalizes stored scores, reporting invalid matches on load", async () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        [fixtures[0].id]: { home: "00", away: "099" },
        [fixtures[1].id]: { home: 100, away: 1 },
        [fixtures[2].id]: { home: "1e1", away: 0 },
      }),
    );
    await renderRound();
    expect(
      (
        card(0).getByLabelText(
          `Scor ${fixtures[0].away.name}`,
        ) as HTMLInputElement
      ).value,
    ).toBe("99");
    expect(
      (
        card(1).getByLabelText(
          `Scor ${fixtures[1].home.name}`,
        ) as HTMLInputElement
      ).value,
    ).toBe("");
    const alert = screen.getByRole("alert");
    expect(alert.textContent).toContain("Pronosticuri respinse la încărcare");
    expect(alert.textContent).toContain(fixtures[1].home.shortName);
    expect(alert.textContent).toContain(fixtures[2].home.shortName);
  });

  it("does not report successful saves when localStorage fails", async () => {
    await renderRound();
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("Storage full");
    });
    fill(0, "99", "0");
    fill(1, "100", "1");
    fireEvent.click(screen.getByRole("button", { name: /Salvează toate/ }));
    expect(report().getByText("Salvate (0)")).toBeTruthy();
    expect(report().getByText("Respinse (2)")).toBeTruthy();
    expect(saved()).toEqual({});
  });
});

async function renderRound() {
  const view = render(<App />);
  await screen.findByRole("article", { name: "Nord – Sud" });
  return view;
}

describe("backend schedule integration", () => {
  it("shows loading while fetching the catalogue and renders API fixtures", async () => {
    render(<App />);
    expect(screen.getByRole("status").textContent).toContain(
      "Se încarcă competițiile",
    );
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

  it("leaves old demo predictions untouched and never associates them with UUID fixtures", async () => {
    const legacyKey = "super-pont:predictions:demo-2026-round-16";
    const legacy = JSON.stringify({ m1: { home: 8, away: 7 } });
    localStorage.setItem(legacyKey, legacy);
    await renderRound();
    expect(
      (card(0).getByLabelText("Scor Nord") as HTMLInputElement).value,
    ).toBe("");
    fill(0, "1", "0");
    fireEvent.click(
      card(0).getByRole("button", { name: "Salvează pronosticul" }),
    );
    expect(localStorage.getItem(legacyKey)).toBe(legacy);
    expect(saved()).toEqual({ [matches[0].id]: { home: 1, away: 0 } });
  });
});
