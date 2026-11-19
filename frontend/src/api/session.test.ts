import { afterEach, expect, it, vi } from "vitest";
import { getCurrentUser, login, onSessionExpired, sessionFetch, SessionExpiredError } from "./auth";

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

it("does not expire the session for an anonymous session check or invalid login credentials", async () => {
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => String(input).endsWith("/auth/csrf")
    ? Response.json({ headerName: "X-CSRF-TOKEN", token: "test-token" }) : new Response(null, { status: 401 })));
  const expired = vi.fn();
  const unsubscribe = onSessionExpired(expired);
  try {
    await expect(getCurrentUser(new AbortController().signal)).resolves.toBeNull();
    await expect(login({ email: "fan@example.test", password: "wrong password" })).rejects.toThrow();
    expect(expired).not.toHaveBeenCalled();
  } finally { unsubscribe(); }
});

it("does not invalidate a new login when an older protected request eventually returns 401", async () => {
  let finishOldRequest!: (response: Response) => void;
  const oldResponse = new Promise<Response>(resolve => { finishOldRequest = resolve; });
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
    if (String(input).endsWith("/auth/csrf")) return Response.json({ headerName: "X-CSRF-TOKEN", token: "test-token" });
    if (String(input).endsWith("/auth/login")) return Response.json({ id: "new-session-user", displayName: "fan" });
    return oldResponse;
  }));
  const expired = vi.fn();
  const unsubscribe = onSessionExpired(expired);
  try {
    const request = sessionFetch("/leaderboard/superliga");
    await login({ email: "fan@example.test", password: "test password" });
    finishOldRequest(new Response(null, { status: 401 }));
    await expect(request).rejects.toBeInstanceOf(SessionExpiredError);
    expect(expired).not.toHaveBeenCalled();
  } finally { unsubscribe(); }
});

it("ignores session invalidation from an aborted request", async () => {
  let finishRequest!: (response: Response) => void;
  vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>(resolve => { finishRequest = resolve; })));
  const controller = new AbortController();
  const expired = vi.fn();
  const unsubscribe = onSessionExpired(expired);
  try {
    const request = sessionFetch("/leaderboard/superliga", { signal: controller.signal });
    controller.abort();
    finishRequest(new Response(null, { status: 401 }));
    await expect(request).rejects.toBeInstanceOf(SessionExpiredError);
    expect(expired).not.toHaveBeenCalled();
  } finally { unsubscribe(); }
});
