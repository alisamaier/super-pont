import { describe, expect, it } from "vitest";
import {
  pendingDrafts,
  readStoredPredictions,
  validateEntries,
  validatePrediction,
  validateScore,
} from "./predictions";

describe("score validation", () => {
  it.each([0, 99, "0", "99", "01", "00", "00099", "000"])(
    "accepts and normalizes %j",
    (value) => {
      expect(validateScore(value)).toEqual({
        value: Number(value),
        error: null,
      });
    },
  );

  it.each([
    100,
    "100",
    -1,
    "-1",
    -0,
    "-0",
    1.5,
    "1.5",
    "1.0",
    "1e1",
    "1E1",
    "9e0",
    "+1",
    "abc",
    "1a",
    "",
    " ",
    " 1",
    "1 ",
    "1\n",
    "0x10",
    "١",
    null,
    undefined,
    NaN,
    Infinity,
    true,
    [],
    {},
  ])("rejects %j", (value) => {
    expect(validateScore(value).value).toBeNull();
    expect(validateScore(value).error).toBeTruthy();
  });

  it("requires both scores and accepts the entire independent range", () => {
    expect(validatePrediction({ home: "0", away: "99" }).prediction).toEqual({
      home: 0,
      away: 99,
    });
    expect(validatePrediction({ home: "99", away: "99" }).prediction).toEqual({
      home: 99,
      away: 99,
    });
    expect(validatePrediction({ home: "0", away: "" })).toMatchObject({
      prediction: null,
      errors: { home: null, away: "Introdu un scor." },
    });
  });
});

const ids = new Set(["m1", "m2", "m3"]);
describe("bulk and localStorage consistency", () => {
  it("returns normalized valid predictions and explicit per-match rejections", () => {
    expect(
      validateEntries(
        {
          m1: { home: "00", away: "99" },
          m2: { home: "100", away: "1" },
          m3: { home: "01", away: "" },
        },
        ids,
      ),
    ).toMatchObject({
      saved: { m1: { home: 0, away: 99 } },
      rejected: {
        m2: expect.stringContaining("99"),
        m3: expect.stringContaining("Oaspeți"),
      },
    });
  });

  it.each(["0", "99", "100", "01", "001", "1e1", "-1", "1.0", "text", ""])(
    "uses the same rules for drafts and stored score %j",
    (home) => {
      const entries = { m1: { home, away: "0" } };
      expect(readStoredPredictions(JSON.stringify(entries), ids)).toEqual(
        validateEntries(entries, ids),
      );
    },
  );

  it("accepts normalized numeric storage values and rejects invalid or unknown records", () => {
    const result = readStoredPredictions(
      JSON.stringify({
        m1: { home: 0, away: 99 },
        m2: { home: 100, away: 1 },
        m3: { home: 1.5, away: 0 },
        unknown: { home: 1, away: 0 },
      }),
      ids,
    );
    expect(result.saved).toEqual({ m1: { home: 0, away: 99 } });
    expect(Object.keys(result.rejected)).toEqual(["m2", "m3", "unknown"]);
  });

  it.each(["{bad", "null", "[]", '"invalid"'])(
    "reports invalid storage envelope %j",
    (raw) => {
      expect(() => readStoredPredictions(raw, ids)).toThrow();
    },
  );

  it("keeps invalid and partially entered drafts pending, excluding untouched and unchanged matches", () => {
    expect(
      pendingDrafts(
        {
          m1: { home: "01", away: "0" },
          m2: { home: "100", away: "" },
          m3: { home: "", away: "" },
        },
        { m1: { home: 1, away: 0 } },
      ),
    ).toEqual({ m2: { home: "100", away: "" } });
    expect(
      pendingDrafts(
        { m1: { home: "", away: "" } },
        { m1: { home: 1, away: 0 } },
      ),
    ).toHaveProperty("m1");
  });
});
