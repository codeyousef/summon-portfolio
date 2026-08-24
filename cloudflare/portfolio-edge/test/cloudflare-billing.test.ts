import { afterEach, describe, expect, it, vi } from "vitest";
import { decimalMicros, normalizeSubscription, normalizeUsage, relayCloudflareBilling } from "../src/cloudflare-billing";
import type { CloudflareSubscription } from "../src/cloudflare-billing";
import type { Env, MutationEnvelope } from "../src/types";

afterEach(() => vi.unstubAllGlobals());

describe("Cloudflare billing normalization", () => {
  it("uses fixed-point decimal parsing including exponent notation and rounding", () => {
    expect(decimalMicros("20.00")).toBe(20_000_000);
    expect(decimalMicros(1e-6)).toBe(1);
    expect(decimalMicros("0.0000005")).toBe(1);
    expect(decimalMicros("-2.672773")).toBe(-2_672_773);
    expect(() => decimalMicros(Number.NaN)).toThrow("invalid decimal cost");
  });

  it("normalizes a zone subscription as accrued Portfolio spend", async () => {
    const envelope = await normalizeSubscription({
      id: "zone-pro",
      currency: "USD",
      current_period_start: "2026-08-01T00:00:00Z",
      current_period_end: "2026-09-01T00:00:00Z",
      frequency: "monthly",
      price: 20,
      state: "Paid",
      rate_plan: { id: "pro", public_name: "Pro Website" },
    }, { scope: "zone", zoneId: "a".repeat(32), zoneName: "yousef.codes" }, "dev");

    const entry = (envelope!.payload as { entry: Record<string, unknown> }).entry;
    expect(entry.project).toBe("portfolio");
    expect(entry.status).toBe("ACCRUED");
    expect(entry.usdMicros).toBe(20_000_000);
    expect(entry.sourceHash).toMatch(/^[a-f0-9]{64}$/);
  });

  it("keeps subscription envelopes stable when non-financial vendor state changes", async () => {
    const base: CloudflareSubscription = {
      id: "workers-paid",
      currency: "USD",
      current_period_start: "2026-08-01T00:00:00Z",
      current_period_end: "2026-09-01T00:00:00Z",
      frequency: "monthly",
      price: 5,
      state: "Active",
      rate_plan: { id: "workers-paid", public_name: "Workers Paid" },
    };
    const context = { scope: "account" as const };

    const active = await normalizeSubscription(base, context, "dev");
    const paid = await normalizeSubscription({ ...base, state: "Paid" }, context, "dev");

    expect(paid).toEqual(active);
  });

  it("keeps restricted billable usage accrued until invoice reconciliation", async () => {
    const envelope = await normalizeUsage({
      BilledCost: "0.125",
      BillingCurrency: "USD",
      BillingPeriodStart: "2026-08-01T00:00:00Z",
      x_ProductFamilyName: "Workers",
      x_ZoneName: "felidai.com",
      x_ZoneId: "b".repeat(32),
      PricingUnit: "requests",
    }, "dev");

    const entry = (envelope!.payload as { entry: Record<string, unknown> }).entry;
    expect(entry.project).toBe("samurai");
    expect(entry.status).toBe("ACCRUED");
    expect(entry.usdMicros).toBe(125_000);
  });

  it("records negative usage corrections as credits in the importing environment", async () => {
    const envelope = await normalizeUsage({
      BilledCost: "-0.125",
      BillingCurrency: "USD",
      BillingPeriodStart: "2026-08-01T00:00:00Z",
      x_ProductFamilyName: "Workers",
      x_ZoneName: "yousef.codes",
      x_ZoneId: "b".repeat(32),
      PricingUnit: "requests-correction",
    }, "dev");

    const entry = (envelope!.payload as { entry: Record<string, unknown> }).entry;
    expect(entry).toMatchObject({
      direction: "CREDIT",
      environment: "dev",
      project: "portfolio",
      status: "ACCRUED",
      usdMicros: 125_000,
    });
  });

  it("does not invent FX for a non-USD subscription", async () => {
    expect(await normalizeSubscription({
      id: "non-usd",
      currency: "SAR",
      current_period_start: "2026-08-01T00:00:00Z",
      price: "75",
    }, { scope: "account" }, "dev")).toBeNull();
  });

  it("bounds vendor dimensions to the authoritative Firestore schema", async () => {
    const envelope = await normalizeSubscription({
      id: "verbose-plan",
      currency: "USD",
      current_period_start: "2026-08-01T00:00:00Z",
      price: "1",
      rate_plan: { id: "sku", public_name: "x".repeat(200) },
    }, { scope: "account" }, "dev");

    const entry = (envelope!.payload as { entry: Record<string, unknown> }).entry;
    expect(entry.service).toBe("x".repeat(120));
  });

  it("publishes a partial coverage receipt when read endpoints are unavailable", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Response.json({
      success: false,
      errors: [{ message: "forbidden" }],
    }, { status: 403 })));
    const sent: MutationEnvelope[] = [];
    const env = {
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-01",
      CLOUDFLARE_ACCOUNT_ID: "a".repeat(32),
      CLOUDFLARE_BILLING_API_TOKEN: "secret",
      FINOPS_INGEST_QUEUE: {
        sendBatch: async () => {},
        send: async (body: MutationEnvelope) => { sent.push(body); },
      },
    } as unknown as Env;

    await relayCloudflareBilling(env);

    expect(sent).toHaveLength(1);
    expect(sent[0]!.aggregateType).toBe("finops.import.run");
    expect(sent[0]!.payload).toMatchObject({
      source: "cloudflare",
      status: "partial",
      sourceRecords: 0,
      entries: 0,
      failureCode: "provider_authorization",
    });
    expect(JSON.stringify(sent[0]!.payload)).not.toContain("forbidden");
  });

  it("rejects malformed vendor rows without losing valid rows or the import receipt", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/subscriptions")) {
        return Response.json({
          success: true,
          result: [
            { id: "bad id with spaces", currency: "USD", current_period_start: "2026-08-01T00:00:00Z", price: 99 },
            { id: "workers-paid", currency: "USD", current_period_start: "2026-08-01T00:00:00Z", price: 5 },
          ],
        });
      }
      return Response.json({ success: true, result: [] });
    }));
    const sent: MutationEnvelope[] = [];
    const env = {
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-01",
      CLOUDFLARE_ACCOUNT_ID: "a".repeat(32),
      CLOUDFLARE_BILLING_API_TOKEN: "secret",
      FINOPS_INGEST_QUEUE: {
        sendBatch: async (messages: Array<{ body: MutationEnvelope }>) => {
          sent.push(...messages.map(({ body }) => body));
        },
        send: async (body: MutationEnvelope) => { sent.push(body); },
      },
    } as unknown as Env;

    await relayCloudflareBilling(env);

    expect(sent).toHaveLength(2);
    expect(sent[0]!.aggregateType).toBe("finops.ingest");
    expect(sent[1]!.aggregateType).toBe("finops.import.run");
    expect(sent[1]!.payload).toMatchObject({
      status: "partial",
      sourceRecords: 2,
      entries: 1,
      failureCode: "record_validation",
    });
  });

  it("publishes a durable failed coverage run when queue delivery fails", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/accounts/") && url.endsWith("/subscriptions")) {
        return Response.json({
          success: true,
          result: [{ id: "workers-paid", currency: "USD", current_period_start: "2026-08-01T00:00:00Z", price: 5 }],
        });
      }
      return Response.json({ success: true, result: [] });
    }));
    const failedRuns: MutationEnvelope[] = [];
    const env = {
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-01",
      CLOUDFLARE_ACCOUNT_ID: "a".repeat(32),
      CLOUDFLARE_BILLING_API_TOKEN: "secret",
      FINOPS_INGEST_QUEUE: {
        sendBatch: vi.fn(async () => { throw new Error("simulated queue failure"); }),
        send: vi.fn(async (body: MutationEnvelope) => { failedRuns.push(body); }),
      },
    } as unknown as Env;

    await expect(relayCloudflareBilling(env)).rejects.toThrow("Cloudflare billing queue delivery failed");

    expect(failedRuns).toHaveLength(1);
    expect(failedRuns[0]).toMatchObject({
      aggregateType: "finops.import.run",
      aggregateId: "cloudflare",
      payload: expect.objectContaining({
        source: "cloudflare",
        status: "failed",
        sourceRecords: 1,
        entries: 1,
        failureCode: "queue_delivery",
      }),
    });
  });

  it("makes unsupported currencies and unavailable cost fields explicit coverage gaps", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/accounts/") && url.endsWith("/subscriptions")) {
        return Response.json({
          success: true,
          result: [{ id: "workers-paid", currency: "SAR", current_period_start: "2026-08-01T00:00:00Z", price: 20 }],
        });
      }
      if (url.includes("/billable/usage")) {
        return Response.json({ success: true, result: [{ BillingCurrency: "USD" }] });
      }
      return Response.json({ success: true, result: [] });
    }));
    const sent: MutationEnvelope[] = [];
    const env = {
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-01",
      CLOUDFLARE_ACCOUNT_ID: "a".repeat(32),
      CLOUDFLARE_BILLING_API_TOKEN: "secret",
      FINOPS_INGEST_QUEUE: {
        sendBatch: async () => {},
        send: async (body: MutationEnvelope) => { sent.push(body); },
      },
    } as unknown as Env;

    await relayCloudflareBilling(env);

    expect(sent).toHaveLength(1);
    expect(sent[0]!.payload).toMatchObject({
      status: "partial",
      sourceRecords: 2,
      entries: 0,
      failureCode: "unsupported_currency",
    });
  });
});
