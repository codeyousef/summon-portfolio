import { describe, expect, it } from "vitest";
import { isAcknowledgedLegacyGcpRollupConflict } from "../src/finops-replay";

const legacyConflict = "code.yousef.firestore.migration.IdempotencyConflictException: Mutation id finops-rollup:gcp:abc123 was already used for a different mutation";

describe("legacy FinOps replay compatibility", () => {
  it("acknowledges only the known dev GCP rollup schema conflict", () => {
    expect(isAcknowledgedLegacyGcpRollupConflict("dev", "gcp", 400, legacyConflict)).toBe(true);
    expect(isAcknowledgedLegacyGcpRollupConflict("prod", "gcp", 400, legacyConflict)).toBe(false);
    expect(isAcknowledgedLegacyGcpRollupConflict("dev", "samurai", 400, legacyConflict)).toBe(false);
    expect(isAcknowledgedLegacyGcpRollupConflict("dev", "gcp", 409, legacyConflict)).toBe(false);
    expect(isAcknowledgedLegacyGcpRollupConflict("dev", "gcp", 400, "unrelated failure")).toBe(false);
  });
});
