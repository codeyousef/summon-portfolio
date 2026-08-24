import {
  ContractError,
  readMutationReceipt,
  readPhotographyStagingReceipt,
  validateDeadLetterReceipt,
  validateMutationEnvelope,
} from "./contracts";
import { portfolioContainer, seenPlaygroundContainer } from "./containers";
import { authorizedInternalRequest } from "./internal-auth";
import { classifyRoute, containerRouteUrl, shouldTryStaticAsset, withContainerRouteHost } from "./routing";
import { handleInternalMedia, servePublicObject } from "./r2";
import type { DeadLetterReceipt, Env, MutationEnvelope } from "./types";
import { relayCloudflareBilling } from "./cloudflare-billing";
import { handleFinOpsReceipt } from "./receipts";
import { healthResponseBody, queueHealthResponseBody } from "./health";
import { isAcknowledgedLegacyGcpRollupConflict } from "./finops-replay";
import {
  readAllocationBackfillResult,
  readAllocationProjectionReconciliation,
  shouldRunAllocationReconciliation,
} from "./reconciliation";
import { relayStripeBilling } from "./stripe-billing";
import { relayGcpBilling } from "./gcp-billing";
import { brokerAccessAssertion } from "./gcp-wif";
import { inventoryReceiptExpiry, type ReceiptAttachmentProbe } from "./receipt-expiry";
import { readBoundedBody, readBoundedJsonRequest } from "./bounded-body";
import { prepareBoundedFinOpsForward } from "./forward-body";
import { storePhotographyStagingReceipt } from "./migration-receipts";
import { finOpsCoverageStart, requireEnvelopeWithinFinOpsCoverage } from "./finops-coverage";
import { deadLetterDestination, enforceFinOpsCoverageForQueue } from "./queue-isolation";
import { replaceSessionCookie } from "./session-cookie";

const SESSION_ID_PATTERN = /^[A-Za-z0-9_-]{32}$/;
const CONTAINER_PORT_READY_TIMEOUT_MS = 60_000;
const MAX_MUTATION_ENVELOPE_BYTES = 132 * 1024;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (shouldTryStaticAsset(request)) {
      const asset = await env.ASSETS.fetch(request);
      if (asset.status !== 404) return withSecurityHeaders(asset);
    }

    const kind = classifyRoute(url.pathname);
    if (kind === "health") return health(env, url.pathname);
    if (kind === "internal") return handleInternal(request, env);
    if (kind === "media") return servePublicObject(request, env.PORTFOLIO_MEDIA);
    if (kind === "docs") return servePublicObject(request, env.PORTFOLIO_DOCS);

    const session = readOrCreateSession(request, env.PORTFOLIO_SESSION_COOKIE);
    const response = kind === "seen-playground"
      ? await forward(request, seenPlaygroundContainer(session.id, env), env, session, kind)
      : await forward(request, portfolioContainer(session.id, env), env, session, kind);
    return withSecurityHeaders(response);
  },

  async queue(batch: MessageBatch<MutationEnvelope>, env: Env): Promise<void> {
    const redriving = batch.queue === env.PORTFOLIO_ASYNC_DLQ_NAME;
    const enforceFinOpsCoverage = enforceFinOpsCoverageForQueue(batch.queue, env);
    for (const message of batch.messages) await processMessage(message, env, redriving, enforceFinOpsCoverage);
  },

  async scheduled(event: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    if (event.cron === "1 5 * * *") ctx.waitUntil(relayGcpBilling(env));
    if (event.cron === "11 5 * * *") ctx.waitUntil(relayCloudflareBilling(env));
    if (event.cron === "21 5 * * *") ctx.waitUntil(relayStripeBilling(env));
    if (event.cron === "31 5 * * *") ctx.waitUntil(materializeRecurringExpenses(env));
    if (event.cron === "51 5 * * *") ctx.waitUntil(reconcileFinOpsAllocations(env));
  },
} satisfies ExportedHandler<Env, MutationEnvelope>;

async function materializeRecurringExpenses(env: Env): Promise<void> {
  const today = new Date();
  const toDateExclusive = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() + 1));
  const fromDate = new Date(Math.max(
    toDateExclusive.getTime() - 35 * 86_400_000,
    finOpsCoverageStart(env).epochMillis,
  ));
  if (fromDate >= toDateExclusive) {
    console.log(JSON.stringify({ event: "recurring_expense_materialization_skipped", reason: "coverage_not_started" }));
    return;
  }
  const container = portfolioContainer("finops-recurring", env);
  await container.startAndWaitForPorts({
    cancellationOptions: { portReadyTimeoutMS: CONTAINER_PORT_READY_TIMEOUT_MS },
  });
  const response = await container.fetch(new Request("https://container.internal/internal/finops/recurring/materialize", {
    method: "POST",
    headers: {
      authorization: `Bearer ${env.FINOPS_INTERNAL_INGEST_TOKEN}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      fromDate: fromDate.toISOString().slice(0, 10),
      toDateExclusive: toDateExclusive.toISOString().slice(0, 10),
    }),
  }));
  if (!response.ok) throw new Error(`recurring expense materialization failed with HTTP ${response.status}`);
}

async function reconcileFinOpsAllocations(env: Env): Promise<void> {
  if (env.PORTFOLIO_ENV !== "dev") throw new Error("automatic FinOps reconciliation is dev-only until production approval");
  if (env.FIRESTORE_WRITE_MODE !== "target") {
    console.log("FinOps allocation reconciliation skipped: named dev Firestore is not authoritative");
    return;
  }
  if (env.FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY === "true") {
    console.log("FinOps allocation reconciliation skipped: projection gate is already enabled");
    return;
  }
  if (!shouldRunAllocationReconciliation(
    env.PORTFOLIO_ENV,
    env.FIRESTORE_WRITE_MODE,
    env.FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY,
  )) throw new Error("FinOps allocation reconciliation gate is inconsistent");
  const tomorrow = new Date();
  tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
  const to = tomorrow.toISOString().slice(0, 10);
  const container = portfolioContainer("finops-reconcile", env);
  await container.startAndWaitForPorts({
    cancellationOptions: { portReadyTimeoutMS: CONTAINER_PORT_READY_TIMEOUT_MS },
  });
  let cursor: string | null = null;
  let processed = 0;
  let residualAllocations = 0;
  for (let page = 0; page < 25; page += 1) {
    const url = new URL("https://container.internal/internal/finops/reconcile");
    url.searchParams.set("from", "0");
    url.searchParams.set("to", to);
    url.searchParams.set("limit", "500");
    if (cursor) url.searchParams.set("cursor", cursor);
    const response = await container.fetch(new Request(url, {
      method: "POST",
      headers: { authorization: `Bearer ${env.FINOPS_INTERNAL_INGEST_TOKEN}` },
    }));
    if (!response.ok) throw new Error(`FinOps allocation reconciliation failed with HTTP ${response.status}`);
    const result = await readAllocationBackfillResult(response);
    processed += result.processed;
    residualAllocations += result.residualAllocations;
    if (!result.nextCursor) {
      const projectionResponse = await container.fetch(new Request(
        "https://container.internal/internal/finops/projection-reconciliation",
        { method: "POST", headers: { authorization: `Bearer ${env.FINOPS_INTERNAL_INGEST_TOKEN}` } },
      ));
      if (!projectionResponse.ok) throw new Error(`FinOps projection reconciliation failed with HTTP ${projectionResponse.status}`);
      const projections = await readAllocationProjectionReconciliation(projectionResponse);
      if (!projections.ready) {
        throw new Error("FinOps projection reconciliation did not prove exact parity");
      }
      console.log("FinOps allocation reconciliation completed", {
        processed,
        residualAllocations,
        projectionCount: projections.actualCount,
      });
      return;
    }
    if (result.processed === 0 || result.nextCursor === cursor) {
      throw new Error("FinOps allocation reconciliation cursor did not advance");
    }
    cursor = result.nextCursor;
  }
  throw new Error("FinOps allocation reconciliation exceeded its 25-page safety bound");
}

async function health(env: Env, pathname: string): Promise<Response> {
  if (pathname === "/health/queues") {
    const [work, finOpsWork, deadLetter, parking] = await Promise.all([
      env.PORTFOLIO_ASYNC_QUEUE.metrics(),
      env.FINOPS_INGEST_QUEUE.metrics(),
      env.PORTFOLIO_ASYNC_DLQ.metrics(),
      env.PORTFOLIO_ASYNC_PARKING.metrics(),
    ]);
    return Response.json(queueHealthResponseBody(work, finOpsWork, deadLetter, parking), {
      headers: { "cache-control": "no-store" },
    });
  }
  return Response.json(healthResponseBody(env, pathname), { headers: { "cache-control": "no-store" } });
}

export async function handleInternal(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  if (url.pathname === "/internal/gcp-wif/assertion") return brokerAccessAssertion(request, env);
  if (url.pathname === "/internal/media/v1/asset") {
    return handleInternalMedia(
      request,
      env.PORTFOLIO_MEDIA,
      Number(env.PHOTOGRAPHY_MAX_UPLOAD_BYTES),
      env.EDGE_ORIGIN_TOKEN,
    );
  }
  if (url.pathname === "/internal/finops/v1/receipt") {
    if (!env.FINOPS_RECEIPTS) {
      if (!(await authorizedInternalRequest(request, env.EDGE_ORIGIN_TOKEN))) {
        return new Response("Unauthorized", { status: 401, headers: { "cache-control": "no-store" } });
      }
      return Response.json({ error: "receipt storage is not configured" }, {
        status: 503,
        headers: { "cache-control": "no-store" },
      });
    }
    return handleFinOpsReceipt(request, env.FINOPS_RECEIPTS, env.EDGE_ORIGIN_TOKEN);
  }
  if (!(await authorizedInternalRequest(request, env.EDGE_ORIGIN_TOKEN))) {
    return new Response("Unauthorized", { status: 401, headers: { "cache-control": "no-store" } });
  }
  if (url.pathname === "/internal/finops/v1/receipt-expiry-dry-run") {
    if (request.method !== "GET") return new Response("Method not allowed", { status: 405, headers: { allow: "GET" } });
    if (!env.FINOPS_RECEIPTS) return Response.json({ error: "receipt storage is not configured" }, { status: 503 });
    const limitValue = url.searchParams.get("limit");
    const limit = limitValue == null ? undefined : Number(limitValue);
    const cursor = url.searchParams.get("cursor") ?? undefined;
    if ((limit !== undefined && (!Number.isSafeInteger(limit) || limit < 1 || limit > 100)) || (cursor?.length ?? 0) > 2048) {
      return Response.json({ error: "invalid expiry inventory page" }, { status: 400 });
    }
    const container = portfolioContainer("finops-receipt-expiry", env);
    await container.startAndWaitForPorts({
      cancellationOptions: { portReadyTimeoutMS: CONTAINER_PORT_READY_TIMEOUT_MS },
    });
    const probe = async (uploadId: string, key: string, sha256: string): Promise<ReceiptAttachmentProbe> => {
      const response = await container.fetch(new Request(
        `https://container.internal/internal/finops/receipt-attachments/${uploadId}`,
        { headers: { authorization: `Bearer ${env.FINOPS_INTERNAL_INGEST_TOKEN}` } },
      ));
      if (response.status === 404) return { attached: false, exactMatch: false };
      if (!response.ok) throw new Error(`attachment probe failed with HTTP ${response.status}`);
      const attachment = await readBoundedJsonResponse(response, 4 * 1024) as {
        uploadId?: unknown;
        storageKey?: unknown;
        sha256?: unknown;
      };
      return {
        attached: true,
        exactMatch: attachment.uploadId === uploadId && attachment.storageKey === key && attachment.sha256 === sha256,
      };
    };
    const result = await inventoryReceiptExpiry(env.FINOPS_RECEIPTS, probe, {
      ...(limit === undefined ? {} : { limit }),
      ...(cursor === undefined ? {} : { cursor }),
    });
    return Response.json(result, { headers: { "cache-control": "private, no-store" } });
  }
  if (url.pathname.startsWith("/internal/session/")) {
    const sessionId = request.headers.get("x-portfolio-session-id");
    if (!sessionId || !SESSION_ID_PATTERN.test(sessionId)) return new Response("Invalid session", { status: 400 });
    const stub = env.PORTFOLIO_SESSIONS.getByName(`session:${sessionId}`, { locationHint: "me" });
    const target = new URL(request.url);
    target.pathname = url.pathname.slice("/internal/session".length);
    return stub.fetch(new Request(target, request));
  }
  if (url.pathname === "/internal/async/enqueue" && request.method === "POST") {
    try {
      const envelope = await validateMutationEnvelope(
        await readBoundedJsonRequest(request, MAX_MUTATION_ENVELOPE_BYTES),
      );
      requireEnvelopeWithinFinOpsCoverage(envelope, env);
      const queue = envelope.aggregateType.startsWith("finops.")
        ? env.FINOPS_INGEST_QUEUE
        : env.PORTFOLIO_ASYNC_QUEUE;
      await queue.send(envelope, { contentType: "json" });
      return Response.json({ accepted: true, id: envelope.id }, { status: 202 });
    } catch (error) {
      return Response.json({ error: safeError(error) }, { status: 400 });
    }
  }
  return new Response("Not found", { status: 404 });
}

async function forward(
  request: Request,
  container: ReturnType<typeof portfolioContainer> | ReturnType<typeof seenPlaygroundContainer>,
  env: Env,
  session: { id: string; created: boolean },
  kind: ReturnType<typeof classifyRoute>,
): Promise<Response> {
  const prepared = await prepareBoundedFinOpsForward(request);
  if (prepared instanceof Response) return prepared;
  const pathname = new URL(prepared.url).pathname;
  const deploymentEnvironment = env.PORTFOLIO_ENV === "prod" ? "prod" : "dev";
  const headers = withContainerRouteHost(prepared.headers, kind, pathname, deploymentEnvironment);
  replaceSessionCookie(headers, env.PORTFOLIO_SESSION_COOKIE, session.created ? null : session.id);
  for (const name of ["x-edge-origin-token", "x-portfolio-session-id", "x-portfolio-session-endpoint"]) headers.delete(name);
  headers.set("x-portfolio-edge-request-id", crypto.randomUUID());
  headers.set("x-portfolio-session-id", session.id);
  headers.set("x-portfolio-session-endpoint", `${env.PORTFOLIO_CANONICAL_ORIGIN}/internal/session`);
  headers.set("x-forwarded-proto", "https");
  const routedRequest = new Request(containerRouteUrl(prepared.url, kind, deploymentEnvironment), {
    method: prepared.method,
    headers,
    body: prepared.body,
    redirect: prepared.redirect,
    ...(prepared.cf === undefined ? {} : { cf: prepared.cf }),
    signal: prepared.signal,
  });
  // The SDK's default cold-start budget is only 20 seconds, while this JVM can
  // need longer. The Container-owned RPC applies the extended wait only when
  // its persisted state is not healthy; warm traffic goes directly through
  // containerFetch instead of paying a redundant port probe on every request.
  const response = await container.fetchWithExtendedColdStart(routedRequest);
  return new Response(response.body, response);
}

async function processMessage(
  message: Message<MutationEnvelope>,
  env: Env,
  redriving = false,
  enforceFinOpsCoverage = false,
): Promise<void> {
  if (redriving) {
    try {
      const receipt = validateDeadLetterReceipt(message.body);
      await env.PORTFOLIO_ASYNC_PARKING.send(receipt, { contentType: "json" });
      message.ack();
      return;
    } catch {
      // Older DLQs may also contain original mutation envelopes. Continue
      // through the normal idempotent path for those records.
    }
  }
  let envelope: MutationEnvelope;
  try {
    envelope = await validateMutationEnvelope(message.body);
    if (enforceFinOpsCoverage) requireEnvelopeWithinFinOpsCoverage(envelope, env);
  } catch (error) {
    await sendDeadLetter(env, message, null, safeError(error), redriving, enforceFinOpsCoverage);
    message.ack();
    return;
  }

  const idempotency = env.PORTFOLIO_SESSIONS.getByName(`idempotency:${envelope.id}`, { locationHint: "me" });
  const claim = await idempotency.fetch("https://idempotency.internal/v1/idempotency/claim", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ fingerprint: envelope.payloadSha256 }),
  });
  if (claim.ok) {
    const claimed = await readBoundedJsonResponse(claim, 2 * 1024)
      .catch(() => ({})) as { state?: string; claimToken?: string };
    if (claimed.state !== "claimed" || !claimed.claimToken) {
      message.retry({ delaySeconds: 10 });
      return;
    }
    const result = await runAsyncOperation(envelope, env);
    const receipt = envelope.aggregateType === "portfolio.photography.backfill"
      ? await readPhotographyStagingReceipt(result.clone(), envelope)
      : await readMutationReceipt(result.clone(), envelope);
    if (!receipt) {
      console.error("Async operation did not produce a valid receipt", {
        aggregateType: envelope.aggregateType,
        status: result.status,
        reason: await safeAsyncOperationReason(result),
      });
      await idempotency.fetch("https://idempotency.internal/v1/idempotency/abandon", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ fingerprint: envelope.payloadSha256, claimToken: claimed.claimToken }),
      });
      message.retry({ delaySeconds: Math.min(300, 10 * 2 ** Math.min(message.attempts, 5)) });
      return;
    }
    if (receipt.mirrorState === "staged") {
      await storePhotographyStagingReceipt(env.PORTFOLIO_LOGS, receipt);
    }
    const completed = await idempotency.fetch("https://idempotency.internal/v1/idempotency/complete", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ fingerprint: envelope.payloadSha256, claimToken: claimed.claimToken }),
    });
    if (!completed.ok) {
      message.retry({ delaySeconds: 10 });
      return;
    }
    message.ack();
    return;
  }
  const state = await readBoundedJsonResponse(claim, 2 * 1024)
    .catch(() => ({})) as { state?: string; error?: string; retryAfterMs?: number };
  if (state.state === "complete") message.ack();
  else if (state.error === "idempotency_key_reused") {
    await sendDeadLetter(
      env,
      message,
      envelope,
      "idempotency key reused with a different payload",
      redriving,
      enforceFinOpsCoverage,
    );
    message.ack();
  } else {
    const retryAfterSeconds = Math.max(10, Math.ceil((state.retryAfterMs ?? 10_000) / 1_000));
    message.retry({ delaySeconds: Math.min(43200, retryAfterSeconds) });
  }
}

async function safeAsyncOperationReason(response: Response): Promise<string> {
  const allowed = new Set([
    "migration execution is disabled",
    "photography migration execution is disabled",
    "invalid photography staging authority",
    "invalid photography backfill payload",
    "photography staging failed",
    "migration mirror remains pending",
    "migration reconciliation requires another pass",
  ]);
  try {
    const body = await readBoundedJsonResponse(response, 1024) as { error?: unknown; stage?: unknown };
    if (typeof body.error !== "string" || !allowed.has(body.error)) return "unclassified";
    if (body.error !== "photography staging failed") return body.error;
    const stages = new Set([
      "source-read", "source-validate", "key-derive", "source-write", "source-verify",
      "target-write", "target-verify", "unclassified",
    ]);
    const stage = typeof body.stage === "string" && stages.has(body.stage) ? body.stage : "unclassified";
    return `${body.error}:${stage}`;
  } catch {
    return "unreadable";
  }
}

async function runAsyncOperation(envelope: MutationEnvelope, env: Env): Promise<Response> {
  if (envelope.aggregateType === "finops.ingest" || envelope.aggregateType === "finops.samurai.ingest" || envelope.aggregateType === "finops.import.run") {
    const container = portfolioContainer(envelope.aggregateId, env);
    await container.startAndWaitForPorts({
      cancellationOptions: { portReadyTimeoutMS: CONTAINER_PORT_READY_TIMEOUT_MS },
    });
    const response = await container.fetch(new Request(
      `https://container.internal/internal/finops/${envelope.aggregateType === "finops.import.run" ? "import-run" : "events"}`,
      {
        method: "POST",
        headers: {
          authorization: `Bearer ${env.FINOPS_INTERNAL_INGEST_TOKEN}`,
          "content-type": "application/json",
          "x-idempotency-key": envelope.id,
        },
        body: JSON.stringify(envelope.payload),
      },
    ));
    if (!response.ok) {
      const error = await safeFinOpsError(response.clone());
      const source = finOpsSource(envelope.payload);
      if (isAcknowledgedLegacyGcpRollupConflict(env.PORTFOLIO_ENV, source, response.status, error)) {
        console.warn("Acknowledged legacy dev GCP rollup replay", { aggregateType: envelope.aggregateType });
        return mutationReceiptResponse(envelope);
      }
      console.error("FinOps operation rejected", {
        aggregateType: envelope.aggregateType,
        source,
        status: response.status,
        error,
      });
      return response;
    }
    return mutationReceiptResponse(envelope);
  }
  const allowed = new Set([
    "portfolio.migration.reconcile",
    "portfolio.photography.backfill",
    "portfolio.finops.rollups.v2.backfill",
    "portfolio.finops.rollups.v2.reconcile",
  ]);
  if (!allowed.has(envelope.aggregateType)) return new Response("Unsupported operation", { status: 422 });
  const request = new Request(`${env.PORTFOLIO_CANONICAL_ORIGIN}/internal/edge/jobs/${envelope.aggregateType}`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${env.EDGE_ORIGIN_TOKEN}`,
      "content-type": "application/json",
      "x-idempotency-key": envelope.id,
    },
    body: JSON.stringify(envelope),
  });
  const container = portfolioContainer(envelope.aggregateId, env);
  await container.startAndWaitForPorts({
    cancellationOptions: { portReadyTimeoutMS: CONTAINER_PORT_READY_TIMEOUT_MS },
  });
  return container.fetch(request);
}

function mutationReceiptResponse(envelope: MutationEnvelope): Response {
  return Response.json({
    id: envelope.id,
    committedEpoch: envelope.authorityEpoch,
    newRevision: envelope.expectedRevision,
    firestoreCommitTime: new Date().toISOString(),
    mirrorState: "mirrored",
  });
}

function finOpsSource(payload: unknown): string {
  if (typeof payload !== "object" || payload === null) return "unknown";
  const record = payload as Record<string, unknown>;
  if (typeof record.source === "string") return record.source.slice(0, 64);
  if (typeof record.entry !== "object" || record.entry === null) return "unknown";
  const source = (record.entry as Record<string, unknown>).source;
  return typeof source === "string" ? source.slice(0, 64) : "unknown";
}

async function safeFinOpsError(response: Response): Promise<string> {
  try {
    const decoded = await readBoundedJsonResponse(response, 4 * 1024) as { error?: unknown };
    return typeof decoded.error === "string" ? decoded.error.slice(0, 256) : `HTTP ${response.status}`;
  } catch {
    return `HTTP ${response.status}`;
  }
}

async function readBoundedJsonResponse(response: Response, maximumBytes: number): Promise<unknown> {
  const bytes = await readBoundedBody(response.body, maximumBytes);
  if (bytes === null) throw new Error("response body too large");
  const text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes);
  return JSON.parse(text) as unknown;
}

async function sendDeadLetter(
  env: Env,
  message: Message<MutationEnvelope>,
  envelope: MutationEnvelope | null,
  reason: string,
  parking = false,
  finOps = false,
): Promise<void> {
  const receipt: DeadLetterReceipt = {
    envelopeId: envelope?.id ?? null,
    aggregateType: envelope?.aggregateType ?? null,
    payloadSha256: envelope?.payloadSha256 ?? null,
    sourceMessageId: message.id,
    attempts: message.attempts,
    reason: reason.slice(0, 512),
    failedAt: new Date().toISOString(),
  };
  const destination = deadLetterDestination(env, { parking, finOps });
  await destination.send(receipt, { contentType: "json" });
}

function readOrCreateSession(request: Request, cookieName: string): { id: string; created: boolean } {
  const cookies = request.headers.get("cookie")?.split(";") ?? [];
  for (const cookie of cookies) {
    const separator = cookie.indexOf("=");
    if (separator < 0 || cookie.slice(0, separator).trim() !== cookieName) continue;
    const value = cookie.slice(separator + 1).trim();
    if (SESSION_ID_PATTERN.test(value)) return { id: value, created: false };
  }
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  const id = btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
  return { id, created: true };
}

function withSecurityHeaders(response: Response): Response {
  const next = new Response(response.body, response);
  next.headers.set("x-content-type-options", "nosniff");
  next.headers.set("referrer-policy", "strict-origin-when-cross-origin");
  next.headers.set("permissions-policy", "camera=(), microphone=(), geolocation=()");
  return next;
}

function safeError(error: unknown): string {
  if (error instanceof ContractError || error instanceof SyntaxError) return error.message;
  return "request could not be processed";
}
