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
import { finOpsCoverageStart } from "./finops-coverage";

const STRIPE_API = "https://api.stripe.com/v1/balance_transactions";
const MAX_PAGES = 100;
const PAGE_SIZE = 100;
const MAX_BATCH = 100;
const MAX_API_RESPONSE_BYTES = 1024 * 1024;
const SAMURAI_USER_ID = /^[A-Za-z0-9][A-Za-z0-9@._:+-]{0,199}$/;

interface StripeSource {
  id?: string;
  metadata?: Record<string, string | undefined>;
}

export interface StripeBalanceTransaction {
  id?: string;
  amount?: number;
  fee?: number;
  currency?: string;
  created?: number;
  type?: string;
  reporting_category?: string;
  source?: string | StripeSource | null;
}

interface StripeList {
  object?: string;
  data?: StripeBalanceTransaction[];
  has_more?: boolean;
}

interface StripePageResult {
  transactions: StripeBalanceTransaction[];
  hasMore: boolean;
}

export async function relayStripeBilling(env: Env, now = new Date()): Promise<void> {
  if (env.PORTFOLIO_ENV !== "dev") throw new Error("Stripe billing ingestion is dev-only until production approval");
  const { fromSeconds, toSeconds, fromDate, toDateExclusive } = stripeWindow(now, finOpsCoverageStart(env));
  if (fromSeconds >= toSeconds) {
    console.log(JSON.stringify({ event: "stripe_billing_import_skipped", reason: "coverage_not_started" }));
    return;
  }
  const token = env.STRIPE_FINOPS_READ_KEY?.trim();
  if (!token) {
    console.log(JSON.stringify({ event: "stripe_billing_import_skipped", reason: "not_configured" }));
    return;
  }

  const envelopes: MutationEnvelope[] = [];
  const sourceMaterials: string[] = [];
  let sourceRecords = 0;
  try {
    if (!/^rk_(?:test|live)_[A-Za-z0-9]{16,}$/.test(token)) {
      throw new ConnectorFailure("configuration", "Invalid Stripe connector configuration");
    }
    const degradationCodes = new Set<ConnectorFailureCode>();
    let cursor: string | null = null;
    for (let page = 0; page < MAX_PAGES; page += 1) {
      const result = await fetchStripePage(token, fromSeconds, toSeconds, cursor);
      if (result.transactions.length === 0 && result.hasMore) {
        throw new ConnectorFailure("pagination_limit", "Stripe pagination did not advance");
      }
      for (const transaction of result.transactions) {
        sourceRecords += 1;
        sourceMaterials.push(canonicalJson(stableStripeRecord(transaction)));
        try {
          const normalized = await normalizeStripeBalanceTransaction(transaction, env.PORTFOLIO_ENV);
          if (normalized === null) degradationCodes.add("unsupported_currency");
          else envelopes.push(...normalized);
        } catch {
          degradationCodes.add("record_validation");
          console.error(JSON.stringify({
            event: "stripe_billing_record_rejected",
            scope: "balance-transaction",
            failureCode: "record_validation",
          }));
        }
      }
      if (!result.hasMore) break;
      const next = result.transactions.at(-1)?.id?.trim() ?? "";
      if (!next || next === cursor) {
        throw new ConnectorFailure("pagination_limit", "Stripe pagination cursor did not advance");
      }
      cursor = next;
      if (page === MAX_PAGES - 1) {
        throw new ConnectorFailure("pagination_limit", "Stripe billing import exceeded its page safety bound");
      }
    }

    try {
      for (let offset = 0; offset < envelopes.length; offset += MAX_BATCH) {
        await env.FINOPS_INGEST_QUEUE.sendBatch(
          envelopes.slice(offset, offset + MAX_BATCH).map((body) => ({ body, contentType: "json" })),
        );
      }
    } catch (error) {
      throw connectorFailure("queue_delivery", error, "Stripe billing queue delivery failed");
    }
    const failureCode = highestPriorityFailureCode(degradationCodes);
    const sourceHash = await sha256(canonicalJson({ records: sourceMaterials.sort(), failureCode: failureCode ?? null }));
    try {
      await env.FINOPS_INGEST_QUEUE.send(await stripeImportRunEnvelope({
        fromDate,
        toDateExclusive,
        sourceRecords,
        entries: envelopes.length,
        sourceHash,
        status: failureCode === undefined ? "succeeded" : "partial",
        completedAt: Date.now(),
        ...(failureCode === undefined ? {} : { failureCode }),
      }), { contentType: "json" });
    } catch (error) {
      throw connectorFailure("queue_delivery", error, "Stripe billing queue delivery failed");
    }
  } catch (error) {
    const failureCode = connectorFailureCode(error);
    console.error(JSON.stringify({ event: "stripe_billing_import_failed", failureCode }));
    await publishFailedImportRun(env, fromDate, toDateExclusive, sourceRecords, envelopes.length, Date.now(), failureCode);
    throw error;
  }
}

export async function normalizeStripeBalanceTransaction(
  transaction: StripeBalanceTransaction,
  environment: "dev" | "prod",
): Promise<MutationEnvelope[] | null> {
  const id = requiredId(transaction.id);
  const amount = requiredMinorUnits(transaction.amount, "amount");
  const fee = requiredMinorUnits(transaction.fee ?? 0, "fee");
  const currency = String(transaction.currency ?? "").trim().toUpperCase();
  if (!/^[A-Z]{3}$/.test(currency)) throw new Error("invalid Stripe currency");
  if (currency !== "USD") return null;
  const createdSeconds = requiredCreated(transaction.created);
  const incurredAt = createdSeconds * 1_000;
  const invoiceMonth = new Date(incurredAt).toISOString().slice(0, 7);
  const category = safeDimension(transaction.reporting_category ?? transaction.type ?? "other");
  const isTransfer = category.includes("payout") || category === "transfer" || category === "connect_collection_transfer";
  const isRefund = amount < 0 || category.includes("refund") || category.includes("dispute");
  const userId = stripeUserId(transaction.source);
  const stable = stableStripeRecord(transaction);
  const grossEntryId = `stripe:${(await sha256(id)).slice(0, 40)}`;
  const grossMicros = minorUnitsToUsdMicros(amount);
  const grossDirection = isTransfer ? "ADJUSTMENT" : isRefund ? "REFUND" : "REVENUE";
  const grossMetadata: Record<string, string> = {
    accountingClass: isTransfer ? "cash_transfer" : "revenue",
    stripeType: safeDimension(transaction.type ?? "unknown"),
    stripeReportingCategory: category,
  };
  const gross = await stripeEntryEnvelope({
    entryId: grossEntryId,
    sourceRecordId: id,
    direction: grossDirection,
    originalMinorUnits: magnitude(amount),
    usdMicros: grossMicros,
    incurredAt,
    invoiceMonth,
    environment,
    service: isTransfer ? "payouts" : "payments",
    sku: category,
    sourceHash: await sha256(canonicalJson({ stable, accountingPart: "gross" })),
    metadata: grossMetadata,
    userId,
  });
  const results = [gross];
  if (fee !== 0) {
    const feeEntryId = `stripe-fee:${(await sha256(id)).slice(0, 40)}`;
    results.push(await stripeEntryEnvelope({
      entryId: feeEntryId,
      sourceRecordId: `${id}:fee`,
      direction: fee < 0 ? "CREDIT" : "FEE",
      originalMinorUnits: magnitude(fee),
      usdMicros: minorUnitsToUsdMicros(fee),
      incurredAt,
      invoiceMonth,
      environment,
      service: "payment-processing",
      sku: category,
      sourceHash: await sha256(canonicalJson({ stable, accountingPart: "fee" })),
      metadata: { accountingClass: "expense", stripeParentId: id },
      userId,
    }));
  }
  return results;
}

interface StripeEntryInput {
  entryId: string;
  sourceRecordId: string;
  direction: "FEE" | "REFUND" | "REVENUE" | "CREDIT" | "ADJUSTMENT";
  originalMinorUnits: number;
  usdMicros: number;
  incurredAt: number;
  invoiceMonth: string;
  environment: "dev" | "prod";
  service: string;
  sku: string;
  sourceHash: string;
  metadata: Record<string, string>;
  userId: string | null;
}

async function stripeEntryEnvelope(input: StripeEntryInput): Promise<MutationEnvelope> {
  const allocations = input.userId === null || input.direction === "ADJUSTMENT" ? [] : [{
    id: `allocation:${(await sha256(`${input.entryId}:${input.userId}`)).slice(0, 40)}`,
    entryId: input.entryId,
    project: "samurai",
    userId: input.userId,
    allocatedUsdMicros: input.usdMicros,
    method: "DIRECT",
  }];
  const payload = {
    entry: {
      id: input.entryId,
      source: "stripe",
      sourceRecordId: input.sourceRecordId,
      direction: input.direction,
      amount: { currency: "USD", minorUnits: input.originalMinorUnits, scale: 2 },
      usdMicros: input.usdMicros,
      incurredAt: input.incurredAt,
      invoiceMonth: input.invoiceMonth,
      project: "samurai",
      environment: input.environment,
      vendor: "stripe",
      service: input.service,
      sku: input.sku,
      status: "FINALIZED",
      reconciliationKey: `stripe:${input.invoiceMonth}`,
      sourceHash: input.sourceHash,
      metadata: input.metadata,
    },
    allocations,
  };
  return makeMutationEnvelope({
    id: input.entryId,
    aggregateType: "finops.ingest",
    aggregateId: "samurai",
    expectedRevision: 0,
    authorityEpoch: 1,
    occurredAt: new Date(input.incurredAt).toISOString(),
    payload,
  });
}

async function fetchStripePage(
  token: string,
  fromSeconds: number,
  toSeconds: number,
  cursor: string | null,
): Promise<StripePageResult> {
  const url = new URL(STRIPE_API);
  url.searchParams.set("limit", String(PAGE_SIZE));
  url.searchParams.set("created[gte]", String(fromSeconds));
  url.searchParams.set("created[lt]", String(toSeconds));
  url.searchParams.append("expand[]", "data.source");
  if (cursor) url.searchParams.set("starting_after", cursor);
  const response = await fetch(url, {
    headers: { authorization: `Bearer ${token}`, accept: "application/json" },
  });
  if (!response.ok) {
    const failureCode = response.status === 401 || response.status === 403
      ? "provider_authorization"
      : "provider_api";
    throw new ConnectorFailure(failureCode, "Stripe balance ledger request failed");
  }
  const body = await parseBoundedJson(response, MAX_API_RESPONSE_BYTES, "provider_schema");
  if (!isStripeList(body)) {
    throw new ConnectorFailure("provider_schema", "Stripe balance ledger response has an unexpected shape");
  }
  return { transactions: body.data, hasMore: body.has_more };
}

function isStripeList(value: unknown): value is Required<Pick<StripeList, "object" | "data" | "has_more">> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return false;
  const candidate = value as Record<string, unknown>;
  return candidate.object === "list" && Array.isArray(candidate.data) &&
    candidate.data.every((item) => typeof item === "object" && item !== null && !Array.isArray(item)) &&
    typeof candidate.has_more === "boolean";
}

function stripeWindow(now: Date, coverage: { date: string; epochSeconds: number }): {
  fromSeconds: number;
  toSeconds: number;
  fromDate: string;
  toDateExclusive: string;
} {
  const tomorrow = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
  const from = new Date(tomorrow.getTime() - 35 * 86_400_000);
  return {
    fromSeconds: Math.max(Math.floor(from.getTime() / 1_000), coverage.epochSeconds),
    toSeconds: Math.floor(tomorrow.getTime() / 1_000),
    fromDate: [from.toISOString().slice(0, 10), coverage.date].sort().at(-1)!,
    toDateExclusive: tomorrow.toISOString().slice(0, 10),
  };
}

function stableStripeRecord(transaction: StripeBalanceTransaction): Record<string, unknown> {
  return {
    id: transaction.id ?? null,
    amount: transaction.amount ?? null,
    fee: transaction.fee ?? 0,
    currency: transaction.currency ?? null,
    created: transaction.created ?? null,
    type: transaction.type ?? null,
    reportingCategory: transaction.reporting_category ?? null,
    sourceId: typeof transaction.source === "string" ? transaction.source : transaction.source?.id ?? null,
    samuraiUserId: stripeUserId(transaction.source),
  };
}

function stripeUserId(source: StripeBalanceTransaction["source"]): string | null {
  if (!source || typeof source === "string") return null;
  const candidate = source.metadata?.samurai_user_id?.trim() ?? "";
  return SAMURAI_USER_ID.test(candidate) ? candidate : null;
}

function requiredId(value: unknown): string {
  const id = String(value ?? "").trim();
  if (!/^[A-Za-z0-9_]{3,180}$/.test(id)) throw new Error("invalid Stripe record id");
  return id;
}

function requiredMinorUnits(value: unknown, label: string): number {
  if (!Number.isSafeInteger(value) || value === Number.MIN_SAFE_INTEGER) throw new Error(`invalid Stripe ${label}`);
  return value as number;
}

function requiredCreated(value: unknown): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0 || (value as number) > 253_402_300_799) {
    throw new Error("invalid Stripe created timestamp");
  }
  return value as number;
}

function magnitude(value: number): number {
  const result = Math.abs(value);
  if (!Number.isSafeInteger(result)) throw new Error("Stripe amount exceeds the fixed-point range");
  return result;
}

function minorUnitsToUsdMicros(value: number): number {
  const result = magnitude(value) * 10_000;
  if (!Number.isSafeInteger(result)) throw new Error("Stripe amount exceeds USD micro precision");
  return result;
}

function safeDimension(value: string): string {
  const normalized = value.replace(/[\r\n\t]/g, " ").trim().slice(0, 120);
  return normalized || "unknown";
}

async function stripeImportRunEnvelope(input: {
  fromDate: string;
  toDateExclusive: string;
  sourceRecords: number;
  entries: number;
  sourceHash: string;
  status: "succeeded" | "partial" | "failed";
  completedAt: number;
  failureCode?: ConnectorFailureCode;
}): Promise<MutationEnvelope> {
  const id = `import:stripe:${input.toDateExclusive}:${input.sourceHash.slice(0, 16)}`;
  return makeMutationEnvelope({
    id,
    aggregateType: "finops.import.run",
    aggregateId: "stripe",
    expectedRevision: 0,
    authorityEpoch: 1,
    occurredAt: new Date(input.completedAt).toISOString(),
    payload: {
      id,
      source: "stripe",
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
      source: "stripe",
      fromDate,
      toDateExclusive,
      status: "failed",
      failureCode,
    }));
    await env.FINOPS_INGEST_QUEUE.send(await stripeImportRunEnvelope({
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
      event: "stripe_billing_failed_import_run_publish_failed",
      failureCode,
    }));
  }
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
