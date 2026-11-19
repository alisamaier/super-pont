import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import App from "./App";
import { footballResponse, page } from "./api/football.test-data";

const account = { id: "20000000-0000-0000-0000-000000000002", displayName: "viewer", email: "viewer@example.test", role: "USER" };
const otherUser = "20000000-0000-0000-0000-000000000001";
const profilePath = `/clasament/utilizatori/${otherUser}`;
const fetcher = vi.fn<typeof fetch>();
let signedIn: boolean;
let restoring: Promise<Response> | undefined;
let protectedStatus: number;
let protectedWait: Promise<void> | undefined;

beforeEach(() => {
  signedIn = false;
  restoring = undefined;
  protectedStatus = 200;
  protectedWait = undefined;
  vi.spyOn(console, "info").mockImplementation(() => {});
  vi.spyOn(console, "error").mockImplementation(() => {});
  window.history.replaceState(null, "", "/");
  fetcher.mockReset().mockImplementation(async (input, init) => {
    const url = new URL(String(input));
    switch (url.pathname) {
      case "/api/users/me": return restoring ?? (signedIn ? Response.json(account) : new Response(null, { status: 401 }));
      case "/api/auth/csrf": return Response.json({ headerName: "X-CSRF-TOKEN", token: "test-csrf" });
      case "/api/auth/login": signedIn = true; return Response.json(account);
      case "/api/auth/logout": signedIn = false; return new Response(null, { status: 204 });
      case "/api/predictions/me": return Response.json(page([]));
      case "/api/leaderboard/superliga":
        expect(init?.credentials).toBe("include");
        expect(init?.cache).toBe("no-store");
        await protectedWait;
        if (protectedStatus !== 200) return new Response(null, { status: protectedStatus });
        return signedIn ? Response.json({
          items: [{ userId: otherUser, rank: 1, displayName: "Ana", totalPoints: 18 }],
          page: Number(url.searchParams.get("page")), size: 20, totalElements: 61, totalPages: 4,
        }) : new Response(null, { status: 401 });
      case `/api/leaderboard/superliga/users/${otherUser}/predictions`:
        expect(init?.credentials).toBe("include");
        expect(init?.cache).toBe("no-store");
        await protectedWait;
        if (protectedStatus !== 200) return new Response(null, { status: protectedStatus });
        return signedIn ? Response.json({
          displayName: "Ana", competitionId: "10000000-0000-0000-0000-000000000002", competitionName: "SuperLiga",
          seasonId: "10000000-0000-0000-0000-000000000003", seasonName: "2026/2027", totalPoints: 18,
          serverTime: "2026-11-19T12:00:00Z", predictions: page([]),
        }) : new Response(null, { status: 401 });
      default: return footballResponse(input);
    }
  });
  vi.stubGlobal("fetch", fetcher);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const protectedCalls = () => fetcher.mock.calls.filter(([url]) => new URL(String(url)).pathname.startsWith("/api/leaderboard/"));
const accountNav = () => within(screen.getByRole("navigation", { name: "Cont" }));
const mainNav = () => within(screen.getByRole("navigation", { name: "Navigare principală" }));

function submitLogin() {
  fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "test password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
}

it.each(["/", "/regulament"])("keeps %s public and hides the leaderboard navigation from visitors", async (path) => {
  window.history.replaceState(null, "", path);
  render(<App />);
  await accountNav().findByRole("link", { name: "Intră în cont" });
  expect(mainNav().queryByRole("link", { name: "Clasament" })).toBeNull();
  expect(mainNav().queryByRole("button", { name: /Pronosticurile mele/ })).toBeNull();
  expect(screen.getByRole("navigation", { name: "Navigare principală" }).querySelector(".nav-count")).toBeNull();
  expect(mainNav().getByRole("button", { name: "Meciuri" })).toBeTruthy();
  expect(mainNav().getByRole("link", { name: "Regulament" })).toBeTruthy();
  if (path === "/regulament") expect(screen.getByRole("heading", { name: "Regulament" })).toBeTruthy();
  else expect(await screen.findByRole("article", { name: "Nord – Sud" })).toBeTruthy();
  expect(protectedCalls()).toHaveLength(0);
});

it.each(["/clasament?page=2#clasament", `${profilePath}?page=1#user-predictions`])(
  "guards a direct visit to %s and restores the complete destination after login", async (destination) => {
    window.history.replaceState(null, "", destination);
    render(<App />);
    expect(screen.getByText("Se verifică sesiunea…")).toBeTruthy();
    expect(screen.queryByRole("heading", { name: /Clasament|Pronosticuri · Ana/ })).toBeNull();
    await screen.findByRole("heading", { name: "Autentificare" });
    expect(new URL(window.location.href).searchParams.get("next")).toBe(destination);
    expect(mainNav().queryByRole("link", { name: "Clasament" })).toBeNull();
    expect(protectedCalls()).toHaveLength(0);
    submitLogin();
    await screen.findByRole("heading", { name: destination.startsWith(profilePath) ? "Pronosticuri · Ana" : "Clasament" });
    expect(window.location.pathname + window.location.search + window.location.hash).toBe(destination);
    expect(mainNav().getByRole("link", { name: "Clasament" })).toBeTruthy();
    expect(protectedCalls().length).toBeGreaterThan(0);
  },
);

it.each(["/clasament", profilePath])("does not fetch or show protected data while restoring the session on %s", async (destination) => {
  signedIn = true;
  let restore!: (response: Response) => void;
  restoring = new Promise(resolve => { restore = resolve; });
  window.history.replaceState(null, "", destination);
  render(<App />);
  expect(screen.getByText("Se verifică sesiunea…")).toBeTruthy();
  expect(screen.queryByRole("heading", { name: /Clasament|Pronosticuri · Ana/ })).toBeNull();
  expect(mainNav().queryByRole("link", { name: "Clasament" })).toBeNull();
  expect(mainNav().queryByRole("button", { name: /Pronosticurile mele/ })).toBeNull();
  expect(screen.getByRole("navigation", { name: "Navigare principală" }).querySelector(".nav-count")).toBeNull();
  expect(protectedCalls()).toHaveLength(0);
  await act(async () => { restore(Response.json(account)); });
  await screen.findByRole("heading", { name: destination === profilePath ? "Pronosticuri · Ana" : "Clasament" });
  expect(window.location.pathname).toBe(destination);
});

it("blocks browser Back to both protected routes after logout without reusing or fetching their data", async () => {
  signedIn = true;
  render(<App />);
  fireEvent.click(await mainNav().findByRole("link", { name: "Clasament" }));
  fireEvent.click(await screen.findByRole("link", { name: "Ana" }));
  await screen.findByRole("heading", { name: "Pronosticuri · Ana" });
  fireEvent.click(accountNav().getByText(account.displayName).closest("summary")!);
  fireEvent.click(accountNav().getByRole("button", { name: "Deconectare" }));
  await accountNav().findByRole("link", { name: "Intră în cont" });
  expect(window.location.pathname).toBe("/");
  expect(mainNav().queryByRole("button", { name: /Pronosticurile mele/ })).toBeNull();
  expect(screen.getByRole("navigation", { name: "Navigare principală" }).querySelector(".nav-count")).toBeNull();
  const calls = protectedCalls().length;
  await act(async () => { window.history.back(); });
  await screen.findByRole("heading", { name: "Autentificare" });
  expect(new URL(window.location.href).searchParams.get("next")).toBe(profilePath);
  expect(screen.queryByRole("heading", { name: "Pronosticuri · Ana" })).toBeNull();
  await act(async () => { window.history.back(); });
  await waitFor(() => expect(new URL(window.location.href).searchParams.get("next")).toBe("/clasament"));
  expect(screen.queryByRole("heading", { name: "Clasament" })).toBeNull();
  expect(mainNav().queryByRole("link", { name: "Clasament" })).toBeNull();
  expect(protectedCalls()).toHaveLength(calls);
});

it.each(["https://untrusted.example/clasament", "//untrusted.example/clasament", "http://["])(
  "rejects an unsafe login return destination %s", async (next) => {
    window.history.replaceState(null, "", `/login?next=${encodeURIComponent(next)}`);
    render(<App />);
    await accountNav().findByRole("link", { name: "Intră în cont" });
    submitLogin();
    await accountNav().findByText(account.displayName);
    expect(window.location.pathname).toBe("/");
    expect(protectedCalls()).toHaveLength(0);
  },
);

it.each(["/clasament?page=2#clasament", `${profilePath}?page=1#user-predictions`])(
  "redirects an expired session on the first request to %s and returns there after login", async (destination) => {
    signedIn = true;
    protectedStatus = 401;
    window.history.replaceState(null, "", destination);
    render(<App />);
    await screen.findByRole("heading", { name: "Autentificare" });
    expect(new URL(window.location.href).searchParams.get("next")).toBe(destination);
    expect(mainNav().queryByRole("link", { name: "Clasament" })).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByRole("button", { name: "Încearcă din nou" })).toBeNull();
    protectedStatus = 200;
    submitLogin();
    await screen.findByRole("heading", { name: destination.startsWith(profilePath) ? "Pronosticuri · Ana" : "Clasament" });
    expect(window.location.pathname + window.location.search + window.location.hash).toBe(destination);
  },
);

it.each(["/clasament?page=1", `${profilePath}?page=2`])(
  "redirects to login if the session expires while viewing %s", async (destination) => {
    signedIn = true;
    window.history.replaceState(null, "", destination);
    render(<App />);
    await screen.findByRole("heading", { name: destination.startsWith(profilePath) ? "Pronosticuri · Ana" : "Clasament" });
    protectedStatus = 401;
    fireEvent(window, new Event("focus"));
    await screen.findByRole("heading", { name: "Autentificare" });
    expect(new URL(window.location.href).searchParams.get("next")).toBe(destination);
    expect(mainNav().queryByRole("link", { name: "Clasament" })).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
  },
);

it.each(["/clasament", profilePath])("checks the session on the next visible-page refresh for %s", async (destination) => {
  vi.useFakeTimers();
  signedIn = true;
  window.history.replaceState(null, "", destination);
  render(<App />);
  await act(async () => { await vi.advanceTimersByTimeAsync(0); });
  expect(screen.getByRole("heading", { name: destination === profilePath ? "Pronosticuri · Ana" : "Clasament" })).toBeTruthy();
  protectedStatus = 401;
  await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
  expect(screen.getByRole("heading", { name: "Autentificare" })).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});

it.each(["/clasament?page=1", profilePath])("keeps a temporary failure on %s compact and retryable with diagnostics outside the UI", async (destination) => {
  signedIn = true;
  protectedStatus = 503;
  window.history.replaceState(null, "", destination);
  render(<App />);
  const error = await screen.findByRole("alert");
  expect(error.className).toBe("load-error");
  expect(error.textContent).toContain(destination === profilePath ? "Pronosticurile nu sunt disponibile momentan." : "Clasamentul nu este disponibil momentan.");
  expect(error.textContent).not.toMatch(/HTTP|API|503|\/leaderboard/);
  expect(mainNav().getByRole("link", { name: "Clasament" })).toBeTruthy();
  expect(window.location.pathname + window.location.search).toBe(destination);
  expect(console.error).toHaveBeenCalledWith("[Super Pont] Cererea API a eșuat.", expect.objectContaining({
    path: expect.stringContaining("/leaderboard/superliga"), status: 503,
  }));
  protectedStatus = 200;
  fireEvent.click(within(error).getByRole("button", { name: "Încearcă din nou" }));
  await screen.findByRole("heading", { name: destination === profilePath ? "Pronosticuri · Ana" : "Clasament" });
  await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
});

it.each(["/clasament?page=1", profilePath])("restores a valid session after refreshing %s before requesting protected data", async (destination) => {
  signedIn = true;
  window.history.replaceState(null, "", destination);
  const app = render(<App />);
  await screen.findByRole("heading", { name: destination === profilePath ? "Pronosticuri · Ana" : "Clasament" });
  app.unmount();
  fetcher.mockClear();
  render(<App />);
  expect(screen.getByText("Se verifică sesiunea…")).toBeTruthy();
  expect(protectedCalls()).toHaveLength(0);
  await screen.findByRole("heading", { name: destination === profilePath ? "Pronosticuri · Ana" : "Clasament" });
  expect(protectedCalls().length).toBeGreaterThan(0);
});
