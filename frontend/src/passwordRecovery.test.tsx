import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import App from "./App";
import { footballResponse } from "./api/football.test-data";
import { invalidResetLink } from "./api/passwordRecovery";

const token = "a".repeat(43);
const confirmation = "Dacă există un cont asociat acestei adrese, vei primi un email cu instrucțiunile pentru resetarea parolei.";
const fetcher = vi.fn<typeof fetch>();
let validateResponse: () => Promise<Response>;
let confirmResponse: () => Promise<Response>;
let requestResponse: () => Promise<Response>;

beforeEach(() => {
  window.history.replaceState(null, "", "/login");
  validateResponse = async () => new Response(null, { status: 204 });
  confirmResponse = async () => new Response(null, { status: 204 });
  requestResponse = async () => Response.json({ message: confirmation });
  fetcher.mockReset().mockImplementation(async (input) => {
    switch (new URL(String(input)).pathname) {
      case "/api/users/me": return new Response(null, { status: 401 });
      case "/api/auth/csrf": return Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf" });
      case "/api/auth/password-reset/request": return requestResponse();
      case "/api/auth/password-reset/validate": return validateResponse();
      case "/api/auth/password-reset/confirm": return confirmResponse();
      default: return footballResponse(input);
    }
  });
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });
const calls = (operation: string) => fetcher.mock.calls.filter(([url]) => String(url).endsWith(`/password-reset/${operation}`));
function fillPasswords(password = "New-password-2026!", confirmation = password) {
  fireEvent.change(screen.getByLabelText("Parola nouă", { exact: true }), { target: { value: password } });
  fireEvent.change(screen.getByLabelText("Confirmă parola nouă", { exact: true }), { target: { value: confirmation } });
}

it("opens public recovery from login, normalizes email, shows the server message and prevents duplicate submissions", async () => {
  let release!: (response: Response) => void;
  requestResponse = () => new Promise(resolve => { release = resolve; });
  render(<App />);
  fireEvent.click(await screen.findByRole("link", { name: "Ai uitat parola?" }));
  expect(window.location.pathname).toBe("/recuperare-parola");
  fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: "invalid" } });
  fireEvent.click(screen.getByRole("button", { name: "Trimite linkul" }));
  expect(await screen.findByRole("alert")).toHaveProperty("textContent", "Introdu o adresă de email validă.");
  expect(calls("request")).toHaveLength(0);
  fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: "  FAN@EXAMPLE.TEST  " } });
  fireEvent.click(screen.getByRole("button", { name: "Trimite linkul" }));
  fireEvent.submit(screen.getByLabelText("Adresă de email").closest("form")!);
  await waitFor(() => expect(calls("request")).toHaveLength(1));
  expect(JSON.parse(String(calls("request")[0][1]?.body))).toEqual({ email: "fan@example.test" });
  expect(new Headers(calls("request")[0][1]?.headers).get("X-CSRF-TOKEN")).toBe("csrf");
  expect((screen.getByRole("button", { name: "Se trimite…" }) as HTMLButtonElement).disabled).toBe(true);
  release(Response.json({ message: confirmation }));
  expect(await screen.findByText(confirmation)).toBeTruthy();
  fireEvent.click(screen.getByRole("link", { name: "Înapoi la autentificare" }));
  expect(window.location.pathname).toBe("/login");
});

it("restores a link on refresh, validates passwords, supports visibility, clears secrets and returns to login after success", async () => {
  window.history.replaceState(null, "", `/reseteaza-parola#token=${token}`);
  const app = render(<App />);
  await screen.findByLabelText("Parola nouă", { exact: true });
  app.unmount(); render(<App />);
  const input = await screen.findByLabelText("Parola nouă", { exact: true }) as HTMLInputElement;
  expect(input.autocomplete).toBe("new-password");
  expect((screen.getByLabelText("Confirmă parola nouă") as HTMLInputElement).autocomplete).toBe("new-password");
  fireEvent.click(screen.getByRole("button", { name: "Arată parola nouă" }));
  expect(input.type).toBe("text");
  fireEvent.click(screen.getByRole("button", { name: "Ascunde parola nouă" }));
  expect(input.type).toBe("password");
  fillPasswords("New-password-2026!", "Different-password!");
  fireEvent.click(screen.getByRole("button", { name: "Resetează parola" }));
  expect(await screen.findByText("Parolele noi nu coincid.")).toBeTruthy();
  expect(calls("confirm")).toHaveLength(0);
  fillPasswords("ă".repeat(37));
  fireEvent.click(screen.getByRole("button", { name: "Resetează parola" }));
  expect(await screen.findByText("Parola poate avea cel mult 72 de octeți.")).toBeTruthy();
  expect(calls("confirm")).toHaveLength(0);
  fillPasswords();
  fireEvent.click(screen.getByRole("button", { name: "Resetează parola" }));
  await screen.findByText("Parola a fost resetată. Autentifică-te cu noua parolă.");
  expect(window.location.pathname + window.location.hash).toBe("/login");
  expect((screen.getByLabelText("Parolă") as HTMLInputElement).value).toBe("");
  expect(JSON.parse(String(calls("confirm")[0][1]?.body))).toEqual({ token, newPassword: "New-password-2026!" });
  expect(calls("validate").length).toBeGreaterThanOrEqual(2);
  expect(fetcher.mock.calls.every(([url]) => !String(url).includes(token))).toBe(true);
  expect(localStorage.length).toBe(0);
});

it("shows an invalid-link message without submitting missing or malformed tokens", async () => {
  window.history.replaceState(null, "", "/reseteaza-parola#token=bad");
  render(<App />);
  expect(await screen.findByText(invalidResetLink)).toBeTruthy();
  expect(calls("validate")).toHaveLength(0);
  expect(screen.queryByLabelText("Parola nouă")).toBeNull();
  fireEvent.click(screen.getByRole("link", { name: "Solicită un link nou" }));
  expect(window.location.pathname).toBe("/recuperare-parola");
  expect(screen.getByLabelText("Adresă de email")).toBeTruthy();
});

it("rejects expired or used links on opening and offers another recovery request", async () => {
  validateResponse = async () => Response.json({ detail: invalidResetLink }, { status: 400 });
  window.history.replaceState(null, "", `/reseteaza-parola#token=${token}`);
  render(<App />);
  expect(await screen.findByText(invalidResetLink)).toBeTruthy();
  expect(screen.getByRole("link", { name: "Solicită un link nou" }).getAttribute("href")).toBe("/recuperare-parola");
  expect(calls("confirm")).toHaveLength(0);
});

it("handles a link consumed during editing and prevents duplicate reset submissions", async () => {
  let release!: (response: Response) => void;
  confirmResponse = () => new Promise(resolve => { release = resolve; });
  window.history.replaceState(null, "", `/reseteaza-parola#token=${token}`);
  render(<App />);
  await screen.findByLabelText("Parola nouă", { exact: true });
  fillPasswords();
  const form = screen.getByLabelText("Parola nouă", { exact: true }).closest("form")!;
  fireEvent.submit(form); fireEvent.submit(form);
  await waitFor(() => expect(calls("confirm")).toHaveLength(1));
  expect((screen.getByRole("button", { name: "Se resetează…" }) as HTMLButtonElement).disabled).toBe(true);
  release(Response.json({ detail: invalidResetLink }, { status: 400 }));
  expect(await screen.findByText(invalidResetLink)).toBeTruthy();
  expect(screen.queryByLabelText("Parola nouă", { exact: true })).toBeNull();
});

it("shows rate-limit and network errors and lets users retry link validation", async () => {
  validateResponse = async () => new Response(null, { status: 429, headers: { "Retry-After": "120" } });
  window.history.replaceState(null, "", `/reseteaza-parola#token=${token}`);
  render(<App />);
  expect(await screen.findByText("Prea multe încercări. Încearcă din nou peste 2 minute.")).toBeTruthy();
  validateResponse = async () => { throw new TypeError("network unavailable"); };
  fireEvent.click(screen.getByRole("button", { name: "Reîncearcă verificarea" }));
  expect(await screen.findByText("Cererea nu a putut fi trimisă. Încearcă din nou.")).toBeTruthy();
  validateResponse = async () => new Response(null, { status: 204 });
  fireEvent.click(screen.getByRole("button", { name: "Reîncearcă verificarea" }));
  await screen.findByLabelText("Parola nouă", { exact: true });
  const main = screen.getByRole("main", { name: "" });
  expect(within(main).getByRole("button", { name: "Resetează parola" })).toBeTruthy();
});
