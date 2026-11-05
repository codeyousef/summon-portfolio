import { describe, expect, it } from "vitest";
import { ContractError, deadLetter, hashLocation, parseSeenOperation } from "../src/contracts";

describe("Seen operation contracts", () => {
  it("accepts exact promotion and maintenance envelopes", () => {
    expect(parseSeenOperation({
      kind: "promotion",
      id: "promotion:12345678",
      aggregateId: "pkg:owner/name:1.0.0",
      expectedRevision: 7,
      requestedAt: "2026-08-21T00:00:00.000Z",
    })).toMatchObject({ kind: "promotion", expectedRevision: 7 });
    expect(parseSeenOperation({
      kind: "maintenance",
      id: "maintenance:12345678",
      command: "verify-root-chain",
      requestedAt: "2026-08-21T00:00:00.000Z",
    })).toMatchObject({ kind: "maintenance", command: "verify-root-chain" });
  });

  it("rejects extension fields and non-canonical timestamps", () => {
    expect(() => parseSeenOperation({
      kind: "maintenance",
      id: "maintenance:12345678",
      command: "verify-root-chain",
      requestedAt: "2026-08-21T00:00:00Z",
      authority: "unexpected",
    })).toThrow(ContractError);
  });

  it("routes a stable aggregate to one North American hint", () => {
    expect(hashLocation("package:one")).toBe(hashLocation("package:one"));
    expect(["enam", "wnam"]).toContain(hashLocation("package:two"));
  });

  it("sanitizes dead-letter reasons without dropping evidence", () => {
    const body = { unsafe: true };
    expect(deadLetter("message-1", body, "bad\u0000reason", new Date("2026-08-21T00:00:00.000Z"))).toEqual({
      messageId: "message-1",
      failedAt: "2026-08-21T00:00:00.000Z",
      reason: "badreason",
      body,
    });
  });
});
