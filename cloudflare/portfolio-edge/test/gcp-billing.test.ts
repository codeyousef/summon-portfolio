import { afterEach, describe, expect, it, vi } from "vitest";
import {
  billingExportCoverage,
  GCP_BILLING_SOURCE_ROW_LIMIT,
  GCP_BILLING_SQL,
  normalizeGcpBillingLine,
  relayGcpBilling,
} from "../src/gcp-billing";
import type { BillingLine } from "../src/gcp-billing";
import type { Env, MutationEnvelope } from "../src/types";

afterEach(() => vi.restoreAllMocks());

const fields = [
  "source_record_id", "billing_account_id", "invoice_month", "project_id", "service", "sku",
  "incurred_at", "export_time", "currency", "signed_original_micros", "signed_usd_micros", "line_kind", "cost_type",
] as const satisfies readonly (keyof BillingLine)[];

function line(overrides: Partial<BillingLine> = {}): BillingLine {
  return {
    source_record_id: "a".repeat(64),
    billing_account_id: "ABCDEF-123456-ABCDEF",
    invoice_month: "202701",
    project_id: "portfolio-prod",
    service: "Firestore",
    sku: "Document reads",
    incurred_at: "2027-01-15T08:00:00.000Z",
    export_time: "2027-01-15T09:00:00.000Z",
    currency: "USD",
    signed_original_micros: "10000000",
    signed_usd_micros: "10000000",
    line_kind: "cost",
    cost_type: "regular",
    ...overrides,
  };
}

function bqRow(value: BillingLine) {
  return { f: fields.map((name) => ({ v: value[name] })) };
}

function accessAssertion(nowSeconds: number): string {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `${encode({ alg: "RS256", typ: "JWT" })}.${encode({
    iss: "https://felidai.cloudflareaccess.com",
    aud: ["b".repeat(64)],
    common_name: `${"a".repeat(32)}.access`,
    sub: "",
    type: "app",
    iat: nowSeconds - 30,
    exp: nowSeconds + 300,
  })}.signature`;
}

function configuredEnv(queue: Env["FINOPS_INGEST_QUEUE"]): Env {
  return {
    PORTFOLIO_ENV: "dev",
    PORTFOLIO_CANONICAL_ORIGIN: "https://dev.yousef.codes",
    EDGE_ORIGIN_TOKEN: "edge-origin-token-that-is-long-enough-123",
    FINOPS_COVERAGE_START_DATE: "2026-08-24",
    FINOPS_INGEST_QUEUE: queue,
    GCP_FINOPS_BILLING_PROJECT_ID: "felidai-dev",
    GCP_FINOPS_BILLING_DATASET: "billing_export",
    GCP_FINOPS_BILLING_TABLE: "gcp_billing_export_v1_ABCDEF_123456_ABCDEF",
    GCP_FINOPS_BILLING_LOCATION: "US",
    GCP_FINOPS_MAX_BYTES_BILLED: "50000000",
    GCP_WIF_ACCESS_BROKER_URL: "https://dev.yousef.codes/internal/gcp-wif/assertion",
    GCP_WIF_ACCESS_CLIENT_ID: `${"a".repeat(32)}.access`,
    GCP_WIF_ACCESS_CLIENT_SECRET: "access-secret-that-is-long-enough-123",
    GCP_WIF_ACCESS_ISSUER: "https://felidai.cloudflareaccess.com",
    GCP_WIF_ACCESS_AUDIENCE: "b".repeat(64),
    GCP_WIF_PROVIDER_AUDIENCE: "//iam.googleapis.com/projects/123456789/locations/global/workloadIdentityPools/cloudflare-dev/providers/access-dev",
    GCP_WIF_SERVICE_ACCOUNT: "finops-reader@felidai-dev.iam.gserviceaccount.com",
  } as Env;
}

describe("GCP FinOps billing relay", () => {
  it("keeps the bounded source window above the measured dev export volume", () => {
    expect(GCP_BILLING_SOURCE_ROW_LIMIT).toBe(100_000);
    expect(GCP_BILLING_SOURCE_ROW_LIMIT).toBeLessThanOrEqual(100 * 1_000);
  });

  it("keeps append-only corrections exact and isolated to dev", async () => {
    const cost = await normalizeGcpBillingLine(line(), "dev");
    const credit = await normalizeGcpBillingLine(line({
      source_record_id: "c".repeat(64),
      signed_original_micros: "-2000000",
      signed_usd_micros: "-2000000",
      line_kind: "credit",
      cost_type: "credit",
    }), "dev");
    const entries = [cost, credit].map((envelope) => (envelope.payload as { entry: Record<string, unknown> }).entry);

    expect(entries[0]).toMatchObject({ direction: "EXPENSE", environment: "dev", usdMicros: 10_000_000 });
    expect(entries[1]).toMatchObject({ direction: "CREDIT", environment: "dev", usdMicros: 2_000_000 });
    expect(cost.id).not.toBe(credit.id);
    expect(entries[0]!.sourceHash).toMatch(/^[a-f0-9]{64}$/);
  });

  it("runs one bounded parameterized query and publishes entries plus durable coverage", async () => {
    const now = new Date("2027-01-15T12:00:00Z");
    const nowSeconds = Math.floor(now.getTime() / 1_000);
    vi.spyOn(Date, "now").mockReturnValue(now.getTime());
    const batches: MutationEnvelope[][] = [];
    const singles: MutationEnvelope[] = [];
    const queue = {
      sendBatch: vi.fn(async (messages: Array<{ body: MutationEnvelope }>) => batches.push(messages.map(({ body }) => body))),
      send: vi.fn(async (body: MutationEnvelope) => singles.push(body)),
    } as unknown as Env["FINOPS_INGEST_QUEUE"];
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: accessAssertion(nowSeconds) }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "google-token", expireTime: "2027-01-15T12:15:00Z" }))
      .mockResolvedValueOnce(Response.json({
        jobComplete: true,
        jobReference: { projectId: "felidai-dev", jobId: "job-1", location: "US" },
        totalBytesProcessed: "12345",
        schema: { fields: fields.map((name) => ({ name })) },
        rows: [
          bqRow(line()),
          bqRow(line({ source_record_id: "c".repeat(64), signed_original_micros: "-2000000", signed_usd_micros: "-2000000", line_kind: "credit", cost_type: "credit" })),
        ],
      }));

    await relayGcpBilling(configuredEnv(queue), now);

    expect(fetchMock).toHaveBeenCalledTimes(4);
    const queryRequest = JSON.parse(String(fetchMock.mock.calls[3]![1]?.body)) as Record<string, unknown>;
    expect(queryRequest).toMatchObject({
      useLegacySql: false,
      useQueryCache: false,
      maximumBytesBilled: "50000000",
      maxResults: 1_000,
      location: "US",
    });
    expect(String(queryRequest.query)).toContain("_PARTITIONDATE >= DATE_SUB(@fromDate, INTERVAL 1 DAY)");
    expect(String(queryRequest.query)).not.toContain("INTERVAL 30 DAY");
    expect(String(queryRequest.query)).toContain("DATE(usage_start_time) < @toDateExclusive");
    expect(queryRequest.queryParameters).toEqual([
      { name: "fromDate", parameterType: { type: "DATE" }, parameterValue: { value: "2026-12-12" } },
      { name: "toDateExclusive", parameterType: { type: "DATE" }, parameterValue: { value: "2027-01-16" } },
    ]);
    expect(batches.flat()).toHaveLength(2);
    expect(batches.flat().map((envelope) => (envelope.payload as { entry: { environment: string } }).entry.environment)).toEqual(["dev", "dev"]);
    expect(singles).toHaveLength(1);
    expect(singles[0]).toMatchObject({ aggregateType: "finops.import.run", aggregateId: "gcp" });
    expect(singles[0]!.payload).toMatchObject({ source: "gcp", sourceRecords: 2, entries: 2, status: "succeeded", completedAt: now.getTime() });
    expect(singles[0]!.payload).not.toHaveProperty("failureCode");
  });

  it("is inert without billing configuration and rejects partial settings", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    await relayGcpBilling({ PORTFOLIO_ENV: "dev", FINOPS_COVERAGE_START_DATE: "2026-08-24" } as Env);
    expect(fetchMock).not.toHaveBeenCalled();

    await expect(relayGcpBilling({
      PORTFOLIO_ENV: "dev",
      FINOPS_COVERAGE_START_DATE: "2026-08-24",
      GCP_FINOPS_BILLING_PROJECT_ID: "felidai-dev",
    } as Env)).rejects.toThrow("FinOps connector failed");
  });

  it("marks a stale or empty billing export as partial coverage instead of zero spend", async () => {
    const now = new Date("2027-01-15T12:00:00Z");
    const nowSeconds = Math.floor(now.getTime() / 1_000);
    vi.spyOn(Date, "now").mockReturnValue(now.getTime());
    const singles: MutationEnvelope[] = [];
    const queue = {
      sendBatch: vi.fn(async () => undefined),
      send: vi.fn(async (body: MutationEnvelope) => singles.push(body)),
    } as unknown as Env["FINOPS_INGEST_QUEUE"];
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: accessAssertion(nowSeconds) }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "google-token", expireTime: "2027-01-15T12:15:00Z" }))
      .mockResolvedValueOnce(Response.json({
        jobComplete: true,
        jobReference: { projectId: "felidai-dev", jobId: "job-stale", location: "US" },
        totalBytesProcessed: "12345",
        schema: { fields: fields.map((name) => ({ name })) },
        rows: [bqRow(line({ export_time: "2027-01-01T09:00:00.000Z" }))],
      }));

    await relayGcpBilling(configuredEnv(queue), now);

    expect(singles).toHaveLength(1);
    expect(singles[0]!.payload).toMatchObject({
      source: "gcp",
      sourceRecords: 1,
      entries: 1,
      status: "partial",
    });
    expect(billingExportCoverage([], "2027-01-16")).toEqual({
      complete: false,
      latestExportAt: null,
      minimumExpectedAt: Date.parse("2027-01-13T00:00:00.000Z"),
    });
  });

  it("anchors export freshness to the billing window rather than wall-clock jitter", () => {
    const rows = [line({ export_time: "2027-01-13T00:00:00.000Z" })];
    expect(billingExportCoverage(rows, "2027-01-16")).toEqual({
      complete: true,
      latestExportAt: Date.parse("2027-01-13T00:00:00.000Z"),
      minimumExpectedAt: Date.parse("2027-01-13T00:00:00.000Z"),
    });
  });

  it("polls incomplete jobs and follows bounded page tokens", async () => {
    const now = new Date("2027-01-15T12:00:00Z");
    const nowSeconds = Math.floor(now.getTime() / 1_000);
    vi.spyOn(Date, "now").mockReturnValue(now.getTime());
    const batches: MutationEnvelope[][] = [];
    const queue = {
      sendBatch: vi.fn(async (messages: Array<{ body: MutationEnvelope }>) => batches.push(messages.map(({ body }) => body))),
      send: vi.fn(async () => undefined),
    } as unknown as Env["FINOPS_INGEST_QUEUE"];
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: accessAssertion(nowSeconds) }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "google-token", expireTime: "2027-01-15T12:15:00Z" }))
      .mockResolvedValueOnce(Response.json({
        jobComplete: false,
        jobReference: { projectId: "felidai-dev", jobId: "job-paged", location: "US" },
      }))
      .mockResolvedValueOnce(Response.json({
        jobComplete: true,
        totalBytesProcessed: "1000",
        schema: { fields: fields.map((name) => ({ name })) },
        rows: [],
        pageToken: "next-page",
      }))
      .mockResolvedValueOnce(Response.json({
        jobComplete: true,
        totalBytesProcessed: "1000",
        schema: { fields: fields.map((name) => ({ name })) },
        rows: [bqRow(line())],
      }));

    await relayGcpBilling(configuredEnv(queue), now);

    expect(fetchMock).toHaveBeenCalledTimes(6);
    expect(String(fetchMock.mock.calls[4]![0])).toContain("/queries/job-paged");
    expect(String(fetchMock.mock.calls[4]![0])).not.toContain("pageToken=");
    expect(String(fetchMock.mock.calls[5]![0])).toContain("pageToken=next-page");
    expect(batches.flat()).toHaveLength(1);
  });

  it("rejects a query response that exceeds the configured byte cap", async () => {
    const now = new Date("2027-01-15T12:00:00Z");
    const nowSeconds = Math.floor(now.getTime() / 1_000);
    vi.spyOn(Date, "now").mockReturnValue(now.getTime());
    const queue = { sendBatch: vi.fn(), send: vi.fn() } as unknown as Env["FINOPS_INGEST_QUEUE"];
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: accessAssertion(nowSeconds) }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "google-token", expireTime: "2027-01-15T12:15:00Z" }))
      .mockResolvedValueOnce(Response.json({
        jobComplete: true,
        jobReference: { projectId: "felidai-dev", jobId: "job-too-large", location: "US" },
        totalBytesProcessed: "50000001",
        schema: { fields: fields.map((name) => ({ name })) },
        rows: [],
      }));

    await expect(relayGcpBilling(configuredEnv(queue), now)).rejects.toThrow(
      "BigQuery processed bytes exceeded its configured cap",
    );
    expect(queue.sendBatch).not.toHaveBeenCalled();
    expect(queue.send).toHaveBeenCalledTimes(1);
    expect((queue.send as ReturnType<typeof vi.fn>).mock.calls[0]![0]).toMatchObject({
      aggregateType: "finops.import.run",
      payload: expect.objectContaining({
        source: "gcp",
        status: "failed",
        sourceRecords: 0,
        entries: 0,
        failureCode: "query_limit",
      }),
    });
  });

  it("stops an oversized streamed BigQuery response despite a misleading length", async () => {
    const now = new Date("2027-01-15T12:00:00Z");
    const nowSeconds = Math.floor(now.getTime() / 1_000);
    vi.spyOn(Date, "now").mockReturnValue(now.getTime());
    const singles: MutationEnvelope[] = [];
    const queue = {
      sendBatch: vi.fn(),
      send: vi.fn(async (body: MutationEnvelope) => singles.push(body)),
    } as unknown as Env["FINOPS_INGEST_QUEUE"];
    const largeChunk = new Uint8Array(5 * 1024 * 1024 + 1).fill(0x20);
    const oversized = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(largeChunk);
        controller.enqueue(largeChunk);
        controller.close();
      },
    });
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: accessAssertion(nowSeconds) }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "google-token", expireTime: "2027-01-15T12:15:00Z" }))
      .mockResolvedValueOnce(new Response(oversized, {
        headers: { "content-length": "1", "content-type": "application/json" },
      }));

    await expect(relayGcpBilling(configuredEnv(queue), now)).rejects.toThrow("BigQuery response exceeds 10 MiB");
    expect(queue.sendBatch).not.toHaveBeenCalled();
    expect(singles).toHaveLength(1);
    expect(singles[0]!.payload).toMatchObject({ status: "failed", failureCode: "query_limit" });
  });

  it("records a redacted workload identity failure without provider error text", async () => {
    const now = new Date("2027-01-15T12:00:00Z");
    vi.spyOn(Date, "now").mockReturnValue(now.getTime());
    const singles: MutationEnvelope[] = [];
    const queue = {
      sendBatch: vi.fn(),
      send: vi.fn(async (body: MutationEnvelope) => singles.push(body)),
    } as unknown as Env["FINOPS_INGEST_QUEUE"];
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      Response.json({ error: "credential details that must not enter Firestore" }, { status: 403 }),
    );

    await expect(relayGcpBilling(configuredEnv(queue), now)).rejects.toThrow("FinOps connector failed");

    expect(singles).toHaveLength(1);
    expect(singles[0]!.payload).toMatchObject({
      source: "gcp",
      status: "failed",
      failureCode: "workload_identity",
    });
    expect(JSON.stringify(singles[0]!.payload)).not.toContain("credential details");
  });

  it("keeps a failed batch delivery fatal and records a queue failure", async () => {
    const now = new Date("2027-01-15T12:00:00Z");
    const nowSeconds = Math.floor(now.getTime() / 1_000);
    vi.spyOn(Date, "now").mockReturnValue(now.getTime());
    const singles: MutationEnvelope[] = [];
    const queue = {
      sendBatch: vi.fn(async () => { throw new Error("sensitive queue detail"); }),
      send: vi.fn(async (body: MutationEnvelope) => singles.push(body)),
    } as unknown as Env["FINOPS_INGEST_QUEUE"];
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: accessAssertion(nowSeconds) }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "google-token", expireTime: "2027-01-15T12:15:00Z" }))
      .mockResolvedValueOnce(Response.json({
        jobComplete: true,
        jobReference: { projectId: "felidai-dev", jobId: "job-queue-failure", location: "US" },
        totalBytesProcessed: "12345",
        schema: { fields: fields.map((name) => ({ name })) },
        rows: [bqRow(line())],
      }));

    await expect(relayGcpBilling(configuredEnv(queue), now)).rejects.toThrow("FinOps connector failed");

    expect(queue.sendBatch).toHaveBeenCalledTimes(1);
    expect(singles).toHaveLength(1);
    expect(singles[0]!.payload).toMatchObject({
      source: "gcp",
      status: "failed",
      sourceRecords: 1,
      entries: 1,
      failureCode: "queue_delivery",
    });
    expect(JSON.stringify(singles[0]!.payload)).not.toContain("sensitive queue detail");
  });

  it("distinguishes BigQuery authorization from query and schema failures", async () => {
    const now = new Date("2027-01-15T12:00:00Z");
    const nowSeconds = Math.floor(now.getTime() / 1_000);
    vi.spyOn(Date, "now").mockReturnValue(now.getTime());
    const singles: MutationEnvelope[] = [];
    const queue = {
      sendBatch: vi.fn(),
      send: vi.fn(async (body: MutationEnvelope) => singles.push(body)),
    } as unknown as Env["FINOPS_INGEST_QUEUE"];
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: accessAssertion(nowSeconds) }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "google-token", expireTime: "2027-01-15T12:15:00Z" }))
      .mockResolvedValueOnce(Response.json({ error: { message: "sensitive policy detail" } }, { status: 403 }));

    await expect(relayGcpBilling(configuredEnv(queue), now)).rejects.toThrow(
      "BigQuery request failed with HTTP 403",
    );

    expect(singles).toHaveLength(1);
    expect(singles[0]!.payload).toMatchObject({
      source: "gcp",
      status: "failed",
      failureCode: "bigquery_authorization",
    });
    expect(JSON.stringify(singles[0]!.payload)).not.toContain("sensitive policy detail");
  });

  it("keeps the SQL contract partition and usage bounded", () => {
    expect(GCP_BILLING_SQL).toContain("_PARTITIONDATE < @toDateExclusive");
    expect(GCP_BILLING_SQL).toContain("DATE(usage_start_time) >= @fromDate");
    expect(GCP_BILLING_SQL).toContain("UNNEST(credits)");
    expect(GCP_BILLING_SQL).toContain("WHERE signed_original_micros != 0");
    expect(GCP_BILLING_SQL).toContain("OR signed_usd_micros IS NULL");
    expect(GCP_BILLING_SQL).not.toContain("SELECT billing_account_id FROM");
  });
});
