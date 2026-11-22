import { apiUrl } from "../config";
import { normalizeEmail, displayNameTakenMessage } from "../lib/accountValidation";

export interface AuthUser {
  id: string;
  displayName: string;
  email: string;
  role: "USER" | "ADMIN";
  createdAt: string;
}

export interface PendingRegistration {
  id: string;
  email: string;
  state: "PENDING" | "VERIFIED";
  serverTime: string;
  codeExpiresAt: string | null;
  resendAvailableAt: string;
  attemptsRemaining: number;
  deliveryStatus: "NONE" | "SENT" | "FAILED" | "RATE_LIMITED";
}

export function isPendingRegistration(value: AuthUser | PendingRegistration): value is PendingRegistration {
  return "state" in value;
}

interface LoginInput {
  email: string;
  password: string;
}

interface RegistrationInput extends LoginInput {
  displayName: string;
}

interface CsrfToken {
  headerName: string;
  token: string;
}

type RegistrationErrors = Partial<Record<"email" | "displayName", string>>;

const sessionExpiredEvent = "super-pont:session-expired";
let sessionVersion = 0;

export class SessionExpiredError extends Error {
  constructor() {
    super("Sesiunea a expirat. Intră din nou în cont.");
    this.name = "SessionExpiredError";
  }
}

export function onSessionExpired(listener: () => void) {
  window.addEventListener(sessionExpiredEvent, listener);
  return () => window.removeEventListener(sessionExpiredEvent, listener);
}

class AuthError extends Error {
  constructor(
    readonly status: number,
    readonly fieldErrors: RegistrationErrors,
    readonly retryAfter: number,
    readonly code?: string,
    readonly attemptsRemaining?: number,
  ) {
    super(`Authentication request failed (${status})`);
  }
}

export async function sessionFetch(path: string, init: RequestInit = {}) {
  const version = sessionVersion;
  const response = await fetch(`${apiUrl}/api${path}`, {
    ...init,
    credentials: "include",
  });
  // Login failures and the initial anonymous session check have their own auth flow.
  if (response.status === 401 && !path.startsWith("/auth/") && path.split("?")[0] !== "/users/me") {
    if (!init.signal?.aborted && version === sessionVersion) {
      sessionVersion++;
      console.info("[Super Pont] Sesiunea nu mai este validă.", { path, status: response.status });
      window.dispatchEvent(new Event(sessionExpiredEvent));
    }
    throw new SessionExpiredError();
  }
  return response;
}

async function request(path: string, init: RequestInit = {}) {
  const response = await sessionFetch(path, init);
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    const fieldErrors: RegistrationErrors = {};
    if (typeof problem?.errors?.email === "string") {
      fieldErrors.email = "Această adresă de e-mail este deja înregistrată.";
    }
    if (typeof problem?.errors?.displayName === "string") fieldErrors.displayName = displayNameTakenMessage;
    throw new AuthError(response.status, fieldErrors, Number(response.headers.get("Retry-After")),
      problem?.code, problem?.attemptsRemaining);
  }
  return response;
}

export async function csrfHeaders(signal?: AbortSignal) {
  const csrf = await request("/auth/csrf", { signal });
  const { headerName, token }: CsrfToken = await csrf.json();
  return new Headers({ [headerName]: token });
}

async function submit(path: string, body?: object) {
  const headers = await csrfHeaders();
  if (body) headers.set("Content-Type", "application/json");
  return request(path, {
    method: "POST",
    headers,
    body: body && JSON.stringify(body),
  });
}

export async function getCurrentUser(signal: AbortSignal): Promise<AuthUser | null> {
  try {
    return await (await request("/users/me", { signal })).json();
  } catch (error) {
    if (error instanceof AuthError && error.status === 401) return null;
    throw error;
  }
}

export async function register(input: RegistrationInput): Promise<PendingRegistration> {
  return (await submit("/auth/register", { ...input, displayName: input.displayName.trim(), email: normalizeEmail(input.email) })).json();
}

export async function login(input: LoginInput): Promise<AuthUser | PendingRegistration> {
  const response = await submit("/auth/login", { ...input, email: normalizeEmail(input.email) });
  if (response.status !== 202) sessionVersion++;
  return response.json();
}

export async function getPendingRegistration(signal: AbortSignal): Promise<PendingRegistration> {
  return (await request("/auth/registration", { signal, cache: "no-store" })).json();
}

export async function confirmRegistration(code: string): Promise<PendingRegistration> {
  return (await submit("/auth/registration/confirm", { code })).json();
}

export async function resendRegistrationCode(): Promise<PendingRegistration> {
  return (await submit("/auth/registration/resend")).json();
}

export async function correctRegistrationEmail(email: string): Promise<PendingRegistration> {
  return (await submit("/auth/registration/email", { email: normalizeEmail(email) })).json();
}

export function verificationFailure(error: unknown) {
  const auth = error instanceof AuthError ? error : undefined;
  const messages: Record<string, string> = {
    INCORRECT_CODE: "Codul este incorect. Verifică mesajul primit și încearcă din nou.",
    CODE_EXPIRED: "Codul a expirat. Solicită un cod nou.",
    CODE_ATTEMPTS_EXHAUSTED: "Ai epuizat cele 5 încercări. Solicită un cod nou.",
    REGISTRATION_EXPIRED: "Sesiunea de confirmare a expirat. Autentifică-te cu adresa de email și parola pentru a continua.",
  };
  return {
    message: auth?.code && messages[auth.code] ? messages[auth.code]
      : auth?.status === 401 ? messages.REGISTRATION_EXPIRED
      : auth?.status === 409 ? "Această adresă de email este deja înregistrată. Alege alta."
      : authErrorMessage(error),
    retryAfter: auth?.retryAfter ?? 0,
    attemptsRemaining: auth?.attemptsRemaining,
    sessionExpired: auth?.status === 401,
  };
}

export async function logout(): Promise<void> {
  await submit("/auth/logout");
  sessionVersion++;
}

export function registrationFieldErrors(error: unknown): RegistrationErrors {
  return error instanceof AuthError ? error.fieldErrors : {};
}

function formatRetryDuration(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  const parts: string[] = [];
  if (minutes) {
    parts.push(new Intl.NumberFormat("ro", { style: "unit", unit: "minute", unitDisplay: "long" }).format(minutes));
  }
  if (seconds) {
    parts.push(new Intl.NumberFormat("ro", { style: "unit", unit: "second", unitDisplay: "long" }).format(seconds));
  }
  return parts.join(" și ");
}

export function authErrorMessage(error: unknown) {
  if (error instanceof AuthError) {
    switch (error.status) {
      case 400:
        return "Verifică datele introduse și încearcă din nou.";
      case 401:
        return "Adresa de email sau parola este incorectă.";
      case 403:
        return "Cererea a fost respinsă. Încearcă din nou.";
      case 409:
        return "Nu am putut crea contul cu aceste date.";
      case 429:
        if (Number.isSafeInteger(error.retryAfter) && error.retryAfter > 0) {
          return `Prea multe încercări. Încearcă din nou peste ${formatRetryDuration(error.retryAfter)}.`;
        }
        return "Prea multe încercări. Așteaptă și încearcă din nou.";
    }
  }
  return "Serviciul de autentificare nu este disponibil. Încearcă din nou.";
}
