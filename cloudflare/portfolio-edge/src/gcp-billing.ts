import { canonicalJson, makeMutationEnvelope, sha256Hex } from "./contracts";
import {
  ConnectorFailure,
  connectorFailure,
  connectorFailureCode,
  type ConnectorFailureCode,
} from "./connector-failure";
import { googleServiceAccountAccessToken } from "./gcp-wif";
import type { Env, MutationEnvelope } from "./types";
import { finOpsCoverageStart } from "./finops-coverage";
import { readBoundedBody } from "./bounded-body";

const BIGQUERY_ROOT = "https://bigquery.googleapis.com/bigquery/v2";
const MAX_BATCH = 100;
const MAX_QUERY_PAGES = 100;
export const GCP_BILLING_SOURCE_ROW_LIMIT = 100_000;
const QUERY_PAGE_SIZE = 1_000;
const BILLING_EXPORT_MAX_LAG_DAYS = 3;
const DAY_MS = 86_400_000;

export const GCP_BILLING_FAILURE_CODES = [
  "configuration",
  "workload_identity",
  "bigquery_authorization",
  "bigquery_query",
  "bigquery_schema",
  "query_limit",
  "queue_delivery",
  "unknown",
] as const satisfies readonly ConnectorFailureCode[];
export type GcpBillingFailureCode = ConnectorFailureCode;

interface BillingConfig {
  projectId: string;
  dataset: string;
  table: string;
  location: string;
  maximumBytesBilled: number;
}

interface BigQueryField { name?: string; }
interface BigQueryCell { v?: unknown; }
interface BigQueryRow { f?: BigQueryCell[]; }
interface BigQueryResponse {
  jobComplete?: boolean;
  jobReference?: { projectId?: string; jobId?: string; location?: string };
  schema?: { fields?: BigQueryField[] };
  rows?: BigQueryRow[];
  pageToken?: string;
  totalBytesProcessed?: string;
  errors?: Array<{ reason?: string; message?: string }>;
}

export interface BillingLine {
  source_record_id: string;
  billing_account_id: string;
  invoice_month: string;
  project_id: string;
  service: string;
  sku: string;
  incurred_at: string;
  export_time: string;
  currency: string;
  signed_original_micros: string;
  signed_usd_micros: string;
  line_kind: string;
  cost_type: string;
}

export const GCP_BILLING_SQL = `WITH base AS (
  SELECT
    billing_account_id,
    invoice.month AS invoice_month,
    COALESCE(project.id, 'unattributed') AS project_id,
    service.description AS service,
    sku.description AS sku,
    FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%E3SZ', usage_start_time, 'UTC') AS incurred_at,
    FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%E3SZ', export_time, 'UTC') AS export_time,
    currency,
    currency_conversion_rate,
    cost,
    credits,
    cost_type,
    adjustment_info
  FROM __TABLE__
  -- Standard billing export partitions follow export time. A one-day buffer
  -- covers timezone edges without scanning historical pre-coverage partitions;
  -- the rolling usage window still replays late corrections idempotently.
  WHERE _PARTITIONDATE >= DATE_SUB(@fromDate, INTERVAL 1 DAY)
    AND _PARTITIONDATE < @toDateExclusive
    AND DATE(usage_start_time) >= @fromDate
    AND DATE(usage_start_time) < @toDateExclusive
), cost_lines_unfiltered AS (
  SELECT
    TO_HEX(SHA256(TO_JSON_STRING(STRUCT('cost' AS component, billing_account_id, invoice_month, project_id, service, sku, incurred_at, export_time, cost, cost_type, adjustment_info)))) AS source_record_id,
    billing_account_id, invoice_month, project_id, service, sku, incurred_at, export_time, currency,
    CAST(ROUND(cost * 1000000) AS INT64) AS signed_original_micros,
    CAST(ROUND(SAFE_DIVIDE(cost, COALESCE(NULLIF(currency_conversion_rate, 0), IF(currency = 'USD', 1, NULL))) * 1000000) AS INT64) AS signed_usd_micros,
    'cost' AS line_kind,
    COALESCE(cost_type, 'regular') AS cost_type
  FROM base
  WHERE cost != 0
), cost_lines AS (
  SELECT *
  FROM cost_lines_unfiltered
  WHERE signed_original_micros != 0
    OR signed_usd_micros != 0
    OR signed_original_micros IS NULL
    OR signed_usd_micros IS NULL
), credit_lines_unfiltered AS (
  SELECT
    TO_HEX(SHA256(TO_JSON_STRING(STRUCT('credit' AS component, billing_account_id, invoice_month, project_id, service, sku, incurred_at, export_time, credit_offset, credit.id, credit.type, credit.name, credit.amount)))) AS source_record_id,
    billing_account_id, invoice_month, project_id, service, sku, incurred_at, export_time, currency,
    CAST(ROUND(credit.amount * 1000000) AS INT64) AS signed_original_micros,
    CAST(ROUND(SAFE_DIVIDE(credit.amount, COALESCE(NULLIF(currency_conversion_rate, 0), IF(currency = 'USD', 1, NULL))) * 1000000) AS INT64) AS signed_usd_micros,
    'credit' AS line_kind,
    'credit' AS cost_type
  FROM base, UNNEST(credits) AS credit WITH OFFSET AS credit_offset
  WHERE credit.amount != 0
), credit_lines AS (
  SELECT *
  FROM credit_lines_unfiltered
  WHERE signed_original_micros != 0
    OR signed_usd_micros != 0
    OR signed_original_micros IS NULL
    OR signed_usd_micros IS NULL
)
SELECT * FROM cost_lines
UNION ALL
SELECT * FROM credit_lines
ORDER BY incurred_at, source_record_id`;

export async function relayGcpBilling(env: Env, now = new Date()): Promise<void> {
  if (env.PORTFOLIO_ENV !== "dev") throw new Error("GCP billing ingestion is dev-only until production approval");
  const { fromDate, toDateExclusive } = billingWindow(now, finOpsCoverageStart(env).date);
  let sourceRecords = 0;
  let entries = 0;
  try {
    let config: BillingConfig | null;
    try {
      config = readBillingConfig(env);
    } catch (error) {
      throw connectorFailure("configuration", error);
    }
    if (config === null) {
      console.log(JSON.stringify({ event: "gcp_billing_import_skipped", reason: "not_configured" }));
      return;
    }
    if (fromDate >= toDateExclusive) {
      console.log(JSON.stringify({ event: "gcp_billing_import_skipped", reason: "coverage_not_started" }));
      return;
    }
    let token: string | null;
    try {
      token = await googleServiceAccountAccessToken(env);
    } catch (error) {
      throw connectorFailure("workload_identity", error);
    }
    if (token === null) {
      throw new ConnectorFailure(
        "workload_identity",
        "GCP billing configuration requires a complete WIF identity",
      );
    }
    const rows = await queryBillingRows(config, token, fromDate, toDateExclusive);
    sourceRecords = rows.length;
    const sourceRecordIds: string[] = [];
    let pending: MutationEnvelope[] = [];
    let partial = false;
    for (const row of rows) {
      // The SQL source ID is a SHA-256 over every invoice-relevant field,
      // including export time and the cost/credit component. Retaining only
      // these fixed-size identities avoids holding a second full copy of a
      // large billing window while preserving exact observation hashing.
      sourceRecordIds.push(row.source_record_id.toLowerCase());
      let envelope: MutationEnvelope;
      try {
        envelope = await normalizeGcpBillingLine(row, env.PORTFOLIO_ENV);
      } catch {
        partial = true;
        console.error(JSON.stringify({ event: "gcp_billing_record_rejected", scope: "billing_export_line" }));
        continue;
      }
      pending.push(envelope);
      entries += 1;
      if (pending.length === MAX_BATCH) {
        await publishBillingBatch(env, pending);
        pending = [];
      }
    }
    if (pending.length > 0) await publishBillingBatch(env, pending);
    const exportCoverage = billingExportCoverage(rows, toDateExclusive);
    if (!exportCoverage.complete) {
      partial = true;
      console.error(JSON.stringify({
        event: "gcp_billing_export_freshness_gap",
        latestExportAt: exportCoverage.latestExportAt === null
          ? "missing"
          : new Date(exportCoverage.latestExportAt).toISOString(),
        minimumExpectedAt: new Date(exportCoverage.minimumExpectedAt).toISOString(),
      }));
    }
    // Coverage evidence is part of the immutable source observation. This both
    // prevents a stale export from masquerading as complete and ensures a new
    // partial receipt cannot collide with an older succeeded receipt that saw
    // the same financial rows before the freshness gate existed.
    const sourceHash = await sha256Hex(canonicalJson({
      records: sourceRecordIds.sort(),
      exportCoverage,
    }));
    try {
      await env.FINOPS_INGEST_QUEUE.send(await importRunEnvelope({
        fromDate,
        toDateExclusive,
        sourceRecords,
        entries,
        sourceHash,
        status: partial ? "partial" : "succeeded",
        completedAt: Date.now(),
      }), { contentType: "json" });
    } catch (error) {
      throw connectorFailure("queue_delivery", error);
    }
  } catch (error) {
    const failureCode = gcpBillingFailureCode(error);
    console.error(JSON.stringify({ event: "gcp_billing_import_failed", failureCode }));
    await publishFailedImportRun(
      env,
      fromDate,
      toDateExclusive,
      sourceRecords,
      entries,
      Date.now(),
      failureCode,
    );
    throw error;
  }
}

async function publishBillingBatch(env: Env, envelopes: MutationEnvelope[]): Promise<void> {
  try {
    await env.FINOPS_INGEST_QUEUE.sendBatch(
      envelopes.map((body) => ({ body, contentType: "json" })),
    );
  } catch (error) {
    throw connectorFailure("queue_delivery", error);
  }
}

export function gcpBillingFailureCode(error: unknown): GcpBillingFailureCode {
  return connectorFailureCode(error);
}

export interface BillingExportCoverage {
  complete: boolean;
  latestExportAt: number | null;
  minimumExpectedAt: number;
}

export function billingExportCoverage(rows: BillingLine[], toDateExclusive: string): BillingExportCoverage {
  const windowEnd = Date.parse(`${toDateExclusive}T00:00:00.000Z`);
  if (!Number.isSafeInteger(windowEnd) || windowEnd < 0) throw new Error("invalid GCP billing coverage boundary");
  const minimumExpectedAt = windowEnd - BILLING_EXPORT_MAX_LAG_DAYS * DAY_MS;
  let latestExportAt: number | null = null;
  for (const row of rows) {
    const timestamp = Date.parse(String(row.export_time));
    if (!Number.isSafeInteger(timestamp) || timestamp < 0) continue;
    latestExportAt = latestExportAt === null ? timestamp : Math.max(latestExportAt, timestamp);
  }
  return {
    complete: latestExportAt !== null && latestExportAt >= minimumExpectedAt,
    latestExportAt,
    minimumExpectedAt,
  };
}

export async function normalizeGcpBillingLine(
  row: BillingLine,
  environment: "dev" | "prod",
): Promise<MutationEnvelope> {
  const sourceRecordId = requiredHex(row.source_record_id, "source record id");
  const billingAccountId = safeDimension(row.billing_account_id);
  const invoiceMonth = requiredInvoiceMonth(row.invoice_month);
  const projectId = safeDimension(row.project_id);
  const service = safeDimension(row.service);
  const sku = safeDimension(row.sku);
  const incurredAt = requiredTimestamp(row.incurred_at, "incurred timestamp");
  requiredTimestamp(row.export_time, "export timestamp");
  const currency = requiredCurrency(row.currency);
  const originalMicros = requiredSignedMicros(row.signed_original_micros, "original amount");
  const usdMicros = requiredSignedMicros(row.signed_usd_micros, "USD amount");
  if (originalMicros === 0 || usdMicros === 0 || Math.sign(originalMicros) !== Math.sign(usdMicros)) {
    throw new Error("GCP original-currency and USD amounts must have matching nonzero signs");
  }
  const lineKind = row.line_kind === "credit" ? "credit" : row.line_kind === "cost" ? "cost" : null;
  if (lineKind === null) throw new Error("invalid GCP billing line kind");
  const costType = safeDimension(row.cost_type.toLowerCase());
  const direction = lineKind === "credit" || usdMicros < 0
    ? "CREDIT"
    : costType === "tax"
      ? "TAX"
      : costType === "adjustment" || costType === "rounding_error"
        ? "ADJUSTMENT"
        : "EXPENSE";
  const entryId = `gcp:${sourceRecordId.slice(0, 40).toLowerCase()}`;
  const rawReconciliationKey = `gcp:${billingAccountId}:${invoiceMonth}:${projectId}:${service}`;
  const reconciliationKey = rawReconciliationKey.length <= 240
    ? rawReconciliationKey
    : `${rawReconciliationKey.slice(0, 175)}:${(await sha256Hex(rawReconciliationKey)).slice(0, 64)}`;
  const payload = {
    entry: {
      id: entryId,
      source: "gcp",
      sourceRecordId: `line:${sourceRecordId.toLowerCase()}`,
      direction,
      amount: { currency, minorUnits: Math.abs(originalMicros), scale: 6 },
      usdMicros: Math.abs(usdMicros),
      incurredAt,
      invoiceMonth,
      project: classifyProject(projectId),
      environment,
      vendor: "google-cloud",
      service,
      sku,
      status: "FINALIZED",
      reconciliationKey,
      sourceHash: await sha256Hex(canonicalJson(row)),
      metadata: {
        costClass: "cloud_platform",
        gcpProjectId: projectId,
        gcpLineKind: lineKind,
        gcpCostType: costType,
        exportTime: row.export_time,
      },
    },
    allocations: [],
  };
  return makeMutationEnvelope({
    id: entryId,
    aggregateType: "finops.ingest",
    aggregateId: classifyProject(projectId),
    expectedRevision: 0,
    authorityEpoch: 1,
    occurredAt: row.incurred_at,
    payload,
  });
}

function readBillingConfig(env: Env): BillingConfig | null {
  const values = [
    env.GCP_FINOPS_BILLING_PROJECT_ID,
    env.GCP_FINOPS_BILLING_DATASET,
    env.GCP_FINOPS_BILLING_TABLE,
    env.GCP_FINOPS_BILLING_LOCATION,
    env.GCP_FINOPS_MAX_BYTES_BILLED,
  ].map((value) => value?.trim() ?? "");
  if (values.every((value) => value === "")) return null;
  if (values.some((value) => value === "")) throw new Error("incomplete GCP billing configuration");
  const [projectId, dataset, table, location, maximumBytesText] = values as [string, string, string, string, string];
  if (!/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(projectId)) throw new Error("invalid GCP billing project");
  if (!/^[A-Za-z0-9_]{1,1024}$/.test(dataset) || !/^[A-Za-z0-9_]{1,1024}$/.test(table)) throw new Error("invalid GCP billing table");
  if (!/^[A-Za-z0-9_-]{2,32}$/.test(location)) throw new Error("invalid BigQuery location");
  const maximumBytesBilled = Number(maximumBytesText);
  if (!Number.isSafeInteger(maximumBytesBilled) || maximumBytesBilled < 1 || maximumBytesBilled > 1_000_000_000) {
    throw new Error("GCP billing maximum bytes must be between 1 and 1,000,000,000");
  }
  return { projectId, dataset, table, location, maximumBytesBilled };
}

async function queryBillingRows(
  config: BillingConfig,
  token: string,
  fromDate: string,
  toDateExclusive: string,
): Promise<BillingLine[]> {
  const table = `\`${config.projectId}.${config.dataset}.${config.table}\``;
  const response = await bigQueryFetch<BigQueryResponse>(
    `${BIGQUERY_ROOT}/projects/${encodeURIComponent(config.projectId)}/queries`,
    token,
    {
      method: "POST",
      body: JSON.stringify({
        query: GCP_BILLING_SQL.replace("__TABLE__", table),
        useLegacySql: false,
        useQueryCache: false,
        maximumBytesBilled: String(config.maximumBytesBilled),
        maxResults: QUERY_PAGE_SIZE,
        timeoutMs: 10_000,
        location: config.location,
        parameterMode: "NAMED",
        queryParameters: [
          dateParameter("fromDate", fromDate),
          dateParameter("toDateExclusive", toDateExclusive),
        ],
      }),
    },
  );
  const reference = response.jobReference;
  if (!reference?.jobId) {
    throw new ConnectorFailure("bigquery_schema", "BigQuery response is missing its job reference");
  }
  let page = response;
  const rows: BillingLine[] = [];
  for (let pageNumber = 0; pageNumber < MAX_QUERY_PAGES; pageNumber += 1) {
    assertQuerySuccess(page, config.maximumBytesBilled);
    if (!page.jobComplete) {
      page = await queryResults(config, token, reference.jobId, null);
      continue;
    }
    rows.push(...decodeRows(page));
    if (rows.length > GCP_BILLING_SOURCE_ROW_LIMIT) {
      throw new ConnectorFailure("query_limit", `GCP billing query exceeded ${GCP_BILLING_SOURCE_ROW_LIMIT} rows`);
    }
    if (!page.pageToken) return rows;
    page = await queryResults(config, token, reference.jobId, page.pageToken);
  }
  throw new ConnectorFailure("query_limit", "GCP billing query exceeded its page/poll safety bound");
}

async function queryResults(config: BillingConfig, token: string, jobId: string, pageToken: string | null): Promise<BigQueryResponse> {
  const url = new URL(`${BIGQUERY_ROOT}/projects/${encodeURIComponent(config.projectId)}/queries/${encodeURIComponent(jobId)}`);
  url.searchParams.set("location", config.location);
  url.searchParams.set("maxResults", String(QUERY_PAGE_SIZE));
  url.searchParams.set("timeoutMs", "10000");
  if (pageToken) url.searchParams.set("pageToken", pageToken);
  return bigQueryFetch<BigQueryResponse>(url.toString(), token);
}

async function bigQueryFetch<T>(url: string, token: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("authorization", `Bearer ${token}`);
  headers.set("accept", "application/json");
  if (init.body) headers.set("content-type", "application/json");
  const response = await fetch(url, { ...init, headers });
  const bytes = await readBoundedBody(response.body, 10 * 1024 * 1024);
  if (bytes === null) {
    throw new ConnectorFailure("query_limit", "BigQuery response exceeds 10 MiB");
  }
  let body: unknown;
  try {
    body = JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes)) as unknown;
  } catch {
    throw new ConnectorFailure("bigquery_schema", "BigQuery response is not JSON");
  }
  if (!response.ok) {
    const failureCode = response.status === 401 || response.status === 403
      ? "bigquery_authorization"
      : "bigquery_query";
    throw new ConnectorFailure(failureCode, `BigQuery request failed with HTTP ${response.status}`);
  }
  return body as T;
}

function assertQuerySuccess(response: BigQueryResponse, maximumBytesBilled: number): void {
  if (response.errors?.length) {
    throw new ConnectorFailure(
      "bigquery_query",
      `BigQuery job failed: ${response.errors[0]?.reason ?? "unknown"}`,
    );
  }
  if (response.totalBytesProcessed !== undefined) {
    const bytes = Number(response.totalBytesProcessed);
    if (!Number.isSafeInteger(bytes) || bytes < 0 || bytes > maximumBytesBilled) {
      throw new ConnectorFailure(
        "query_limit",
        "BigQuery processed bytes exceeded its configured cap",
      );
    }
  }
}

function decodeRows(response: BigQueryResponse): BillingLine[] {
  const names = response.schema?.fields?.map((field) => field.name ?? "") ?? [];
  const required = ["source_record_id", "billing_account_id", "invoice_month", "project_id", "service", "sku", "incurred_at", "export_time", "currency", "signed_original_micros", "signed_usd_micros", "line_kind", "cost_type"];
  if (names.length !== required.length || required.some((name, index) => names[index] !== name)) {
    throw new ConnectorFailure("bigquery_schema", "unexpected GCP billing query schema");
  }
  return (response.rows ?? []).map((row) => {
    if (!Array.isArray(row.f) || row.f.length !== names.length) {
      throw new ConnectorFailure("bigquery_schema", "invalid GCP billing row shape");
    }
    return Object.fromEntries(names.map((name, index) => {
      const value = row.f?.[index]?.v;
      if (typeof value !== "string") {
        throw new ConnectorFailure("bigquery_schema", `GCP billing field ${name} is not a string`);
      }
      return [name, value];
    })) as unknown as BillingLine;
  });
}

function dateParameter(name: string, value: string): object {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error("invalid BigQuery date parameter");
  return { name, parameterType: { type: "DATE" }, parameterValue: { value } };
}

function billingWindow(now: Date, coverageStartDate: string): { fromDate: string; toDateExclusive: string } {
  if (!Number.isFinite(now.getTime())) throw new Error("invalid GCP billing clock");
  const tomorrow = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
  const from = new Date(tomorrow.getTime() - 35 * 86_400_000);
  return {
    fromDate: [from.toISOString().slice(0, 10), coverageStartDate].sort().at(-1)!,
    toDateExclusive: tomorrow.toISOString().slice(0, 10),
  };
}

async function importRunEnvelope(input: {
  fromDate: string;
  toDateExclusive: string;
  sourceRecords: number;
  entries: number;
  sourceHash: string;
  status: "succeeded" | "partial" | "failed";
  completedAt: number;
  failureCode?: GcpBillingFailureCode;
}): Promise<MutationEnvelope> {
  const id = `import:gcp:${input.toDateExclusive}:${input.sourceHash.slice(0, 16)}`;
  return makeMutationEnvelope({
    id,
    aggregateType: "finops.import.run",
    aggregateId: "gcp",
    expectedRevision: 0,
    authorityEpoch: 1,
    occurredAt: new Date(input.completedAt).toISOString(),
    payload: {
      id,
      source: "gcp",
      ...input,
      ...(input.failureCode === undefined ? {} : { failureCode: input.failureCode }),
    },
  });
}

async function publishFailedImportRun(
  env: Env,
  fromDate: string,
  toDateExclusive: string,
  sourceRecords: number,
  entries: number,
  completedAt: number,
  failureCode: GcpBillingFailureCode,
): Promise<void> {
  try {
    const sourceHash = await sha256Hex(canonicalJson({
      source: "gcp",
      fromDate,
      toDateExclusive,
      status: "failed",
      failureCode,
    }));
    await env.FINOPS_INGEST_QUEUE.send(await importRunEnvelope({
      fromDate,
      toDateExclusive,
      sourceRecords,
      entries,
      sourceHash,
      status: "failed",
      completedAt,
      failureCode,
    }), { contentType: "json" });
  } catch {
    console.error(JSON.stringify({
      event: "gcp_billing_failed_import_run_publish_failed",
      failureCode,
    }));
  }
}

function requiredHex(value: string, label: string): string {
  const normalized = String(value).trim();
  if (!/^[A-Fa-f0-9]{64}$/.test(normalized)) throw new Error(`invalid ${label}`);
  return normalized;
}

function requiredInvoiceMonth(value: string): string {
  const normalized = String(value).trim().replace(/^(\d{4})(\d{2})$/, "$1-$2");
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(normalized)) throw new Error("invalid GCP invoice month");
  return normalized;
}

function requiredTimestamp(value: string, label: string): number {
  const timestamp = Date.parse(value);
  if (!Number.isSafeInteger(timestamp) || timestamp < 0) throw new Error(`invalid ${label}`);
  return timestamp;
}

function requiredCurrency(value: string): string {
  const currency = String(value).trim().toUpperCase();
  if (!/^[A-Z]{3}$/.test(currency)) throw new Error("invalid GCP billing currency");
  return currency;
}

function requiredSignedMicros(value: string, label: string): number {
  if (!/^-?\d+$/.test(String(value))) throw new Error(`invalid ${label}`);
  const result = Number(value);
  if (!Number.isSafeInteger(result) || result === Number.MIN_SAFE_INTEGER) throw new Error(`${label} exceeds the safe fixed-point range`);
  return result;
}

function safeDimension(value: string): string {
  const normalized = String(value).replace(/[\r\n\t]/g, " ").trim().slice(0, 120);
  return normalized || "unattributed";
}

function classifyProject(projectId: string): string {
  const normalized = projectId.toLowerCase();
  if (normalized.includes("samurai") || normalized.includes("teddy")) return "samurai";
  if (normalized.includes("seen")) return "seen";
  if (normalized.includes("portfolio")) return "portfolio";
  return "shared";
}
