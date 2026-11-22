import { csrfHeaders, sessionFetch } from "./auth";

export const invalidResetLink = "Linkul este invalid sau a expirat. Solicită un link nou.";
export class InvalidResetLinkError extends Error {
  constructor() { super(invalidResetLink); }
}

async function submit(operation: string, body: object, signal: AbortSignal) {
  const headers = await csrfHeaders(signal);
  headers.set("Content-Type", "application/json");
  const response = await sessionFetch(`/auth/password-reset/${operation}`, {
    method: "POST", body: JSON.stringify(body), headers, signal, cache: "no-store",
  });
  if (response.ok) return response;
  const problem = await response.json().catch(() => null);
  if (response.status === 400 && (problem?.detail === invalidResetLink || problem?.errors?.token)) {
    throw new InvalidResetLinkError();
  }
  if (response.status === 400 && (problem?.errors?.newPassword || problem?.detail === "Password must be at most 72 UTF-8 bytes")) {
    throw new Error("Parola trebuie să aibă cel puțin 8 caractere și cel mult 72 de octeți.");
  }
  if (response.status === 429) {
    const seconds = Number(response.headers.get("Retry-After"));
    throw new Error(Number.isSafeInteger(seconds) && seconds > 0
      ? `Prea multe încercări. Încearcă din nou peste ${new Intl.NumberFormat("ro", { style: "unit", unit: "minute", unitDisplay: "long" }).format(Math.ceil(seconds / 60))}.`
      : "Prea multe încercări. Așteaptă și încearcă din nou.");
  }
  if (response.status === 400 && operation === "request") throw new Error("Introdu o adresă de email validă.");
  throw new Error("Cererea nu a putut fi trimisă. Încearcă din nou.");
}

export async function requestPasswordReset(email: string, signal: AbortSignal): Promise<string> {
  const response = await submit("request", { email: email.trim().toLowerCase() }, signal);
  const data = await response.json();
  if (typeof data?.message !== "string") throw new Error("Cererea nu a putut fi confirmată. Încearcă din nou.");
  return data.message;
}

export async function validatePasswordReset(token: string, signal: AbortSignal): Promise<void> {
  await submit("validate", { token }, signal);
}

export async function resetPassword(token: string, newPassword: string, signal: AbortSignal): Promise<void> {
  await submit("confirm", { token, newPassword }, signal);
}
