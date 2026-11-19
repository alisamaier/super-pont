import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import App from "./App";
import { footballResponse, matches, page } from "./api/football.test-data";

const account = { id: "user-id", displayName: "Suporter", email: "fan@example.test", role: "USER" };
const fetcher = vi.fn<typeof fetch>();
let signedIn: boolean;
let registrationConflict: object | undefined;
let throttled: { path: "login" | "register"; retryAfter: string | null } | undefined;

beforeEach(() => {
  vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-11-01T18:00:00Z"));
  signedIn = false;
  registrationConflict = undefined;
  throttled = undefined;
  window.history.replaceState(null, "", "/");
  localStorage.clear();
  fetcher.mockReset().mockImplementation(async (input, init) => {
    const path = new URL(String(input)).pathname;
    if (throttled && path === `/api/auth/${throttled.path}`) {
      const headers = new Headers();
      if (throttled.retryAfter !== null) headers.set("Retry-After", throttled.retryAfter);
      return new Response(null, { status: 429, headers });
    }
    switch (path) {
      case "/api/users/me":
        return signedIn ? Response.json(account) : new Response(null, { status: 401 });
      case "/api/predictions/me":
        return signedIn
          ? Response.json(page([{ matchId: matches[0].id, homeScore: 2, awayScore: 1, result: null }]))
          : new Response(null, { status: 401 });
      case "/api/auth/csrf":
        return Response.json({ headerName: "X-CSRF-TOKEN", token: "session-csrf" });
      case "/api/auth/register":
        return registrationConflict
          ? Response.json(registrationConflict, { status: 409 })
          : Response.json(account);
      case "/api/auth/login":
        if (JSON.parse(String(init?.body)).password !== "good password") {
          return new Response(null, { status: 401 });
        }
        signedIn = true;
        return Response.json(account);
      case "/api/auth/logout":
        signedIn = false;
        return new Response(null, { status: 204 });
      default:
        return footballResponse(input);
    }
  });
  vi.stubGlobal("fetch", fetcher);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

it("registers, signs in, restores the session on reload and signs out", async () => {
  const app = render(<App />);
  let navigation = screen.getByRole("navigation", { name: "Navigare principală" });
  expect(within(navigation).queryByRole("button", { name: /Pronosticurile mele/ })).toBeNull();
  expect(within(navigation).queryByRole("link", { name: "Clasament" })).toBeNull();
  expect(navigation.querySelector(".nav-count")).toBeNull();
  fireEvent.click(await screen.findByRole("link", { name: "Cont nou" }));
  expect(window.location.pathname).toBe("/register");
  fireEvent.change(screen.getByLabelText("Nume"), { target: { value: "Suporter" } });
  fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "good password" } });
  fireEvent.click(screen.getByRole("button", { name: "Creează cont" }));
  expect(await screen.findByText(/Contul a fost creat/)).toBeTruthy();
  expect(window.location.pathname).toBe("/login");
  expect((screen.getByLabelText("Adresă de email") as HTMLInputElement).value).toBe(account.email);

  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "good password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  const header = within(screen.getByRole("navigation", { name: "Cont" }));
  expect(await header.findByText(account.displayName)).toBeTruthy();
  expect(window.location.pathname).toBe("/");
  await screen.findByRole("button", { name: "Pronostic salvat" });
  expect(within(navigation).getByRole("link", { name: "Clasament" })).toBeTruthy();
  expect(within(navigation).getByRole("button", { name: /Pronosticurile mele/ }).querySelector(".nav-count")?.textContent).toBe("1");
  app.unmount();
  render(<App />);
  navigation = screen.getByRole("navigation", { name: "Navigare principală" });
  expect(within(navigation).queryByRole("button", { name: /Pronosticurile mele/ })).toBeNull();
  expect(within(navigation).queryByRole("link", { name: "Clasament" })).toBeNull();
  expect(navigation.querySelector(".nav-count")).toBeNull();
  const restored = within(screen.getByRole("navigation", { name: "Cont" }));
  expect(await restored.findByText(account.displayName)).toBeTruthy();
  await waitFor(() => expect(within(navigation).getByRole("button", { name: /Pronosticurile mele/ }).querySelector(".nav-count")?.textContent).toBe("1"));
  fireEvent.click(within(navigation).getByRole("button", { name: /Pronosticurile mele/ }));
  fireEvent.click(restored.getByText(account.displayName).closest("summary")!);
  fireEvent.click(restored.getByRole("button", { name: "Deconectare" }));
  expect(await restored.findByRole("link", { name: "Intră în cont" })).toBeTruthy();
  expect(within(navigation).queryByRole("button", { name: /Pronosticurile mele/ })).toBeNull();
  expect(within(navigation).queryByRole("link", { name: "Clasament" })).toBeNull();
  expect(navigation.querySelector(".nav-count")).toBeNull();
  expect(restored.getByRole("link", { name: "Cont nou" })).toBeTruthy();
  expect(screen.getByRole("heading", { name: "Meciurile etapei" })).toBeTruthy();
  expect(window.location.pathname).toBe("/");

  const authCalls = fetcher.mock.calls.filter(([input]) => /\/api\/(auth\/|users\/me)/.test(String(input)));
  expect(authCalls.every(([, init]) => init?.credentials === "include")).toBe(true);
  const writes = authCalls.filter(([, init]) => init?.method === "POST");
  expect(writes.map(([url]) => new URL(String(url)).pathname)).toEqual([
    "/api/auth/register", "/api/auth/login", "/api/auth/logout",
  ]);
  expect(writes.every(([, init]) => new Headers(init?.headers).get("X-CSRF-TOKEN") === "session-csrf")).toBe(true);
  expect(authCalls.filter(([url]) => String(url).endsWith("/auth/csrf"))).toHaveLength(3);
  expect(localStorage.length).toBe(0);
});

it("shows an invalid credentials error on the direct login page", async () => {
  window.history.replaceState(null, "", "/login");
  render(<App />);
  await screen.findByRole("link", { name: "Cont nou" });
  fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "wrong password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  expect(await screen.findByText(/parola este incorectă/)).toBeTruthy();
  expect(window.location.pathname).toBe("/login");
});

async function fillRegistration() {
  window.history.replaceState(null, "", "/register");
  render(<App />);
  await screen.findByRole("link", { name: "Cont nou" });
  const name = screen.getByLabelText("Nume");
  const email = screen.getByLabelText("Adresă de email");
  fireEvent.change(name, { target: { value: "Suporter" } });
  fireEvent.change(email, { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "good password" } });
  return { name, email, submit: screen.getByRole("button", { name: "Creează cont" }) };
}

it("shows a generic registration conflict without marking the email field", async () => {
  registrationConflict = { detail: "Registration could not be completed" };
  const { name, email, submit } = await fillRegistration();
  fireEvent.click(submit);
  expect((await screen.findByRole("alert")).textContent).toBe("Nu am putut crea contul cu aceste date.");
  expect(name.getAttribute("aria-invalid")).toBeNull();
  expect(email.getAttribute("aria-invalid")).toBeNull();
});

it("displays the email conflict without marking the public name and clears it on edit", async () => {
  registrationConflict = { errors: { email: "Email is already registered" } };
  const { name, email, submit } = await fillRegistration();
  fireEvent.click(submit);
  const message = "Această adresă de e-mail este deja înregistrată.";
  await screen.findByText(message);
  expectFieldError(email, message);
  expect(name.getAttribute("aria-invalid")).toBeNull();
  fireEvent.change(email, { target: { value: "new@example.test" } });
  expect(email.getAttribute("aria-invalid")).toBeNull();
  expect(screen.queryByText(message)).toBeNull();
});

it.each([
  { path: "login", retryAfter: "60", duration: "1 minut" },
  { path: "register", retryAfter: "583", duration: "9 minute și 43 de secunde" },
  { path: "login", retryAfter: "1", duration: "1 secundă" },
  { path: "register", retryAfter: "61", duration: "1 minut și 1 secundă" },
  { path: "login", retryAfter: "43", duration: "43 de secunde" },
  { path: "login", retryAfter: null, duration: null },
] as const)("shows a Romanian retry message on $path for Retry-After=$retryAfter", async ({ path, retryAfter, duration }) => {
  throttled = { path, retryAfter };
  if (path === "register") {
    const { submit } = await fillRegistration();
    fireEvent.click(submit);
  } else {
    window.history.replaceState(null, "", "/login");
    render(<App />);
    await screen.findByRole("link", { name: "Cont nou" });
    fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: account.email } });
    fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "good password" } });
    fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  }
  expect((await screen.findByRole("alert")).textContent).toBe(duration
    ? `Prea multe încercări. Încearcă din nou peste ${duration}.`
    : "Prea multe încercări. Așteaptă și încearcă din nou.");
  expect(window.location.pathname).toBe(`/${path}`);
});

function expectFieldError(field: HTMLElement, message: string) {
  const error = screen.getByText(message);
  expect(field.getAttribute("aria-invalid")).toBe("true");
  expect(field.getAttribute("aria-describedby")?.split(" ")).toContain(error.id);
  expect(field.nextElementSibling).toBe(error);
  expect(error.getAttribute("aria-live")).toBe("polite");
}

function expectNoAuthRequest() {
  expect(fetcher.mock.calls.some(([url]) => String(url).includes("/api/auth/"))).toBe(false);
}

it.each([
  { path: "/login", submitLabel: "Intră în cont" },
  { path: "/register", submitLabel: "Creează cont" },
])("shows accessible required-field errors on $path", async ({ path, submitLabel }) => {
  window.history.replaceState(null, "", path);
  render(<App />);
  await screen.findByRole("link", { name: "Cont nou" });
  fireEvent.click(screen.getByRole("button", { name: submitLabel }));
  expectFieldError(screen.getByLabelText("Adresă de email"), "Introdu adresa de email.");
  expectFieldError(screen.getByLabelText("Parolă"), "Introdu parola.");
  if (path === "/register") expectFieldError(screen.getByLabelText("Nume"), "Introdu numele.");
  else expect(screen.queryByLabelText("Nume")).toBeNull();
  fireEvent.change(screen.getByLabelText("Adresă de email"), { target: { value: account.email } });
  expect(screen.queryByText("Introdu adresa de email.")).toBeNull();
  expectFieldError(screen.getByLabelText("Parolă"), "Introdu parola.");
  expectNoAuthRequest();
});

it("validates registration name, email and password without requiring a unique name", async () => {
  window.history.replaceState(null, "", "/register");
  render(<App />);
  await screen.findByRole("link", { name: "Cont nou" });
  const name = screen.getByLabelText("Nume");
  const email = screen.getByLabelText("Adresă de email");
  const password = screen.getByLabelText("Parolă");
  fireEvent.change(name, { target: { value: "x".repeat(65) } });
  fireEvent.change(email, { target: { value: "invalid-email" } });
  fireEvent.change(password, { target: { value: "scurt" } });
  fireEvent.click(screen.getByRole("button", { name: "Creează cont" }));
  expectFieldError(name, "Numele poate avea cel mult 64 de caractere.");
  expectFieldError(email, "Introdu o adresă de email validă.");
  expectFieldError(password, "Parola trebuie să aibă cel puțin 8 caractere.");
  expectNoAuthRequest();
  fireEvent.change(name, { target: { value: "  Suporter Ștefan  " } });
  fireEvent.change(email, { target: { value: "  FAN@EXAMPLE.TEST  " } });
  fireEvent.change(password, { target: { value: "good password" } });
  fireEvent.click(screen.getByRole("button", { name: "Creează cont" }));
  await screen.findByText(/Contul a fost creat/);
  const call = fetcher.mock.calls.find(([url]) => String(url).endsWith("/auth/register"));
  expect(JSON.parse(String(call?.[1]?.body))).toEqual({ displayName: "Suporter Ștefan", email: "fan@example.test", password: "good password" });
});

it("validates the login email and UTF-8 password limit", async () => {
  window.history.replaceState(null, "", "/login");
  render(<App />);
  await screen.findByRole("link", { name: "Cont nou" });
  const email = screen.getByLabelText<HTMLInputElement>("Adresă de email");
  expect(email.type).toBe("email");
  expect(email.autocomplete).toBe("email");
  fireEvent.change(email, { target: { value: "x".repeat(250) + "@example.test" } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "ă".repeat(37) } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  expectFieldError(email, "Adresa de email poate avea cel mult 254 de caractere.");
  expectFieldError(screen.getByLabelText("Parolă"), "Parola poate avea cel mult 72 de octeți.");
  expectNoAuthRequest();
});
