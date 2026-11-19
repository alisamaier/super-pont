import { csrfHeaders, sessionFetch } from "./auth";
import type { AuthUser } from "./auth";
import { isRecord } from "./page";

export interface AccountSettings {
  hidePredictionsUntilKickoff: boolean;
}

async function request(init: RequestInit): Promise<AccountSettings> {
  const response = await sessionFetch("/users/me/settings", { ...init, cache: "no-store" });
  if (response.status === 401 || response.status === 403) {
    throw new Error("Sesiunea a expirat sau cererea a fost respinsă. Intră din nou în cont și încearcă din nou.");
  }
  if (!response.ok) throw new Error("Setările nu au putut fi sincronizate. Încearcă din nou.");
  const data: unknown = await response.json();
  if (!isRecord(data) || typeof data.hidePredictionsUntilKickoff !== "boolean") {
    throw new Error("Răspunsul pentru setările contului nu este valid. Încearcă din nou.");
  }
  return { hidePredictionsUntilKickoff: data.hidePredictionsUntilKickoff };
}

export function getAccountSettings(signal: AbortSignal) {
  return request({ signal });
}

export async function saveAccountSettings(settings: AccountSettings, signal: AbortSignal) {
  const headers = await csrfHeaders(signal);
  headers.set("Content-Type", "application/json");
  return request({ method: "PUT", headers, body: JSON.stringify(settings), signal });
}

async function putAccount(path: string, body: object, signal: AbortSignal) {
  const headers = await csrfHeaders(signal);
  headers.set("Content-Type", "application/json");
  const response = await sessionFetch(path, { method: "PUT", headers, body: JSON.stringify(body), signal, cache: "no-store" });
  if (response.ok) return response;
  if (response.status === 400) {
    const problem = await response.json().catch(() => null);
    if (problem?.detail === "Current password is incorrect") throw new Error("Parola actuală este incorectă.");
    throw new Error(path.endsWith("/profile")
      ? "Introdu un nume de cel mult 64 de caractere."
      : "Parola nouă trebuie să aibă cel puțin 8 caractere și cel mult 72 de octeți.");
  }
  if (response.status === 403) throw new Error("Cererea a fost respinsă. Reîncarcă pagina și încearcă din nou.");
  throw new Error("Modificarea nu a putut fi salvată. Încearcă din nou.");
}

export async function saveDisplayName(displayName: string, signal: AbortSignal): Promise<AuthUser> {
  return (await putAccount("/users/me/profile", { displayName }, signal)).json();
}

export async function changePassword(currentPassword: string, newPassword: string, signal: AbortSignal): Promise<void> {
  await putAccount("/users/me/password", { currentPassword, newPassword }, signal);
}
