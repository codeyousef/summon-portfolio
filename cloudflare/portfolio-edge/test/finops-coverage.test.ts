import { describe, expect, it } from "vitest";
import { makeMutationEnvelope } from "../src/contracts";
import { finOpsCoverageStart, requireEnvelopeWithinFinOpsCoverage } from "../src/finops-coverage";

const env = { FINOPS_COVERAGE_START_DATE: "2026-08-24" } as const;

describe("FinOps future-only coverage", () => {
  it("parses an exact UTC-day cutoff and fails closed on malformed dates", () => {
    expect(finOpsCoverageStart(env)).toEqual({
      date: "2026-08-24",
      epochMillis: Date.parse("2026-08-24T00:00:00.000Z"),
      epochSeconds: Date.parse("2026-08-24T00:00:00.000Z") / 1_000,
    });
    expect(() => finOpsCoverageStart({ FINOPS_COVERAGE_START_DATE: "2026-02-30" })).toThrow();
  });

  it("rejects pre-cutoff entries and imports but accepts the exact boundary", async () => {
    const boundary = finOpsCoverageStart(env).epochMillis;
    const entry = await makeMutationEnvelope({
      id: "finops:coverage-entry",
      aggregateType: "finops.ingest",
      aggregateId: "gcp",
      expectedRevision: 0,
      authorityEpoch: 1,
      occurredAt: "2026-08-24T00:00:00.000Z",
      payload: { entry: { incurredAt: boundary } },
    });
    expect(() => requireEnvelopeWithinFinOpsCoverage(entry, env)).not.toThrow();
    expect(() => requireEnvelopeWithinFinOpsCoverage({
      ...entry,
      payload: { entry: { incurredAt: boundary - 1 } },
    }, env)).toThrow("predates");

    const importRun = { ...entry, aggregateType: "finops.import.run", payload: { fromDate: "2026-08-23" } };
    expect(() => requireEnvelopeWithinFinOpsCoverage(importRun, env)).toThrow("predates");
  });
});
