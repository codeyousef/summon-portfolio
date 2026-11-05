import { DurableObject } from "cloudflare:workers";
import { isRecord } from "./contracts";
import type { Env } from "./types";
import { readBoundedJsonRequest } from "./bounded-body";

interface SessionRecord {
  revision: number;
  expiresAt: number;
  values: Record<string, string>;
}

interface SessionReadResult extends SessionRecord {
  exists: boolean;
}

interface IdempotencyRecord {
  fingerprint: string;
  state: "leased" | "complete";
  leaseUntil: number;
  completedAt?: number;
  claimToken?: string;
  expiresAt: number;
}

const SESSION_KEY = "session:v1";
const IDEMPOTENCY_KEY = "idempotency:v1";
const MAX_SESSION_BYTES = 24 * 1024;
const SESSION_TTL_SECONDS = 24 * 60 * 60;
const IDEMPOTENCY_LEASE_MILLIS = 5 * 60 * 1000;
const IDEMPOTENCY_RECEIPT_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000;

export class PortfolioSessionDO extends DurableObject<Env> {
  override async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/v1/session/read") return this.readSession();
    if (request.method === "POST" && url.pathname === "/v1/session/compare-and-set") {
      return this.compareAndSetSession(request);
    }
    if (request.method === "DELETE" && url.pathname === "/v1/session") return this.deleteSession();
    if (request.method === "POST" && url.pathname === "/v1/idempotency/claim") {
      return this.claimIdempotency(request);
    }
    if (request.method === "POST" && url.pathname === "/v1/idempotency/complete") {
      return this.completeIdempotency(request);
    }
    if (request.method === "POST" && url.pathname === "/v1/idempotency/abandon") {
      return this.abandonIdempotency(request);
    }
    return json({ error: "not_found" }, 404);
  }

  private async readSession(): Promise<Response> {
    const now = Date.now();
    const stored = await this.ctx.storage.get<SessionRecord>(SESSION_KEY);
    if (stored && stored.expiresAt > now) return json({ ...stored, exists: true } satisfies SessionReadResult);
    if (stored) await this.ctx.storage.delete(SESSION_KEY);
    return json({
      exists: false,
      revision: 0,
      expiresAt: now + SESSION_TTL_SECONDS * 1000,
      values: {},
    } satisfies SessionReadResult);
  }

  private async compareAndSetSession(request: Request): Promise<Response> {
    const raw = await readLimitedJson(request, MAX_SESSION_BYTES);
    if (!isRecord(raw) || !Number.isSafeInteger(raw.expectedRevision) || !isStringRecord(raw.values)) {
      return json({ error: "invalid_session_update" }, 400);
    }
    const current = await this.ctx.storage.get<SessionRecord>(SESSION_KEY);
    const currentRevision = current?.revision ?? 0;
    if (raw.expectedRevision !== currentRevision) {
      return json({ error: "revision_conflict", currentRevision }, 409);
    }
    const next: SessionRecord = {
      revision: currentRevision + 1,
      expiresAt: Date.now() + SESSION_TTL_SECONDS * 1000,
      values: raw.values,
    };
    await this.ctx.storage.put(SESSION_KEY, next);
    await this.ctx.storage.setAlarm(next.expiresAt);
    return json(next);
  }

  private async deleteSession(): Promise<Response> {
    await this.ctx.storage.delete(SESSION_KEY);
    return new Response(null, { status: 204 });
  }

  private async claimIdempotency(request: Request): Promise<Response> {
    const raw = await readLimitedJson(request, 2048);
    if (!isRecord(raw) || typeof raw.fingerprint !== "string" || !/^[a-f0-9]{64}$/.test(raw.fingerprint)) {
      return json({ error: "invalid_fingerprint" }, 400);
    }
    const now = Date.now();
    const existing = await this.ctx.storage.get<IdempotencyRecord>(IDEMPOTENCY_KEY);
    if (existing && existing.expiresAt <= now) await this.ctx.storage.delete(IDEMPOTENCY_KEY);
    const active = existing?.expiresAt && existing.expiresAt > now ? existing : undefined;
    if (active && active.fingerprint !== raw.fingerprint) {
      return json({ error: "idempotency_key_reused" }, 409);
    }
    if (active?.state === "complete") return json({ state: "complete" });
    if (active && active.leaseUntil > now) {
      return json({ state: "leased", retryAfterMs: active.leaseUntil - now }, 409);
    }
    const claimToken = crypto.randomUUID();
    const record: IdempotencyRecord = {
      fingerprint: raw.fingerprint,
      state: "leased",
      leaseUntil: now + IDEMPOTENCY_LEASE_MILLIS,
      claimToken,
      expiresAt: now + IDEMPOTENCY_LEASE_MILLIS,
    };
    await this.ctx.storage.put(IDEMPOTENCY_KEY, record);
    await this.scheduleNextAlarm();
    return json({ state: "claimed", leaseUntil: record.leaseUntil, claimToken });
  }

  private async completeIdempotency(request: Request): Promise<Response> {
    const raw = await readLimitedJson(request, 2048);
    const existing = await this.ctx.storage.get<IdempotencyRecord>(IDEMPOTENCY_KEY);
    if (
      !existing || !isRecord(raw) || raw.fingerprint !== existing.fingerprint ||
      typeof raw.claimToken !== "string" || raw.claimToken !== existing.claimToken
    ) {
      return json({ error: "unknown_claim" }, 409);
    }
    const completedAt = Date.now();
    await this.ctx.storage.put(IDEMPOTENCY_KEY, {
      fingerprint: existing.fingerprint,
      state: "complete",
      completedAt,
      leaseUntil: 0,
      expiresAt: completedAt + IDEMPOTENCY_RECEIPT_TTL_MILLIS,
    } satisfies IdempotencyRecord);
    await this.scheduleNextAlarm();
    return json({ state: "complete" });
  }

  private async abandonIdempotency(request: Request): Promise<Response> {
    const raw = await readLimitedJson(request, 2048);
    const existing = await this.ctx.storage.get<IdempotencyRecord>(IDEMPOTENCY_KEY);
    if (
      !existing || existing.state !== "leased" || !isRecord(raw) ||
      raw.fingerprint !== existing.fingerprint || raw.claimToken !== existing.claimToken
    ) {
      return json({ error: "unknown_claim" }, 409);
    }
    await this.ctx.storage.delete(IDEMPOTENCY_KEY);
    await this.scheduleNextAlarm();
    return json({ state: "abandoned" });
  }

  override async alarm(): Promise<void> {
    const now = Date.now();
    const [session, idempotency] = await Promise.all([
      this.ctx.storage.get<SessionRecord>(SESSION_KEY),
      this.ctx.storage.get<IdempotencyRecord>(IDEMPOTENCY_KEY),
    ]);
    if (session && session.expiresAt <= now) await this.ctx.storage.delete(SESSION_KEY);
    if (idempotency && idempotency.expiresAt <= now) await this.ctx.storage.delete(IDEMPOTENCY_KEY);
    await this.scheduleNextAlarm();
  }

  private async scheduleNextAlarm(): Promise<void> {
    const [session, idempotency] = await Promise.all([
      this.ctx.storage.get<SessionRecord>(SESSION_KEY),
      this.ctx.storage.get<IdempotencyRecord>(IDEMPOTENCY_KEY),
    ]);
    const next = [session?.expiresAt, idempotency?.expiresAt]
      .filter((value): value is number => typeof value === "number" && value > Date.now())
      .sort((left, right) => left - right)[0];
    if (next) await this.ctx.storage.setAlarm(next);
    else await this.ctx.storage.deleteAlarm();
  }
}

async function readLimitedJson(request: Request, maxBytes: number): Promise<unknown> {
  return readBoundedJsonRequest(request, maxBytes);
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return isRecord(value) && Object.keys(value).length <= 32 && Object.values(value).every((entry) => typeof entry === "string");
}

function json(value: unknown, status = 200): Response {
  return Response.json(value, { status, headers: { "cache-control": "no-store" } });
}
