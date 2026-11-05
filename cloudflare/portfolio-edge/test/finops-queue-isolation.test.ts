import { describe, expect, it } from "vitest";
import { deadLetterDestination, enforceFinOpsCoverageForQueue } from "../src/queue-isolation";
import type { DeadLetterReceipt, Env } from "../src/types";

function queue(name: string): Queue<DeadLetterReceipt> & { name: string } {
  return { name } as unknown as Queue<DeadLetterReceipt> & { name: string };
}

describe("future-only FinOps queue isolation", () => {
  it("enforces the coverage cutoff only on the dedicated FinOps queue", () => {
    const env = { FINOPS_INGEST_QUEUE_NAME: "yousef-finops-ingest-dev" };
    expect(enforceFinOpsCoverageForQueue("yousef-finops-ingest-dev", env)).toBe(true);
    expect(enforceFinOpsCoverageForQueue("yousef-portfolio-async-dev", env)).toBe(false);
    expect(enforceFinOpsCoverageForQueue("yousef-portfolio-async-dlq-dev", env)).toBe(false);
  });

  it("never sends future FinOps failures to the legacy shared DLQ", () => {
    const env = {
      FINOPS_INGEST_DLQ: queue("finops"),
      PORTFOLIO_ASYNC_DLQ: queue("legacy"),
      PORTFOLIO_ASYNC_PARKING: queue("parking"),
    } as unknown as Pick<Env, "FINOPS_INGEST_DLQ" | "PORTFOLIO_ASYNC_DLQ" | "PORTFOLIO_ASYNC_PARKING">;

    expect(deadLetterDestination(env, { parking: false, finOps: true })).toBe(env.FINOPS_INGEST_DLQ);
    expect(deadLetterDestination(env, { parking: false, finOps: false })).toBe(env.PORTFOLIO_ASYNC_DLQ);
    expect(deadLetterDestination(env, { parking: true, finOps: true })).toBe(env.PORTFOLIO_ASYNC_PARKING);
  });
});
