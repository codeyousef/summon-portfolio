import { canonicalJson, makeMutationEnvelope } from "./contracts";
import {
  ConnectorFailure,
  connectorFailure,
  connectorFailureCode,
  highestPriorityFailureCode,
  parseBoundedJson,
  type ConnectorFailureCode,
} from "./connector-failure";
import type { Env, MutationEnvelope } from "./types";
import { finOpsCoverageStart, requireEnvelopeWithinFinOpsCoverage } from "./finops-coverage";

const API_ROOT = "https://api.cloudflare.com/client/v4";
const MAX_BATCH = 100;
const MAX_API_RESPONSE_BYTES = 1024 * 1024;

interface ApiEnvelope {
  success?: boolean;
  result?: unknown;
}

interface Zone {
  id?: string;
  name?: string;
}

export interface CloudflareSubscription {
  id?: string;
  currency?: string;
  current_period_start?: string;
  current_period_end?: string;
  frequency?: string;
  price?: number | string;
  state?: string;
  rate_plan?: {
    id?: string;
    currency?: string;
    public_name?: string;
    scope?: string;
  };
}

export type CloudflareUsageRecord = Record<string, unknown>;

interface SourceContext {
  scope: "account" | "zone" | "usage";
  zoneId?: string;
  zoneName?: string;
}

export async function relayCloudflareBilling(env: Env): Promise<void> {
  if (env.PORTFOLIO_ENV !== "dev") throw new Error("Cloudflare billing ingestion is dev-only until production approval");
  const { from, to } = currentBillingWindow(new Date(), finOpsCoverageStart(env).epochMillis);
  const fromDate = from.slice(0, 10);
  const toDateExclusive = to.slice(0, 10);
  if (from >= to) {
    console.log(JSON.stringify({ event: "cloudflare_billing_import_skipped", reason: "coverage_not_started" }));
    return;
  }
  const token = env.CLOUDFLARE_BILLING_API_TOKEN?.trim();
  const accountId = env.CLOUDFLARE_ACCOUNT_ID?.trim();
  if (!token || !accountId) {
    if (!token && !accountId) {
      console.log(JSON.stringify({ event: "cloudflare_billing_import_skipped", reason: "not_configured" }));
      return;
    }
  }

  const records: MutationEnvelope[] = [];
  const sourceMaterials: string[] = [];
  let sourceRecords = 0;
  try {
    if (!token || !accountId || !/^[a-f0-9]{32}$/.test(accountId)) {
      throw new ConnectorFailure("configuration", "Invalid Cloudflare billing connector configuration");
    }
    const degradationCodes = new Set<ConnectorFailureCode>();
    let accountSubscriptions: unknown[] = [];
    try {
      accountSubscriptions = await apiGetArray(
        `${API_ROOT}/accounts/${accountId}/subscriptions`, token,
      );
    } catch (error) {
      recordDegradation(degradationCodes, "account-subscriptions", connectorFailureCode(error));
    }
    for (const candidate of accountSubscriptions) {
      if (!isCloudflareSubscription(candidate)) {
        degradationCodes.add("record_validation");
        logInvalidRecord("account-subscription");
        continue;
      }
      const subscription = candidate;
      sourceRecords += 1;
      sourceMaterials.push(canonicalJson({ context: { scope: "account" }, subscription }));
      const coverageCode = subscriptionCoverageCode(subscription);
      if (coverageCode !== null) degradationCodes.add(coverageCode);
      try {
        const envelope = await normalizeSubscription(subscription, { scope: "account" }, env.PORTFOLIO_ENV);
        if (envelope) records.push(envelope);
      } catch {
        degradationCodes.add("record_validation");
        logInvalidRecord("account-subscription");
      }
    }

    let zones: unknown[] = [];
    try {
      zones = await apiGetArray(`${API_ROOT}/zones?account.id=${accountId}&per_page=50`, token);
    } catch (error) {
      recordDegradation(degradationCodes, "zones", connectorFailureCode(error));
    }
    for (const candidate of zones) {
      if (!isZoneWithId(candidate)) {
        degradationCodes.add("record_validation");
        logInvalidRecord("zone");
        continue;
      }
      const zone = candidate;
      const zoneId = zone.id;
      let subscriptions: unknown[] = [];
      try {
        subscriptions = await apiGetArray(`${API_ROOT}/zones/${zoneId}/subscriptions`, token);
      } catch (error) {
        recordDegradation(degradationCodes, "zone-subscriptions", connectorFailureCode(error));
      }
      for (const subscriptionCandidate of subscriptions) {
        if (!isCloudflareSubscription(subscriptionCandidate)) {
          degradationCodes.add("record_validation");
          logInvalidRecord("zone-subscription");
          continue;
        }
        const subscription = subscriptionCandidate;
        const context: SourceContext = { scope: "zone", zoneId };
        if (zone.name) context.zoneName = zone.name;
        sourceRecords += 1;
        sourceMaterials.push(canonicalJson({ context, subscription }));
        const coverageCode = subscriptionCoverageCode(subscription);
        if (coverageCode !== null) degradationCodes.add(coverageCode);
        try {
          const envelope = await normalizeSubscription(
            subscription,
            context,
            env.PORTFOLIO_ENV,
          );
          if (envelope) records.push(envelope);
        } catch {
          degradationCodes.add("record_validation");
          logInvalidRecord("zone-subscription");
        }
      }
    }

    let usage: unknown[] = [];
    try {
      usage = await apiGetArray(
        `${API_ROOT}/accounts/${accountId}/billable/usage?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
        token,
        "restricted_api_unavailable",
      );
    } catch (error) {
      recordDegradation(degradationCodes, "restricted-usage", connectorFailureCode(error));
    }
    for (const candidate of usage) {
      if (!isCloudflareUsageRecord(candidate)) {
        degradationCodes.add("record_validation");
        logInvalidRecord("usage");
        continue;
      }
      const item = candidate;
      sourceRecords += 1;
      sourceMaterials.push(canonicalJson({ context: { scope: "usage" }, item }));
      const coverageCode = usageCoverageCode(item);
      if (coverageCode !== null) degradationCodes.add(coverageCode);
      try {
        const envelope = await normalizeUsage(item, env.PORTFOLIO_ENV);
        if (envelope) records.push(envelope);
      } catch {
        degradationCodes.add("record_validation");
        logInvalidRecord("usage");
      }
    }

    const coveredRecords = records.filter((envelope) => {
      try {
        requireEnvelopeWithinFinOpsCoverage(envelope, env);
        return true;
      } catch {
        return false;
      }
    });
    try {
      for (let offset = 0; offset < coveredRecords.length; offset += MAX_BATCH) {
        await env.FINOPS_INGEST_QUEUE.sendBatch(
          coveredRecords.slice(offset, offset + MAX_BATCH).map((body) => ({ body, contentType: "json" })),
        );
      }
    } catch (error) {
      throw connectorFailure("queue_delivery", error, "Cloudflare billing queue delivery failed");
    }
    const failureCode = highestPriorityFailureCode(degradationCodes);
    const sourceHash = await sha256(canonicalJson({ records: sourceMaterials.sort(), failureCode: failureCode ?? null }));
    try {
      await env.FINOPS_INGEST_QUEUE.send(await importRunEnvelope({
        fromDate,
        toDateExclusive,
        sourceRecords,
        entries: coveredRecords.length,
        sourceHash,
        status: failureCode === undefined ? "succeeded" : "partial",
        completedAt: Date.now(),
        ...(failureCode === undefined ? {} : { failureCode }),
      }), { contentType: "json" });
    } catch (error) {
      throw connectorFailure("queue_delivery", error, "Cloudflare billing queue delivery failed");
    }
  } catch (error) {
    const failureCode = connectorFailureCode(error);
    console.error(JSON.stringify({ event: "cloudflare_billing_import_failed", failureCode }));
    await publishFailedImportRun(env, fromDate, toDateExclusive, sourceRecords, records.length, Date.now(), failureCode);
    throw error;
  }
}

export async function normalizeSubscription(
  subscription: CloudflareSubscription,
  context: SourceContext,
  environment: "dev" | "prod",
): Promise<MutationEnvelope | null> {
  const id = requiredSafeText(subscription.id, "subscription id");
  const periodStart = requiredTimestamp(subscription.current_period_start, "subscription period start");
  const currency = requiredCurrency(subscription.currency ?? subscription.rate_plan?.currency ?? "USD");
  if (currency !== "USD") return null; // Requires an authoritative invoice or FX rate before ingestion.
  const signedMicros = decimalMicros(subscription.price ?? 0);
  if (signedMicros === 0) return null;
  const service = safeDimension(subscription.rate_plan?.public_name ?? subscription.rate_plan?.id ?? "Cloudflare subscription");
  const sourceRecordId = `subscription:${context.scope}:${context.zoneId ?? "account"}:${id}:${periodStart}`;
  // Subscription state and vendor-only display fields may change while the
  // economic line item remains the same. Keep both the immutable source hash
  // and the queued payload restricted to stable billed fields so a later poll
  // cannot reuse an idempotency key with a different payload.
  const recordHash = await sha256(canonicalJson({
    sourceRecordId,
    currency,
    signedMicros,
    service,
    sku: subscription.rate_plan?.id ?? subscription.frequency ?? "recurring",
  }));
  return finOpsEnvelope({
    sourceRecordId,
    direction: signedMicros < 0 ? "CREDIT" : "EXPENSE",
    usdMicros: Math.abs(signedMicros),
    incurredAt: Date.parse(periodStart),
    invoiceMonth: periodStart.slice(0, 7),
    project: classifyProject(context.zoneName, service),
    environment,
    service,
    sku: safeDimension(subscription.rate_plan?.id ?? subscription.frequency ?? "recurring"),
    reconciliationKey: `cloudflare:${periodStart.slice(0, 7)}:${context.zoneId ?? "account"}`,
    sourceHash: recordHash,
    metadata: {
      costClass: "cloud_platform",
      cloudflareScope: context.scope,
      coverage: "subscription",
    },
  });
}

export async function normalizeUsage(
  record: CloudflareUsageRecord,
  environment: "dev" | "prod",
): Promise<MutationEnvelope | null> {
  const rawCost = firstDefined(record, "BilledCost", "EffectiveCost", "ContractedCost", "ListCost");
  if (rawCost === undefined || rawCost === null) return null;
  const signedMicros = decimalMicros(rawCost);
  if (signedMicros === 0) return null;
  const currency = requiredCurrency(String(firstDefined(record, "BillingCurrency", "Currency") ?? "USD"));
  if (currency !== "USD") return null;
  const periodStart = requiredTimestamp(
    firstDefined(record, "ChargePeriodStart", "BillingPeriodStart", "UsagePeriodStart"),
    "usage period start",
  );
  const zoneName = optionalText(firstDefined(record, "x_ZoneName", "ZoneName"));
  const service = safeDimension(String(firstDefined(record, "x_ProductFamilyName", "ServiceName", "ChargeCategory") ?? "Cloudflare usage"));
  const sourceRecordId = `usage:${(await sha256(canonicalJson(record))).slice(0, 48)}`;
  return finOpsEnvelope({
    sourceRecordId,
    direction: signedMicros < 0 ? "CREDIT" : "EXPENSE",
    usdMicros: Math.abs(signedMicros),
    incurredAt: Date.parse(periodStart),
    invoiceMonth: periodStart.slice(0, 7),
    project: classifyProject(zoneName, service),
    environment,
    service,
    sku: safeDimension(String(firstDefined(record, "SkuPriceId", "PricingUnit", "ChargeCategory") ?? "billable-usage")),
    reconciliationKey: `cloudflare:${periodStart.slice(0, 7)}:${optionalText(firstDefined(record, "x_ZoneId", "ZoneId")) ?? "account"}`,
    sourceHash: await sha256(canonicalJson(record)),
    metadata: {
      costClass: "cloud_platform",
      cloudflareScope: "usage",
      coverage: "restricted-usage-api",
    },
  }, "ACCRUED");
}

export function decimalMicros(input: unknown): number {
  const text = typeof input === "number" && Number.isFinite(input) ? String(input) : String(input).trim();
  const match = /^([+-]?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(text);
  if (!match) throw new Error("invalid decimal cost");
  const negative = match[1] === "-";
  const fraction = match[3] ?? "";
  const exponent = Number(match[4] ?? "0");
  if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 100) throw new Error("decimal exponent is out of range");
  let value = BigInt(`${match[2]}${fraction}`);
  const shift = 6 - fraction.length + exponent;
  if (shift >= 0) value *= 10n ** BigInt(shift);
  else {
    const divisor = 10n ** BigInt(-shift);
    const quotient = value / divisor;
    const remainder = value % divisor;
    value = quotient + (remainder * 2n >= divisor ? 1n : 0n);
  }
  if (negative) value = -value;
  const result = Number(value);
  if (!Number.isSafeInteger(result)) throw new Error("cost exceeds fixed-point range");
  return result;
}

interface FinOpsInput {
  sourceRecordId: string;
  direction: "EXPENSE" | "CREDIT";
  usdMicros: number;
  incurredAt: number;
  invoiceMonth: string;
  project: string;
  environment: "dev" | "prod";
  service: string;
  sku: string;
  reconciliationKey: string;
  sourceHash: string;
  metadata: Record<string, string>;
}

async function finOpsEnvelope(input: FinOpsInput, status: "ESTIMATED" | "ACCRUED" = "ACCRUED"): Promise<MutationEnvelope> {
  const id = `cloudflare:${(await sha256(input.sourceRecordId)).slice(0, 40)}`;
  const payload = {
    entry: {
      id,
      source: "cloudflare",
      sourceRecordId: input.sourceRecordId,
      direction: input.direction,
      amount: { currency: "USD", minorUnits: input.usdMicros, scale: 6 },
      usdMicros: input.usdMicros,
      incurredAt: input.incurredAt,
      invoiceMonth: input.invoiceMonth,
      project: input.project,
      environment: input.environment,
      vendor: "cloudflare",
      service: input.service,
      sku: input.sku,
      status,
      reconciliationKey: input.reconciliationKey,
      sourceHash: input.sourceHash,
      metadata: input.metadata,
    },
    allocations: [],
  };
  return makeMutationEnvelope({
    id,
    aggregateType: "finops.ingest",
    aggregateId: input.project,
    expectedRevision: 0,
    authorityEpoch: 1,
    occurredAt: new Date(input.incurredAt).toISOString(),
    payload,
  });
}

async function importRunEnvelope(input: {
  fromDate: string;
  toDateExclusive: string;
  sourceRecords: number;
  entries: number;
  sourceHash: string;
  status: "succeeded" | "partial" | "failed";
  completedAt: number;
  failureCode?: ConnectorFailureCode;
}): Promise<MutationEnvelope> {
  const id = `import:cloudflare:${input.toDateExclusive}:${input.sourceHash.slice(0, 16)}`;
  return makeMutationEnvelope({
    id,
    aggregateType: "finops.import.run",
    aggregateId: "cloudflare",
    expectedRevision: 0,
    authorityEpoch: 1,
    occurredAt: new Date(input.completedAt).toISOString(),
    payload: {
      id,
      source: "cloudflare",
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
  failureCode: ConnectorFailureCode,
): Promise<void> {
  try {
    const sourceHash = await sha256(canonicalJson({
      source: "cloudflare",
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
      event: "cloudflare_billing_failed_import_run_publish_failed",
      failureCode,
    }));
  }
}

async function apiGetArray(
  url: string,
  token: string,
  authorizationFailureCode: ConnectorFailureCode = "provider_authorization",
): Promise<unknown[]> {
  const response = await fetch(url, {
    headers: { authorization: `Bearer ${token}`, accept: "application/json" },
  });
  if (!response.ok) {
    const failureCode = response.status === 401 || response.status === 403
      ? authorizationFailureCode
      : "provider_api";
    throw new ConnectorFailure(failureCode, `Cloudflare API request failed with HTTP ${response.status}`);
  }
  const body = await parseBoundedJson(response, MAX_API_RESPONSE_BYTES, "provider_schema");
  if (!isApiEnvelope(body) || body.success !== true || !Array.isArray(body.result)) {
    throw new ConnectorFailure("provider_schema", "Cloudflare API response has an unexpected shape");
  }
  return body.result;
}

function isApiEnvelope(value: unknown): value is ApiEnvelope {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isZoneWithId(value: unknown): value is Zone & { id: string } {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.id === "string" && /^[a-f0-9]{32}$/.test(candidate.id) &&
    (candidate.name === undefined || typeof candidate.name === "string");
}

function isCloudflareSubscription(value: unknown): value is CloudflareSubscription {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isCloudflareUsageRecord(value: unknown): value is CloudflareUsageRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function recordDegradation(
  degradationCodes: Set<ConnectorFailureCode>,
  scope: string,
  failureCode: ConnectorFailureCode,
): void {
  degradationCodes.add(failureCode);
  console.error(JSON.stringify({
    event: "cloudflare_billing_source_unavailable",
    scope,
    failureCode,
  }));
}

function logInvalidRecord(scope: string): void {
  // Never log vendor payloads: subscription metadata may contain account or
  // invoice details that do not belong in Worker observability.
  console.error(JSON.stringify({
    event: "cloudflare_billing_record_rejected",
    scope,
    failureCode: "record_validation",
  }));
}

function subscriptionCoverageCode(subscription: CloudflareSubscription): ConnectorFailureCode | null {
  if (subscription.price === undefined || subscription.price === null) return "cost_unavailable";
  const currency = String(subscription.currency ?? subscription.rate_plan?.currency ?? "USD").trim().toUpperCase();
  return /^[A-Z]{3}$/.test(currency) && currency !== "USD" ? "unsupported_currency" : null;
}

function usageCoverageCode(record: CloudflareUsageRecord): ConnectorFailureCode | null {
  if (firstDefined(record, "BilledCost", "EffectiveCost", "ContractedCost", "ListCost") === undefined) {
    return "cost_unavailable";
  }
  const currency = String(firstDefined(record, "BillingCurrency", "Currency") ?? "USD").trim().toUpperCase();
  return /^[A-Z]{3}$/.test(currency) && currency !== "USD" ? "unsupported_currency" : null;
}

function currentBillingWindow(now = new Date(), coverageStartAt = 0): { from: string; to: string } {
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  const to = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
  return { from: new Date(Math.max(from.getTime(), coverageStartAt)).toISOString(), to: to.toISOString() };
}

function classifyProject(zoneName: string | undefined, service: string): string {
  const zone = zoneName?.toLowerCase() ?? "";
  const product = service.toLowerCase();
  if (zone === "yousef.codes") return "portfolio";
  if (zone === "felidai.com" || product.includes("ai gateway") || product.includes("vectorize")) return "samurai";
  if (product.includes("seen")) return "seen";
  return "shared";
}

function firstDefined(record: CloudflareUsageRecord, ...keys: string[]): unknown {
  return keys.map((key) => record[key]).find((value) => value !== undefined && value !== null);
}

function requiredSafeText(value: unknown, label: string): string {
  const text = String(value ?? "").trim();
  if (!/^[A-Za-z0-9._:-]{1,180}$/.test(text)) throw new Error(`invalid ${label}`);
  return text;
}

function requiredTimestamp(value: unknown, label: string): string {
  const text = String(value ?? "");
  if (!Number.isFinite(Date.parse(text))) throw new Error(`invalid ${label}`);
  return new Date(text).toISOString();
}

function requiredCurrency(value: string): string {
  const currency = value.trim().toUpperCase();
  if (!/^[A-Z]{3}$/.test(currency)) throw new Error("invalid billing currency");
  return currency;
}

function safeDimension(value: string): string {
  // Portfolio's authoritative FinOps schema caps query dimensions at 120
  // characters. Normalize at the connector boundary so a verbose vendor
  // product label cannot poison the Queue with a permanently invalid entry.
  const normalized = value.replace(/[\r\n\t]/g, " ").trim().slice(0, 120);
  return normalized || "unknown";
}

function optionalText(value: unknown): string | undefined {
  const text = String(value ?? "").trim();
  return text ? safeDimension(text) : undefined;
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
