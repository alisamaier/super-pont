import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import App from "./App";
import { footballResponse } from "./api/football.test-data";

const account = { id: "user-id", username: "supporter", email: "fan@example.test", role: "USER" };
const fetcher = vi.fn<typeof fetch>();
let signedIn: boolean;
let registrationConflict: object | undefined;
let throttled: { path: "login" | "register"; retryAfter: string | null } | undefined;

beforeEach(() => {
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
  vi.unstubAllGlobals();
});

it("registers, signs in, restores the session on reload and signs out", async () => {
  const app = render(<App />);
  fireEvent.click(await screen.findByRole("link", { name: "Cont nou" }));
  expect(window.location.pathname).toBe("/register");
  fireEvent.change(screen.getByLabelText("Nume de utilizator"), { target: { value: "Supporter" } });
  fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "good password" } });
  fireEvent.click(screen.getByRole("button", { name: "Creează cont" }));
  expect(await screen.findByText(/Contul a fost creat/)).toBeTruthy();
  expect(window.location.pathname).toBe("/login");
  expect((screen.getByLabelText("Nume de utilizator") as HTMLInputElement).value).toBe(account.username);

  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "good password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  const header = within(screen.getByRole("navigation", { name: "Cont" }));
  expect(await header.findByText(account.username)).toBeTruthy();
  expect(window.location.pathname).toBe("/");
  app.unmount();
  render(<App />);
  const restored = within(screen.getByRole("navigation", { name: "Cont" }));
  expect(await restored.findByText(account.username)).toBeTruthy();
  fireEvent.click(restored.getByRole("button", { name: "Deconectare" }));
  expect(await restored.findByRole("link", { name: "Intră în cont" })).toBeTruthy();

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
  fireEvent.change(screen.getByLabelText("Nume de utilizator"), { target: { value: account.username } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "wrong password" } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  expect(await screen.findByText(/parola este incorectă/)).toBeTruthy();
  expect(window.location.pathname).toBe("/login");
});

async function fillRegistration() {
  window.history.replaceState(null, "", "/register");
  render(<App />);
  await screen.findByRole("link", { name: "Cont nou" });
  const username = screen.getByLabelText("Nume de utilizator");
  const email = screen.getByLabelText("E-mail");
  fireEvent.change(username, { target: { value: "Supporter" } });
  fireEvent.change(email, { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText("Parolă"), { target: { value: "good password" } });
  return { username, email, submit: screen.getByRole("button", { name: "Creează cont" }) };
}

it("shows a taken username below the username field and clears it on edit", async () => {
  registrationConflict = { errors: { username: "Username is already taken" } };
  const { username, email, submit } = await fillRegistration();
  fireEvent.click(submit);
  const message = "Acest nume de utilizator este deja folosit.";
  await screen.findByText(message);
  expectFieldError(username, message);
  expect(email.getAttribute("aria-invalid")).toBeNull();
  expect(screen.queryByRole("alert")).toBeNull();

  fireEvent.change(username, { target: { value: "AnotherSupporter" } });
  expect(screen.queryByText(message)).toBeNull();
  expect(username.getAttribute("aria-invalid")).toBeNull();
});

it("shows a generic registration conflict without marking the email field", async () => {
  registrationConflict = { detail: "Registration could not be completed" };
  const { username, email, submit } = await fillRegistration();
  fireEvent.click(submit);
  expect((await screen.findByRole("alert")).textContent).toBe("Nu am putut crea contul cu aceste date.");
  expect(username.getAttribute("aria-invalid")).toBeNull();
  expect(email.getAttribute("aria-invalid")).toBeNull();
});

it.each([false, true])("displays the email conflict with username conflict=%s", async (both) => {
  registrationConflict = {
    errors: {
      email: "Email is already registered",
      ...(both ? { username: "Username is already taken" } : {}),
    },
  };
  const { username, email, submit } = await fillRegistration();
  fireEvent.click(submit);
  const message = "Această adresă de e-mail este deja înregistrată.";
  await screen.findByText(message);
  expectFieldError(email, message);
  if (both) {
    expectFieldError(username, "Acest nume de utilizator este deja folosit.");
  } else {
    expect(username.getAttribute("aria-invalid")).toBeNull();
  }
  fireEvent.change(email, { target: { value: "new@example.test" } });
  expect(email.getAttribute("aria-invalid")).toBeNull();
  expect(screen.queryByText(message)).toBeNull();
  if (both) expectFieldError(username, "Acest nume de utilizator este deja folosit.");
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
    fireEvent.change(screen.getByLabelText("Nume de utilizator"), { target: { value: account.username } });
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
  const button = screen.getByRole("button", { name: submitLabel });
  expect(button.closest("form")?.noValidate).toBe(true);
  fireEvent.click(button);

  const username = screen.getByLabelText("Nume de utilizator");
  const password = screen.getByLabelText("Parolă");
  expectFieldError(username, "Introdu numele de utilizator.");
  expectFieldError(password, "Introdu parola.");
  if (path === "/register") {
    expectFieldError(screen.getByLabelText("E-mail"), "Introdu adresa de e-mail.");
  }

  fireEvent.change(username, { target: { value: "Supporter" } });
  expect(screen.queryByText("Introdu numele de utilizator.")).toBeNull();
  expect(username.getAttribute("aria-invalid")).toBeNull();
  expectFieldError(password, "Introdu parola.");
  expectNoAuthRequest();
});

it("validates registration username, email and minimum password length inline", async () => {
  window.history.replaceState(null, "", "/register");
  render(<App />);
  await screen.findByRole("link", { name: "Cont nou" });
  const username = screen.getByLabelText("Nume de utilizator");
  const email = screen.getByLabelText("E-mail");
  const password = screen.getByLabelText("Parolă");

  fireEvent.change(username, { target: { value: "fan!" } });
  fireEvent.change(email, { target: { value: "invalid-email" } });
  fireEvent.change(password, { target: { value: "scurt" } });
  fireEvent.click(screen.getByRole("button", { name: "Creează cont" }));
  expectFieldError(username, "Folosește între 3 și 32 de litere latine, cifre sau caractere _.");
  expectFieldError(email, "Introdu o adresă de e-mail validă.");
  expectFieldError(password, "Parola trebuie să aibă cel puțin 8 caractere.");
  expectNoAuthRequest();

  fireEvent.change(username, { target: { value: "Fan_2026" } });
  fireEvent.change(email, { target: { value: account.email } });
  fireEvent.change(password, { target: { value: "good password" } });
  for (const field of [username, email, password]) {
    expect(field.getAttribute("aria-invalid")).toBeNull();
    expect(field.nextElementSibling?.textContent).toBe("");
  }

  fireEvent.click(screen.getByRole("button", { name: "Creează cont" }));
  expect(await screen.findByText(/Contul a fost creat/)).toBeTruthy();
});

it("validates login username length and the UTF-8 password limit inline", async () => {
  window.history.replaceState(null, "", "/login");
  render(<App />);
  await screen.findByRole("link", { name: "Cont nou" });
  const username = screen.getByLabelText("Nume de utilizator");
  const password = screen.getByLabelText("Parolă");

  fireEvent.change(username, { target: { value: "x".repeat(33) } });
  fireEvent.change(password, { target: { value: "ă".repeat(37) } });
  fireEvent.click(screen.getByRole("button", { name: "Intră în cont" }));
  expectFieldError(username, "Numele de utilizator poate avea cel mult 32 de caractere.");
  expectFieldError(password, "Parola poate avea cel mult 72 de octeți.");
  expectNoAuthRequest();

  fireEvent.change(password, { target: { value: "ă".repeat(36) } });
  expect(screen.queryByText("Parola poate avea cel mult 72 de octeți.")).toBeNull();
  expect(password.getAttribute("aria-invalid")).toBeNull();
});
