import { describe, expect, it } from "vitest";
import {
  portfolioMigrationEnvelopeInput,
  validatePortfolioMigrationParams,
  type PortfolioMigrationParams,
} from "../src/workflow-contract";

const ASSET = {
  photoId: "photo-1",
  sourceStorageKey: "photography/portfolio-dev/photo-1.jpg",
  contentType: "image/jpeg",
  expectedSha256: "a".repeat(64),
  expectedSizeBytes: 10,
};

describe("Portfolio migration Workflow", () => {
  it("preserves the existing reconciliation envelope by default", () => {
    const params = validatePortfolioMigrationParams(baseParams());

    expect(portfolioMigrationEnvelopeInput(params, "workflow-1")).toMatchObject({
      id: "migration:plan-1",
      aggregateType: "portfolio.migration.reconcile",
      payload: { planId: "plan-1", workflowInstanceId: "workflow-1" },
    });
  });

  it("creates an exact photography inventory envelope only at source epoch zero revision", () => {
    const params = validatePortfolioMigrationParams({
      ...baseParams(),
      jobType: "portfolio.photography.backfill",
      authorityEpoch: 1,
      expectedRevision: 0,
      assets: [ASSET],
    });

    expect(portfolioMigrationEnvelopeInput(params, "workflow-2")).toEqual({
      id: "photography:plan-1",
      aggregateType: "portfolio.photography.backfill",
      aggregateId: "portfolio-dev",
      authorityEpoch: 1,
      expectedRevision: 0,
      occurredAt: "2026-08-23T00:00:00Z",
      payload: { assets: [ASSET] },
    });
  });

  it("rejects duplicate ids unsafe keys mismatched authority and assets on reconciliation", () => {
    expect(() => validatePortfolioMigrationParams({ ...baseParams(), assets: [ASSET] })).toThrow();
    expect(() => validatePortfolioMigrationParams({
      ...baseParams(), jobType: "portfolio.photography.backfill", authorityEpoch: 1, expectedRevision: 0,
      assets: [ASSET, ASSET],
    })).toThrow();
    expect(() => validatePortfolioMigrationParams({
      ...baseParams(), jobType: "portfolio.photography.backfill", authorityEpoch: 2, expectedRevision: 0,
      assets: [ASSET],
    })).toThrow();
    expect(() => validatePortfolioMigrationParams({
      ...baseParams(), jobType: "portfolio.photography.backfill", authorityEpoch: 1, expectedRevision: 0,
      assets: [{ ...ASSET, sourceStorageKey: "firestore-export/shard" }],
    })).toThrow();
  });

  it("creates bounded UTC-day FinOps rollup backfill and reconciliation envelopes", () => {
    const dayStart = 1_799_971_200_000;
    const backfill = validatePortfolioMigrationParams({
      ...baseParams(),
      jobType: "portfolio.finops.rollups.v2.backfill",
      authorityEpoch: 1,
      from: dayStart,
      toExclusive: dayStart + 86_400_000,
      limit: 250,
      cursor: "v1:entry:cursor",
    });
    expect(portfolioMigrationEnvelopeInput(backfill, "workflow-finops")).toMatchObject({
      id: "finops-rollups-v2:backfill:plan-1",
      aggregateType: "portfolio.finops.rollups.v2.backfill",
      payload: {
        from: dayStart,
        toExclusive: dayStart + 86_400_000,
        limit: 250,
        cursor: "v1:entry:cursor",
      },
    });

    const reconciliation = validatePortfolioMigrationParams({
      ...baseParams(),
      jobType: "portfolio.finops.rollups.v2.reconcile",
      authorityEpoch: 1,
      expectedRevision: 0,
      from: dayStart,
      toExclusive: dayStart + 86_400_000,
    });
    expect(portfolioMigrationEnvelopeInput(reconciliation, "workflow-finops")).toMatchObject({
      id: "finops-rollups-v2:reconcile:plan-1",
      aggregateType: "portfolio.finops.rollups.v2.reconcile",
      payload: { from: dayStart, toExclusive: dayStart + 86_400_000 },
    });
  });

  it("rejects unaligned unbounded or cross-purpose FinOps rollup requests", () => {
    const dayStart = 1_799_971_200_000;
    const rollup = {
      ...baseParams(),
      jobType: "portfolio.finops.rollups.v2.backfill" as const,
      authorityEpoch: 1,
      from: dayStart,
      toExclusive: dayStart + 86_400_000,
    };
    expect(() => validatePortfolioMigrationParams({ ...rollup, from: dayStart + 1 })).toThrow();
    expect(() => validatePortfolioMigrationParams({ ...rollup, limit: 501 })).toThrow();
    expect(() => validatePortfolioMigrationParams({ ...rollup, assets: [ASSET] })).toThrow();
    expect(() => validatePortfolioMigrationParams({
      ...rollup,
      jobType: "portfolio.finops.rollups.v2.reconcile",
      limit: 100,
    })).toThrow();
  });
});

function baseParams(): PortfolioMigrationParams {
  return {
    planId: "plan-1",
    aggregateId: "portfolio-dev",
    authorityEpoch: 1,
    expectedRevision: 0,
    requestedAt: "2026-08-23T00:00:00Z",
  };
}
