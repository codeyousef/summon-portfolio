import { afterEach, describe, expect, it, vi } from "vitest";
import { normalizeStripeBalanceTransaction, relayStripeBilling } from "../src/stripe-billing";
import type { Env, MutationEnvelope } from "../src/types";

afterEach(() => vi.restoreAllMocks());

function payload(envelope: MutationEnvelope) {
  return envelope.payload as {
    entry: Record<string, unknown>;
    allocations: Array<Record<string, unknown>>;
  };
}

describe("Stripe FinOps normalization", () => {
  it("separates revenue and processing fees and attributes opaque Samurai users", async () => {
    const envelopes = await normalizeStripeBalanceTransaction({
      id: "txn_charge_1",
      amount: 2500,
      fee: 103,
      currency: "usd",
      created: 1_800_000_000,
      type: "charge",
      reporting_category: "charge",
      source: { id: "ch_1", metadata: { samurai_user_id: "github:user-1", email: "must-not-leak@example.com" } },
    }, "dev");

    expect(envelopes).toHaveLength(2);
    expect(payload(envelopes![0]!).entry).toMatchObject({
      direction: "REVENUE",
      usdMicros: 25_000_000,
      project: "samurai",
      environment: "dev",
      vendor: "stripe",
    });
    expect(payload(envelopes![0]!).allocations).toEqual([
      expect.objectContaining({ userId: "github:user-1", allocatedUsdMicros: 25_000_000, method: "DIRECT" }),
    ]);
    expect(JSON.stringify(envelopes)).not.toContain("must-not-leak@example.com");
    expect(payload(envelopes![1]!).entry).toMatchObject({ direction: "FEE", usdMicros: 1_030_000 });
  });

  it("records revenue refunds and fee reversals without turning either into new spend", async () => {
    const envelopes = await normalizeStripeBalanceTransaction({
      id: "txn_refund_1",
      amount: -2500,
      fee: -103,
      currency: "usd",
      created: 1_800_000_000,
      type: "refund",
      reporting_category: "refund",
    }, "dev");

    expect(payload(envelopes![0]!).entry).toMatchObject({
      direction: "REFUND",
      usdMicros: 25_000_000,
      metadata: expect.objectContaining({ accountingClass: "revenue" }),
    });
    expect(payload(envelopes![1]!).entry).toMatchObject({ direction: "CREDIT", usdMicros: 1_030_000 });
  });

  it("records disputes as revenue reversals in the importing environment", async () => {
    const envelopes = await normalizeStripeBalanceTransaction({
      id: "txn_dispute_1",
      amount: -2500,
      fee: 0,
      currency: "usd",
      created: 1_800_000_000,
      type: "adjustment",
      reporting_category: "dispute",
    }, "dev");

    expect(payload(envelopes![0]!).entry).toMatchObject({
      direction: "REFUND",
      environment: "dev",
      usdMicros: 25_000_000,
      metadata: expect.objectContaining({ accountingClass: "revenue" }),
    });
  });

  it("keeps payouts inspectable as non-P-and-L adjustments", async () => {
    const envelopes = await normalizeStripeBalanceTransaction({
      id: "txn_payout_1",
      amount: -10000,
      fee: 0,
      currency: "usd",
      created: 1_800_000_000,
      type: "payout",
      reporting_category: "payout",
      source: { metadata: { samurai_user_id: "user-1" } },
    }, "dev");

    expect(payload(envelopes![0]!).entry).toMatchObject({
      direction: "ADJUSTMENT",
      service: "payouts",
      metadata: expect.objectContaining({ accountingClass: "cash_transfer" }),
    });
    expect(payload(envelopes![0]!).allocations).toEqual([]);
  });

  it("marks non-USD records as coverage gaps until authoritative FX is available", async () => {
    expect(await normalizeStripeBalanceTransaction({
      id: "txn_sar_1",
      amount: 100,
      fee: 3,
      currency: "sar",
      created: 1_800_000_000,
      type: "charge",
    }, "dev")).toBeNull();
  });
});

describe("Stripe FinOps relay", () => {
  it("paginates a bounded overlap window and publishes idempotent entries plus coverage", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({
        object: "list",
        has_more: true,
        data: [{ id: "txn_1", amount: 100, fee: 5, currency: "usd", created: 1_800_000_000, type: "charge" }],
      }))
      .mockResolvedValueOnce(Response.json({
        object: "list",
        has_more: false,
        data: [{ id: "txn_2", amount: -100, fee: -5, currency: "usd", created: 1_800_000_100, type: "refund" }],
      }));
    const batches: MutationEnvelope[][] = [];
    const singles: MutationEnvelope[] = [];
    const env = {
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-24",
      STRIPE_FINOPS_READ_KEY: `rk_test_${"a".repeat(24)}`,
      FINOPS_INGEST_QUEUE: {
        sendBatch: vi.fn(async (messages: Array<{ body: MutationEnvelope }>) => { batches.push(messages.map((message) => message.body)); }),
        send: vi.fn(async (body: MutationEnvelope) => { singles.push(body); }),
      },
    } as unknown as Env;

    await relayStripeBilling(env, new Date("2027-01-15T12:00:00Z"));

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(String(fetchMock.mock.calls[1]![0])).toContain("starting_after=txn_1");
    expect(batches.flat()).toHaveLength(4);
    expect(batches.flat().map(payload).map((item) => item.entry.environment)).toEqual([
      "dev", "dev", "dev", "dev",
    ]);
    expect(singles).toHaveLength(1);
    expect(singles[0]).toMatchObject({ aggregateType: "finops.import.run", aggregateId: "stripe" });
    expect(singles[0]!.payload).toMatchObject({ source: "stripe", sourceRecords: 2, entries: 4, status: "succeeded" });
    expect(singles[0]!.payload).not.toHaveProperty("failureCode");
  });

  it("is inert without the read-only restricted key", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    const env = { PORTFOLIO_ENV: "dev", FINOPS_COVERAGE_START_DATE: "2026-08-24" } as Env;

    await relayStripeBilling(env);

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("publishes a durable failed coverage run when the provider request fails", async () => {
    vi.spyOn(Date, "now").mockReturnValue(new Date("2027-01-15T12:00:01Z").getTime());
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(Response.json(
      { error: { type: "api_error" } },
      { status: 503 },
    ));
    const singles: MutationEnvelope[] = [];
    const env = {
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-24",
      STRIPE_FINOPS_READ_KEY: `rk_test_${"a".repeat(24)}`,
      FINOPS_INGEST_QUEUE: {
        sendBatch: vi.fn(),
        send: vi.fn(async (body: MutationEnvelope) => { singles.push(body); }),
      },
    } as unknown as Env;

    await expect(relayStripeBilling(env, new Date("2027-01-15T12:00:00Z"))).rejects.toThrow(
      "Stripe balance ledger request failed",
    );

    expect(env.FINOPS_INGEST_QUEUE.sendBatch).not.toHaveBeenCalled();
    expect(singles).toHaveLength(1);
    expect(singles[0]).toMatchObject({
      aggregateType: "finops.import.run",
      aggregateId: "stripe",
      payload: expect.objectContaining({
        source: "stripe",
        status: "failed",
        sourceRecords: 0,
        entries: 0,
        completedAt: new Date("2027-01-15T12:00:01Z").getTime(),
        failureCode: "provider_api",
      }),
    });
    expect(JSON.stringify(singles[0]!.payload)).not.toContain("api_error");
  });

  it("publishes a reasoned partial receipt when authoritative FX is unavailable", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(Response.json({
      object: "list",
      has_more: false,
      data: [{ id: "txn_sar_1", amount: 100, fee: 3, currency: "sar", created: 1_800_000_000, type: "charge" }],
    }));
    const singles: MutationEnvelope[] = [];
    const env = {
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-24",
      STRIPE_FINOPS_READ_KEY: `rk_test_${"a".repeat(24)}`,
      FINOPS_INGEST_QUEUE: {
        sendBatch: vi.fn(),
        send: vi.fn(async (body: MutationEnvelope) => { singles.push(body); }),
      },
    } as unknown as Env;

    await relayStripeBilling(env, new Date("2027-01-15T12:00:00Z"));

    expect(env.FINOPS_INGEST_QUEUE.sendBatch).not.toHaveBeenCalled();
    expect(singles[0]!.payload).toMatchObject({
      source: "stripe",
      status: "partial",
      sourceRecords: 1,
      entries: 0,
      failureCode: "unsupported_currency",
    });
  });

  it("records queue delivery as the failed receipt reason", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(Response.json({
      object: "list",
      has_more: false,
      data: [{ id: "txn_1", amount: 100, fee: 0, currency: "usd", created: 1_800_000_000, type: "charge" }],
    }));
    const singles: MutationEnvelope[] = [];
    const env = {
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-24",
      STRIPE_FINOPS_READ_KEY: `rk_test_${"a".repeat(24)}`,
      FINOPS_INGEST_QUEUE: {
        sendBatch: vi.fn(async () => { throw new Error("queue body must not be persisted"); }),
        send: vi.fn(async (body: MutationEnvelope) => { singles.push(body); }),
      },
    } as unknown as Env;

    await expect(relayStripeBilling(env, new Date("2027-01-15T12:00:00Z"))).rejects.toThrow(
      "Stripe billing queue delivery failed",
    );

    expect(singles[0]!.payload).toMatchObject({ status: "failed", failureCode: "queue_delivery" });
    expect(JSON.stringify(singles[0]!.payload)).not.toContain("queue body must not be persisted");
  });
});
