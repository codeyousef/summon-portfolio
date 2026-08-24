import { describe, expect, it } from "vitest";
import { healthResponseBody, queueHealthResponseBody } from "../src/health";

function env(writeMode: "source" | "dual" | "target") {
  return {
    PORTFOLIO_ENV: "dev" as const,
    FIRESTORE_DATABASE_ID: "portfolio-me-dev",
    FIRESTORE_WRITE_MODE: writeMode,
    FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY: "false" as const,
  };
}

describe("health database authority", () => {
  it("distinguishes the configured migration target from the active source authority", async () => {
    expect(healthResponseBody(env("source"), "/health/ready")).toMatchObject({
      firestoreDatabase: "portfolio-me-dev",
      firestoreConfiguredTargetDatabase: "portfolio-me-dev",
      firestoreAuthorityDatabase: "(default)",
      firestoreWriteMode: "source",
      finOpsAllocationEntryProjectionsReady: false,
    });
  });

  it("reports queue and DLQ metrics without exposing messages", () => {
    expect(queueHealthResponseBody(
      { backlogCount: 12, backlogBytes: 345, oldestMessageTimestamp: new Date("2026-08-22T05:00:00Z") },
      { backlogCount: 2, backlogBytes: 20, oldestMessageTimestamp: new Date("2026-08-23T05:00:00Z") },
      { backlogCount: 0, backlogBytes: 0 },
      { backlogCount: 0, backlogBytes: 0 },
    )).toEqual({
      status: "ok",
      work: { backlogCount: 12, backlogBytes: 345, oldestMessageTimestamp: "2026-08-22T05:00:00.000Z" },
      finOpsWork: { backlogCount: 2, backlogBytes: 20, oldestMessageTimestamp: "2026-08-23T05:00:00.000Z" },
      deadLetter: { backlogCount: 0, backlogBytes: 0, oldestMessageTimestamp: null },
      parking: { backlogCount: 0, backlogBytes: 0, oldestMessageTimestamp: null },
    });
  });

  it("reports the named database after authority moves to the target", async () => {
    expect(healthResponseBody(env("target"), "/health")).toMatchObject({
      firestoreAuthorityDatabase: "portfolio-me-dev",
      firestoreWriteMode: "target",
    });
  });
});
