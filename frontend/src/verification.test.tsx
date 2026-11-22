import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { VerificationPage } from "./components/VerificationPage";
import type { PendingRegistration } from "./api/auth";

const fetcher = vi.fn<typeof fetch>();
const onConfirmed = vi.fn();
let data: PendingRegistration;
let confirmError: string | undefined;
let attemptsRemaining: number;
let resendStatus: number;

beforeEach(() => {
  confirmError = undefined;
  attemptsRemaining = 4;
  resendStatus = 200;
  onConfirmed.mockReset();
  data = { id: "pending", email: "fan@example.test", state: "PENDING", serverTime: "2026-11-19T12:00:00Z",
    codeExpiresAt: "2026-11-19T12:10:00Z", resendAvailableAt: "2026-11-19T12:00:00Z", attemptsRemaining: 5, deliveryStatus: "SENT" };
  fetcher.mockReset().mockImplementation(async (input, init) => {
    expect(init?.credentials).toBe("include");
    const path = new URL(String(input)).pathname;
    if (path === "/api/auth/csrf") return Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf" });
    if (path === "/api/auth/registration") return Response.json(data);
    expect(new Headers(init?.headers).get("X-CSRF-TOKEN")).toBe("csrf");
    if (path.endsWith("/confirm")) {
      return confirmError ? Response.json({ code: confirmError, attemptsRemaining }, { status: 400 })
        : Response.json({ ...data, state: "VERIFIED" });
    }
    if (path.endsWith("/resend")) {
      if (resendStatus === 429) return Response.json({ code: "RATE_LIMITED" }, { status: 429, headers: { "Retry-After": "120" } });
      data = { ...data, deliveryStatus: "SENT", attemptsRemaining: 5, resendAvailableAt: "2026-11-19T12:01:00Z" };
      return Response.json(data);
    }
    if (path.endsWith("/email")) {
      data = { ...data, email: JSON.parse(String(init?.body)).email, deliveryStatus: "SENT", resendAvailableAt: "2026-11-19T12:01:00Z" };
      return Response.json(data);
    }
    return new Response(null, { status: 404 });
  });
  vi.stubGlobal("fetch", fetcher);
});

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
const mount = () => render(<VerificationPage onConfirmed={onConfirmed} onNavigate={vi.fn()} />);

it("keeps a pasted code with leading zeros and uses one-time-code mobile input", async () => {
  mount();
  const input = await screen.findByLabelText<HTMLInputElement>("Cod de verificare");
  expect(input.getAttribute("type")).toBe("text");
  expect(input.getAttribute("inputmode")).toBe("numeric");
  expect(input.getAttribute("autocomplete")).toBe("one-time-code");
  fireEvent.change(input, { target: { value: "001234" } });
  fireEvent.click(screen.getByRole("button", { name: "Confirmă contul" }));
  await waitFor(() => expect(onConfirmed).toHaveBeenCalledWith("fan@example.test"));
  const call = fetcher.mock.calls.find(([url]) => String(url).endsWith("/confirm"));
  expect(JSON.parse(String(call?.[1]?.body))).toEqual({ code: "001234" });
});

it.each([
  ["INCORRECT_CODE", "Codul este incorect.", 4],
  ["CODE_EXPIRED", "Codul a expirat.", 5],
  ["CODE_ATTEMPTS_EXHAUSTED", "Ai epuizat cele 5 încercări.", 0],
] as const)("explains %s and updates the remaining attempt count", async (code, message, remaining) => {
  confirmError = code;
  attemptsRemaining = remaining;
  mount();
  fireEvent.change(await screen.findByLabelText("Cod de verificare"), { target: { value: "111111" } });
  fireEvent.click(screen.getByRole("button", { name: "Confirmă contul" }));
  expect((await screen.findByRole("alert")).textContent).toContain(message);
  expect(screen.getByText(`${remaining} încercări rămase pentru acest cod.`)).toBeTruthy();
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Confirmă contul" }).disabled).toBe(remaining === 0);
});

it("restores server state on refresh and calculates cooldown from server time", async () => {
  data.resendAvailableAt = "2026-11-19T12:01:00Z";
  vi.spyOn(Date, "now").mockReturnValue(Date.parse("2030-01-01T00:00:00Z"));
  mount();
  expect(await screen.findByText("fan@example.test", { exact: true })).toBeTruthy();
  await waitFor(() => expect(screen.getByRole<HTMLButtonElement>("button", { name: /Retrimite codul \(60s\)/ }).disabled).toBe(true));
  cleanup();
  data.serverTime = "2026-11-19T12:01:01Z";
  mount();
  await screen.findByLabelText("Cod de verificare");
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "Retrimite codul" }).disabled).toBe(false);
});

it("recovers from delivery failure and shows a new server cooldown after resending", async () => {
  data.deliveryStatus = "FAILED";
  data.codeExpiresAt = null;
  mount();
  expect((await screen.findByRole("alert")).textContent).toContain("Contul este păstrat");
  fireEvent.click(screen.getByRole("button", { name: "Retrimite codul" }));
  expect(await screen.findByText("Am trimis un cod nou. Codul anterior nu mai este valabil.")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
  expect((await screen.findByRole<HTMLButtonElement>("button", { name: /Retrimite codul \(60s\)/ })).disabled).toBe(true);
});

it("shows rate limits and corrects the submitted email without storing a token in browser storage", async () => {
  mount();
  await screen.findByLabelText("Cod de verificare");
  fireEvent.click(screen.getByRole("button", { name: "Schimbă adresa de email" }));
  fireEvent.change(screen.getByLabelText("Noua adresă de email"), { target: { value: "  Correct@EXAMPLE.test " } });
  fireEvent.click(screen.getByRole("button", { name: "Salvează și trimite codul" }));
  expect(await screen.findByText("correct@example.test", { exact: true })).toBeTruthy();
  expect(localStorage.getItem("registrationToken")).toBeNull();
  cleanup();
  data.resendAvailableAt = data.serverTime;
  resendStatus = 429;
  mount();
  await screen.findByLabelText("Cod de verificare");
  fireEvent.click(screen.getByRole("button", { name: "Retrimite codul" }));
  expect((await screen.findByRole("alert")).textContent).toContain("2 minute");
  expect((await screen.findByRole<HTMLButtonElement>("button", { name: /Retrimite codul \(120s\)/ })).disabled).toBe(true);
});

it("leaves public navigation usable and gives a recovery link when the pending cookie expires", async () => {
  fetcher.mockImplementation(async () => Response.json({ code: "REGISTRATION_EXPIRED" }, { status: 401 }));
  mount();
  expect((await screen.findByRole("alert")).textContent).toContain("Autentifică-te");
  expect(screen.getByRole("link", { name: "Autentifică-te pentru a relua confirmarea" }).getAttribute("href")).toBe("/login");
  expect(screen.getByRole("link", { name: "Înapoi la meciuri" }).getAttribute("href")).toBe("/");
});
