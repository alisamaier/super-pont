import { afterEach, describe, expect, it, vi } from "vitest";
import { footballApi } from "./football";
import { competitions, page, rounds } from "./football.test-data";

afterEach(() => vi.unstubAllGlobals());

describe("football REST client", () => {
  it("follows all pages and uses the configured API origin", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        Response.json({ ...page([competitions[0]]), totalPages: 2 }),
      )
      .mockResolvedValueOnce(
        Response.json({ ...page([competitions[1]]), page: 1, totalPages: 2 }),
      );
    vi.stubGlobal("fetch", fetcher);
    const signal = new AbortController().signal;
    await expect(footballApi.competitions(signal)).resolves.toEqual(
      competitions,
    );
    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      "https://api.example.test/api/competitions?page=0&size=100",
      expect.objectContaining({ signal }),
    );
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      "https://api.example.test/api/competitions?page=1&size=100",
      expect.objectContaining({ signal }),
    );
  });

  it("rejects an invalid match DTO instead of displaying unusable data", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          Response.json(page([{ id: "m1", roundId: rounds[0].id }])),
        ),
    );
    await expect(
      footballApi.matches(rounds[0].id, new AbortController().signal),
    ).rejects.toThrow("formatul așteptat");
  });

  it("handles unavailable services and a non-JSON response", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(new Response("unavailable", { status: 503 }))
        .mockResolvedValueOnce(new Response("<html>Not an API</html>")),
    );
    const signal = new AbortController().signal;
    await expect(footballApi.competitions(signal)).rejects.toThrow("HTTP 503");
    await expect(footballApi.competitions(signal)).rejects.toThrow(
      "formatul așteptat",
    );
  });
});
