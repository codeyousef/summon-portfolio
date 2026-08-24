import { describe, expect, it } from "vitest";
import {
  readAllocationBackfillResult,
  readAllocationProjectionReconciliation,
  shouldRunAllocationReconciliation,
} from "../src/reconciliation";

describe("FinOps allocation reconciliation", () => {
  it("accepts a bounded cursor page", async () => {
    await expect(readAllocationBackfillResult(Response.json({
      processed: 500,
      residualAllocations: 125,
      nextCursor: "1800000000000:entry-500",
    }))).resolves.toEqual({
      processed: 500,
      residualAllocations: 125,
      nextCursor: "1800000000000:entry-500",
    });
  });

  it("accepts only projection readiness backed by exact counts and hashes", async () => {
    const hash = "a".repeat(64);
    await expect(readAllocationProjectionReconciliation(Response.json({
      expectedCount: 3,
      actualCount: 3,
      missingCount: 0,
      unexpectedCount: 0,
      mismatchedCount: 0,
      invalidCount: 0,
      orphanedAllocationCount: 0,
      expectedHash: hash,
      actualHash: hash,
      ready: true,
    }))).resolves.toMatchObject({ ready: true, expectedCount: 3, actualCount: 3 });

    await expect(readAllocationProjectionReconciliation(Response.json({
      expectedCount: 3,
      actualCount: 2,
      missingCount: 1,
      unexpectedCount: 0,
      mismatchedCount: 0,
      invalidCount: 0,
      orphanedAllocationCount: 0,
      expectedHash: hash,
      actualHash: "b".repeat(64),
      ready: true,
    }))).rejects.toThrow("readiness contradicts its evidence");
  });

  it("runs the backfill only after the named dev database is authoritative", () => {
    expect(shouldRunAllocationReconciliation("dev", "target", "false")).toBe(true);
    expect(shouldRunAllocationReconciliation("dev", "source", "false")).toBe(false);
    expect(shouldRunAllocationReconciliation("dev", "dual", "false")).toBe(false);
    expect(shouldRunAllocationReconciliation("prod", "target", "false")).toBe(false);
    expect(shouldRunAllocationReconciliation("dev", "target", "true")).toBe(false);
  });

  it("rejects malformed or oversized reconciliation responses", async () => {
    await expect(readAllocationBackfillResult(Response.json({
      processed: 501,
      residualAllocations: 0,
      nextCursor: null,
    }))).rejects.toThrow("invalid processed count");
    await expect(readAllocationBackfillResult(Response.json({
      processed: 1,
      residualAllocations: 2,
      nextCursor: null,
    }))).rejects.toThrow("invalid residual count");
    await expect(readAllocationBackfillResult(new Response("x".repeat(16 * 1024 + 1), {
      headers: { "content-length": "1" },
    })))
      .rejects.toThrow("response is too large");
  });
});
