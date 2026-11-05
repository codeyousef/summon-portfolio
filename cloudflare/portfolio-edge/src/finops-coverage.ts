import type { Env, MutationEnvelope } from "./types";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export function finOpsCoverageStart(env: Pick<Env, "FINOPS_COVERAGE_START_DATE">): {
  date: string;
  epochMillis: number;
  epochSeconds: number;
} {
  const date = env.FINOPS_COVERAGE_START_DATE;
  const epochMillis = ISO_DATE.test(date) ? Date.parse(`${date}T00:00:00.000Z`) : Number.NaN;
  if (!Number.isSafeInteger(epochMillis) || epochMillis < 0 || new Date(epochMillis).toISOString().slice(0, 10) !== date) {
    throw new Error("invalid FinOps coverage start date");
  }
  return { date, epochMillis, epochSeconds: Math.floor(epochMillis / 1_000) };
}

export function requireEnvelopeWithinFinOpsCoverage(
  envelope: MutationEnvelope,
  env: Pick<Env, "FINOPS_COVERAGE_START_DATE">,
): void {
  if (!envelope.aggregateType.startsWith("finops.")) return;
  const payload = record(envelope.payload);
  if (envelope.aggregateType === "finops.import.run") {
    const fromDate = payload.fromDate;
    if (typeof fromDate !== "string" || fromDate < finOpsCoverageStart(env).date) {
      throw new Error("FinOps import predates configured coverage");
    }
    return;
  }
  const entry = record(payload.entry);
  const incurredAt = entry.incurredAt;
  if (!Number.isSafeInteger(incurredAt) || (incurredAt as number) < finOpsCoverageStart(env).epochMillis) {
    throw new Error("FinOps entry predates configured coverage");
  }
}

function record(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}
