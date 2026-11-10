import { apiUrl } from "../config";

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  role: "USER" | "ADMIN";
  createdAt: string;
}

interface LoginInput {
  username: string;
  password: string;
}

interface RegistrationInput extends LoginInput {
  email: string;
}

interface CsrfToken {
  headerName: string;
  token: string;
}

type RegistrationErrors = Partial<Record<"username" | "email", string>>;

class AuthError extends Error {
  constructor(
    readonly status: number,
    readonly fieldErrors: RegistrationErrors,
    readonly retryAfter: number,
  ) {
    super(`Authentication request failed (${status})`);
  }
}

async function request(path: string, init: RequestInit = {}) {
  const response = await fetch(`${apiUrl}/api${path}`, {
    ...init,
    credentials: "include",
  });
  if (!response.ok) {
    const problem = path === "/auth/register" && response.status === 409
      ? await response.json().catch(() => null)
      : null;
    const fieldErrors: RegistrationErrors = {};
    if (typeof problem?.errors?.username === "string") {
      fieldErrors.username = "Acest nume de utilizator este deja folosit.";
    }
    if (typeof problem?.errors?.email === "string") {
      fieldErrors.email = "Această adresă de e-mail este deja înregistrată.";
    }
    throw new AuthError(response.status, fieldErrors, Number(response.headers.get("Retry-After")));
  }
  return response;
}

async function submit(path: string, body?: LoginInput | RegistrationInput) {
  const csrf = await request("/auth/csrf");
  const { headerName, token }: CsrfToken = await csrf.json();
  const headers = new Headers({ [headerName]: token });
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

export async function register(input: RegistrationInput): Promise<AuthUser> {
  return (await submit("/auth/register", input)).json();
}

export async function login(input: LoginInput): Promise<AuthUser> {
  return (await submit("/auth/login", input)).json();
}

export async function logout(): Promise<void> {
  await submit("/auth/logout");
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
        return "Numele de utilizator sau parola este incorectă.";
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
