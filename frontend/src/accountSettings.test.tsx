import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import App from "./App";
import { footballResponse, page } from "./api/football.test-data";

const account = { id: "20000000-0000-0000-0000-000000000002", displayName: "supporter", email: "fan@example.test", role: "USER" };
const fetcher = vi.fn<typeof fetch>();
let signedIn: boolean;
let preference: boolean;
let readStatus: number;
let writeStatus: number;
let waitForSave: Promise<void> | undefined;
let profileName: string;
let profileStatus: number;
let profileWait: Promise<void> | undefined;
let passwordWait: Promise<void> | undefined;
let password: string;

beforeEach(() => {
  window.history.replaceState(null, "", "/");
  signedIn = true;
  preference = true;
  readStatus = 200;
  writeStatus = 200;
  waitForSave = undefined;
  profileName = account.displayName;
  profileStatus = 200;
  profileWait = undefined;
  passwordWait = undefined;
  password = "test password";
  fetcher.mockReset().mockImplementation(async (input, init) => {
    const path = new URL(String(input)).pathname;
    switch (path) {
      case "/api/users/me": return signedIn ? Response.json({ ...account, displayName: profileName }) : new Response(null, { status: 401 });
      case "/api/predictions/me": return Response.json(page([]));
      case "/api/auth/csrf": return Response.json({ headerName: "X-CSRF-TOKEN", token: "settings-csrf" });
      case "/api/auth/login":
        if (JSON.parse(String(init?.body)).password !== password) return new Response(null, { status: 401 });
        signedIn = true; return Response.json({ ...account, displayName: profileName });
      case "/api/auth/logout": signedIn = false; return new Response(null, { status: 204 });
      case "/api/users/me/profile": {
        expect(init?.credentials).toBe("include");
        expect(new Headers(init?.headers).get("X-CSRF-TOKEN")).toBe("settings-csrf");
        await profileWait;
        if (profileStatus === 409) return Response.json({ errors: { displayName: "Display name is already registered" } }, { status: 409 });
        if (profileStatus !== 200) return new Response(null, { status: profileStatus });
        profileName = JSON.parse(String(init?.body)).displayName;
        return Response.json({ ...account, displayName: profileName });
      }
      case "/api/users/me/password": {
        expect(init?.credentials).toBe("include");
        expect(new Headers(init?.headers).get("X-CSRF-TOKEN")).toBe("settings-csrf");
        const body = JSON.parse(String(init?.body));
        expect(Object.keys(body).sort()).toEqual(["currentPassword", "newPassword"]);
        await passwordWait;
        if (body.currentPassword !== password) return Response.json({ detail: "Current password is incorrect" }, { status: 400 });
        password = body.newPassword;
        signedIn = false;
        return new Response(null, { status: 204 });
      }
      case "/api/leaderboard/superliga": return Response.json(page([{ rank: 1, userId: account.id, displayName: profileName, totalPoints: 10 }], 0, 20));
      case `/api/leaderboard/superliga/users/${account.id}/predictions`: return Response.json({
        displayName: profileName, competitionId: "10000000-0000-0000-0000-000000000002", competitionName: "SuperLiga",
        seasonId: "10000000-0000-0000-0000-000000000003", seasonName: "2026/2027", totalPoints: 10,
        serverTime: "2026-11-19T12:00:00Z", predictions: page([]),
      });
      case "/api/users/me/settings": {
        expect(init?.credentials).toBe("include");
        expect(init?.cache).toBe("no-store");
        if (init?.method === "PUT") {
          expect(new Headers(init.headers).get("X-CSRF-TOKEN")).toBe("settings-csrf");
          expect(JSON.parse(String(init.body))).toEqual({ hidePredictionsUntilKickoff: !preference });
          await waitForSave;
          if (writeStatus !== 200) return new Response(null, { status: writeStatus });
          preference = JSON.parse(String(init.body)).hidePredictionsUntilKickoff;
        } else if (readStatus !== 200) return new Response(null, { status: readStatus });
        return Response.json({ hidePredictionsUntilKickoff: preference });
      }
      default: return footballResponse(input);
    }
  });
  vi.stubGlobal("fetch", fetcher);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const toggle = () => screen.getByRole<HTMLInputElement>("switch", { name: "Ascunde pronosticurile mele până la start" });
const writes = () => fetcher.mock.calls.filter(([input, init]) => String(input).endsWith("/users/me/settings") && init?.method === "PUT");

it("associates a taken-name error with Nume and allows correcting it", async () => {
  profileStatus = 409;
  window.history.replaceState(null, "", "/setari-cont");
  render(<App />);
  const name = await screen.findByLabelText<HTMLInputElement>("Nume");
  fireEvent.change(name, { target: { value: "Alisa" } });
  fireEvent.click(screen.getByRole("button", { name: "Salvează numele" }));
  expect((await screen.findByRole("alert")).textContent).toBe("Acest nume este deja folosit. Alege altul.");
  expect(name.getAttribute("aria-invalid")).toBe("true");
  expect(name.getAttribute("aria-describedby")).toContain("display-name-error");
  expect(document.getElementById("display-name-error")?.parentElement).toBe(name.parentElement);
  profileStatus = 200;
  fireEvent.change(name, { target: { value: "Alisa noua" } });
  expect(screen.queryByRole("alert")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Salvează numele" }));
  await screen.findByText("Numele a fost salvat.");
  expect(name.value).toBe("Alisa noua");
});

it.each([false, true])("maps the switch directly to the hide setting and restores %s after refresh", async (hide) => {
  preference = !hide;
  window.history.replaceState(null, "", "/setari-cont");
  const app = render(<App />);
  await screen.findByRole("switch");
  const explanation = "Activată: pronosticurile tale sunt ascunse până la începerea fiecărui meci. Dezactivată: ceilalți utilizatori autentificați le pot vedea imediat după salvare.";
  expect(screen.getByText(explanation)).toBeTruthy();
  expect(toggle().getAttribute("aria-describedby")).toBe("visibility-explanation");
  fireEvent.click(toggle());
  await screen.findByText("Setarea a fost salvată.");
  expect(toggle().checked).toBe(hide);
  expect(JSON.parse(String(writes()[0][1]?.body))).toEqual({ hidePredictionsUntilKickoff: hide });
  app.unmount();
  render(<App />);
  await screen.findByRole("switch");
  expect(toggle().checked).toBe(hide);
});

it("opens settings from the account-name disclosure and supports Escape", async () => {
  preference = false;
  await act(async () => { render(<App />); });
  const name = await screen.findByText(account.displayName);
  const summary = name.closest("summary")!;
  fireEvent.click(summary);
  expect(summary.closest("details")!.open).toBe(true);
  fireEvent.keyDown(document, { key: "Escape" });
  expect(summary.closest("details")!.open).toBe(false);
  expect(document.activeElement).toBe(summary);
  fireEvent.click(summary);
  fireEvent.click(screen.getByRole("link", { name: "Setările contului" }));
  expect(window.location.pathname).toBe("/setari-cont");
  await screen.findByRole("switch");
  expect(toggle().checked).toBe(false);
  expect(summary.closest("details")!.open).toBe(false);
});

it("guards a direct settings visit and returns there after login", async () => {
  signedIn = false;
  window.history.replaceState(null, "", "/setari-cont");
  render(<App />);
  expect(await screen.findByRole("heading", { name: "Autentificare" })).toBeTruthy();
  expect(window.location.pathname).toBe("/login");
  expect(fetcher.mock.calls.some(([url]) => String(url).endsWith("/users/me/settings"))).toBe(false);
  fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "test password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  await screen.findByRole("switch");
  expect(window.location.pathname).toBe("/setari-cont");
  expect(toggle().checked).toBe(true);
});

it("prevents overlapping writes, saves with CSRF, and restores the value after refresh and login", async () => {
  window.history.replaceState(null, "", "/setari-cont");
  const app = render(<App />);
  await screen.findByRole("switch");
  let finishSave!: () => void;
  waitForSave = new Promise(resolve => { finishSave = resolve; });
  fireEvent.click(toggle());
  expect(toggle().checked).toBe(false);
  expect(toggle().disabled).toBe(true);
  expect(screen.getByText("Se salvează…")).toBeTruthy();
  fireEvent.click(toggle());
  await waitFor(() => expect(writes()).toHaveLength(1));
  await act(async () => { finishSave(); });
  expect(await screen.findByText("Setarea a fost salvată.")).toBeTruthy();
  expect(toggle().disabled).toBe(false);
  expect(toggle().checked).toBe(false);
  app.unmount();
  render(<App />);
  await screen.findByRole("switch");
  expect(toggle().checked).toBe(false);
  fireEvent.click(screen.getByText(account.displayName).closest("summary")!);
  fireEvent.click(screen.getByRole("button", { name: "Deconectare" }));
  fireEvent.click(await within(screen.getByRole("navigation", { name: "Cont" })).findByRole("link", { name: "Intră în cont" }));
  fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "test password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  fireEvent.click((await screen.findByText(account.displayName)).closest("summary")!);
  fireEvent.click(screen.getByRole("link", { name: "Setările contului" }));
  await screen.findByRole("switch");
  expect(toggle().checked).toBe(false);
});

it.each([403, 503])("restores the saved value on HTTP %s and allows a retry", async (status) => {
  window.history.replaceState(null, "", "/setari-cont");
  writeStatus = status;
  render(<App />);
  await screen.findByRole("switch");
  fireEvent.click(toggle());
  expect((await screen.findByRole("alert")).textContent).toContain("Am păstrat ultima valoare salvată");
  expect(toggle().checked).toBe(true);
  expect(toggle().disabled).toBe(false);
  writeStatus = 200;
  fireEvent.click(toggle());
  await screen.findByText("Setarea a fost salvată.");
  expect(toggle().checked).toBe(false);
});

it("shows a retryable load error without a usable toggle", async () => {
  window.history.replaceState(null, "", "/setari-cont");
  readStatus = 503;
  render(<App />);
  expect((await screen.findByRole("alert")).textContent).toContain("Nu am putut încărca setările pronosticurilor");
  expect(screen.queryByRole("switch")).toBeNull();
  readStatus = 200;
  fireEvent.click(screen.getByRole("button", { name: "Încearcă din nou" }));
  expect((await screen.findByRole<HTMLInputElement>("switch")).checked).toBe(true);
});

it("validates, trims and saves the display name independently and updates the header and prediction views", async () => {
  window.history.replaceState(null, "", "/setari-cont");
  const app = render(<App />);
  await screen.findByRole("switch");
  const name = screen.getByLabelText<HTMLInputElement>("Nume");
  expect(name.value).toBe(account.displayName);
  for (const invalid of ["   ", "a".repeat(65)]) {
    fireEvent.change(name, { target: { value: invalid } });
    fireEvent.click(screen.getByRole("button", { name: "Salvează numele" }));
    expect(screen.getByRole("alert")).toBeTruthy();
  }
  expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith("/profile"))).toHaveLength(0);
  let finish!: () => void;
  profileWait = new Promise(resolve => { finish = resolve; });
  fireEvent.change(name, { target: { value: "  Alisa Ștefan  " } });
  fireEvent.click(screen.getByRole("button", { name: "Salvează numele" }));
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Se salvează numele…" }).disabled).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Se salvează numele…" }));
  expect(toggle().disabled).toBe(false);
  fireEvent.click(toggle());
  await screen.findByText("Setarea a fost salvată.");
  await waitFor(() => expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith("/profile"))).toHaveLength(1));
  await act(async () => { finish(); });
  await screen.findByText("Numele a fost salvat.");
  expect(name.value).toBe("Alisa Ștefan");
  expect(within(screen.getByRole("navigation", { name: "Cont" })).getByText("Alisa Ștefan")).toBeTruthy();
  fireEvent.click(screen.getByRole("link", { name: "Clasament" }));
  fireEvent.click(await screen.findByRole("link", { name: "Alisa Ștefan" }));
  expect(await screen.findByRole("heading", { name: "Pronosticuri · Alisa Ștefan" })).toBeTruthy();
  app.unmount();
  window.history.replaceState(null, "", "/setari-cont");
  render(<App />);
  expect((await screen.findByLabelText<HTMLInputElement>("Nume")).value).toBe("Alisa Ștefan");
});

it("preserves the entered display name on failure and keeps the other sections usable", async () => {
  window.history.replaceState(null, "", "/setari-cont");
  profileStatus = 503;
  render(<App />);
  await screen.findByRole("switch");
  fireEvent.change(screen.getByLabelText("Nume"), { target: { value: "Nume nou" } });
  fireEvent.click(screen.getByRole("button", { name: "Salvează numele" }));
  expect((await screen.findByRole("alert")).textContent).toContain("Modificarea nu a putut fi salvată");
  expect(screen.getByLabelText<HTMLInputElement>("Nume").value).toBe("Nume nou");
  expect(toggle().disabled).toBe(false);
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Schimbă parola" }).disabled).toBe(false);
});

function fillPasswords(current: string, next: string, confirmation: string) {
  fireEvent.change(screen.getByLabelText("Parola actuală"), { target: { value: current } });
  fireEvent.change(screen.getByLabelText("Parola nouă", { exact: true }), { target: { value: next } });
  fireEvent.change(screen.getByLabelText("Confirmă parola nouă"), { target: { value: confirmation } });
}

it.each([
  ["short", "short", "Parola trebuie să aibă cel puțin 8 caractere."],
  ["ă".repeat(37), "ă".repeat(37), "Parola poate avea cel mult 72 de octeți."],
  ["new password", "different password", "Parolele noi nu coincid."],
])("validates the new password before sending it: %s", async (next, confirmation, message) => {
  window.history.replaceState(null, "", "/setari-cont");
  render(<App />);
  await screen.findByRole("switch");
  fillPasswords("test password", next, confirmation);
  fireEvent.click(screen.getByRole("button", { name: "Schimbă parola" }));
  expect(screen.getByText(message)).toBeTruthy();
  expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith("/users/me/password"))).toHaveLength(0);
  expect(screen.getByLabelText<HTMLInputElement>("Parola nouă", { exact: true }).value).toBe(next);
});

it("preserves failed passwords, supports visibility, prevents duplicate saves and requires a new login after success", async () => {
  window.history.replaceState(null, "", "/setari-cont");
  render(<App />);
  await screen.findByRole("switch");
  fillPasswords("wrong password", "new password", "new password");
  fireEvent.click(screen.getByRole("button", { name: "Arată parola nouă" }));
  expect(screen.getByLabelText<HTMLInputElement>("Parola nouă", { exact: true }).type).toBe("text");
  fireEvent.click(screen.getByRole("button", { name: "Schimbă parola" }));
  expect((await screen.findByRole("alert")).textContent).toBe("Parola actuală este incorectă.");
  expect(screen.getByLabelText<HTMLInputElement>("Parola actuală").value).toBe("wrong password");
  expect(screen.getByLabelText<HTMLInputElement>("Confirmă parola nouă").value).toBe("new password");
  let finish!: () => void;
  passwordWait = new Promise(resolve => { finish = resolve; });
  fireEvent.change(screen.getByLabelText("Parola actuală"), { target: { value: "test password" } });
  fireEvent.click(screen.getByRole("button", { name: "Schimbă parola" }));
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Se schimbă parola…" }).disabled).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Se schimbă parola…" }));
  await waitFor(() => expect(fetcher.mock.calls.filter(([url]) => String(url).endsWith("/users/me/password"))).toHaveLength(2));
  expect(toggle().disabled).toBe(false);
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Salvează numele" }).disabled).toBe(false);
  await act(async () => { finish(); });
  expect(await screen.findByText("Parola a fost schimbată. Autentifică-te din nou.")).toBeTruthy();
  expect(window.location.pathname).toBe("/login");
  expect(screen.queryByText(/Contul a fost creat/)).toBeNull();
  expect(screen.queryByLabelText("Parola actuală")).toBeNull();
  expect(screen.getByLabelText<HTMLInputElement>("Parolă").value).toBe("");
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "test password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  expect(await screen.findByText("Adresa de email sau parola este incorectă.")).toBeTruthy();
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "new password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  expect(await within(screen.getByRole("navigation", { name: "Cont" })).findByText(account.displayName)).toBeTruthy();
});
