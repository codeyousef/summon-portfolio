import { DurableObject } from "cloudflare:workers";
import { ContractError, requireId, requireSha256, sha256Hex } from "./contracts";
import type { Env, TufOperation, TufRole } from "./types";

export interface ReservationRequest {
  idempotencyKey: string;
  aggregateId: string;
  expectedRevision: number;
  payloadSha256: string;
  expiresAt: string;
}

export interface ReservationReceipt {
  [key: string]: SqlStorageValue;
  idempotencyKey: string;
  aggregateId: string;
  expectedRevision: number;
  payloadSha256: string;
  expiresAt: string;
  state: "reserved" | "released";
}

export interface PromotionClaim {
  idempotencyKey: string;
  aggregateId: string;
  expectedRevision: number;
  inputSha256: string;
}

export interface PromotionReceipt extends PromotionClaim {
  [key: string]: SqlStorageValue;
  state: "claimed" | "complete" | "failed";
  detail: string | null;
  updatedAt: string;
}

export interface SigningReservation {
  [key: string]: SqlStorageValue;
  idempotencyKey: string;
  role: TufRole;
  operation: TufOperation;
  version: number;
  signedSha256: string;
  guardReceiptId: string;
  guardExpiresAt: string;
}

export interface TimestampCommitRequest extends SigningReservation {
  envelopeBase64: string;
  envelopeSha256: string;
  expectedEtag: string | null;
}

export interface TimestampCommitReceipt {
  [key: string]: SqlStorageValue;
  idempotencyKey: string;
  version: number;
  signedSha256: string;
  envelopeSha256: string;
  etag: string;
  committedAt: string;
}

export class SeenReservationDO extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.blockConcurrencyWhile(async () => {
      this.ctx.storage.sql.exec(`
        CREATE TABLE IF NOT EXISTS reservations (
          idempotency_key TEXT PRIMARY KEY,
          aggregate_id TEXT NOT NULL,
          expected_revision INTEGER NOT NULL,
          payload_sha256 TEXT NOT NULL,
          expires_at TEXT NOT NULL,
          state TEXT NOT NULL CHECK(state IN ('reserved', 'released'))
        )
      `);
    });
  }

  async reserve(input: ReservationRequest): Promise<ReservationReceipt> {
    const request = validateReservation(input);
    const existing = this.ctx.storage.sql.exec<ReservationReceipt>(
      "SELECT idempotency_key AS idempotencyKey, aggregate_id AS aggregateId, expected_revision AS expectedRevision, payload_sha256 AS payloadSha256, expires_at AS expiresAt, state FROM reservations WHERE idempotency_key = ?",
      request.idempotencyKey,
    ).toArray()[0];
    if (existing) {
      if (!sameReservation(existing, request)) throw new ContractError("idempotency key was reused for a different reservation");
      return existing;
    }
    const active = this.ctx.storage.sql.exec<{ idempotencyKey: string }>(
      "SELECT idempotency_key AS idempotencyKey FROM reservations WHERE aggregate_id = ? AND state = 'reserved' AND expires_at > ? LIMIT 1",
      request.aggregateId,
      new Date().toISOString(),
    ).toArray()[0];
    if (active) throw new ContractError("aggregate already has an active reservation");
    this.ctx.storage.sql.exec(
      "INSERT INTO reservations (idempotency_key, aggregate_id, expected_revision, payload_sha256, expires_at, state) VALUES (?, ?, ?, ?, ?, 'reserved')",
      request.idempotencyKey,
      request.aggregateId,
      request.expectedRevision,
      request.payloadSha256,
      request.expiresAt,
    );
    return { ...request, state: "reserved" };
  }

  async release(idempotencyKey: string): Promise<ReservationReceipt> {
    requireId(idempotencyKey, "idempotencyKey");
    this.ctx.storage.sql.exec(
      "UPDATE reservations SET state = 'released' WHERE idempotency_key = ? AND state = 'reserved'",
      idempotencyKey,
    );
    const result = this.ctx.storage.sql.exec<ReservationReceipt>(
      "SELECT idempotency_key AS idempotencyKey, aggregate_id AS aggregateId, expected_revision AS expectedRevision, payload_sha256 AS payloadSha256, expires_at AS expiresAt, state FROM reservations WHERE idempotency_key = ?",
      idempotencyKey,
    ).toArray()[0];
    if (!result) throw new ContractError("reservation does not exist");
    return result;
  }
}

export class SeenPromotionDO extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.blockConcurrencyWhile(async () => {
      this.ctx.storage.sql.exec(`
        CREATE TABLE IF NOT EXISTS promotions (
          idempotency_key TEXT PRIMARY KEY,
          aggregate_id TEXT NOT NULL,
          expected_revision INTEGER NOT NULL,
          input_sha256 TEXT NOT NULL,
          state TEXT NOT NULL CHECK(state IN ('claimed', 'complete', 'failed')),
          detail TEXT,
          updated_at TEXT NOT NULL
        )
      `);
    });
  }

  async claim(input: PromotionClaim): Promise<PromotionReceipt> {
    const claim = validatePromotionClaim(input);
    const existing = this.read(claim.idempotencyKey);
    if (existing) {
      if (!samePromotion(existing, claim)) throw new ContractError("idempotency key was reused for a different promotion");
      return existing;
    }
    const current = this.ctx.storage.sql.exec<PromotionReceipt>(
      "SELECT idempotency_key AS idempotencyKey, aggregate_id AS aggregateId, expected_revision AS expectedRevision, input_sha256 AS inputSha256, state, detail, updated_at AS updatedAt FROM promotions WHERE aggregate_id = ? AND state = 'claimed' LIMIT 1",
      claim.aggregateId,
    ).toArray()[0];
    if (current) throw new ContractError("aggregate already has an active promotion");
    const updatedAt = new Date().toISOString();
    this.ctx.storage.sql.exec(
      "INSERT INTO promotions (idempotency_key, aggregate_id, expected_revision, input_sha256, state, detail, updated_at) VALUES (?, ?, ?, ?, 'claimed', NULL, ?)",
      claim.idempotencyKey,
      claim.aggregateId,
      claim.expectedRevision,
      claim.inputSha256,
      updatedAt,
    );
    return { ...claim, state: "claimed", detail: null, updatedAt };
  }

  async finish(idempotencyKey: string, state: "complete" | "failed", detail: string | null): Promise<PromotionReceipt> {
    requireId(idempotencyKey, "idempotencyKey");
    if (detail !== null && (detail.length > 256 || /[\u0000-\u001f\u007f]/.test(detail))) {
      throw new ContractError("promotion detail is invalid");
    }
    const existing = this.read(idempotencyKey);
    if (!existing) throw new ContractError("promotion claim does not exist");
    if (existing.state !== "claimed" && existing.state !== state) throw new ContractError("promotion is already terminal");
    if (existing.state === state) return existing;
    const updatedAt = new Date().toISOString();
    this.ctx.storage.sql.exec(
      "UPDATE promotions SET state = ?, detail = ?, updated_at = ? WHERE idempotency_key = ? AND state = 'claimed'",
      state,
      detail,
      updatedAt,
      idempotencyKey,
    );
    return { ...existing, state, detail, updatedAt };
  }

  private read(idempotencyKey: string): PromotionReceipt | undefined {
    return this.ctx.storage.sql.exec<PromotionReceipt>(
      "SELECT idempotency_key AS idempotencyKey, aggregate_id AS aggregateId, expected_revision AS expectedRevision, input_sha256 AS inputSha256, state, detail, updated_at AS updatedAt FROM promotions WHERE idempotency_key = ?",
      idempotencyKey,
    ).toArray()[0];
  }
}

export class SeenSigningOrderDO extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.blockConcurrencyWhile(async () => {
      this.ctx.storage.sql.exec(`
        CREATE TABLE IF NOT EXISTS signing_reservations (
          idempotency_key TEXT PRIMARY KEY,
          role TEXT NOT NULL,
          operation TEXT NOT NULL,
          version INTEGER NOT NULL,
          signed_sha256 TEXT NOT NULL,
          guard_receipt_id TEXT NOT NULL UNIQUE,
          guard_expires_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS timestamp_commits (
          idempotency_key TEXT PRIMARY KEY,
          version INTEGER NOT NULL UNIQUE,
          signed_sha256 TEXT NOT NULL,
          envelope_sha256 TEXT NOT NULL,
          etag TEXT NOT NULL,
          committed_at TEXT NOT NULL
        )
      `);
    });
  }

  async reserveSigning(input: SigningReservation): Promise<SigningReservation> {
    const reservation = validateSigningReservation(input);
    const existing = this.ctx.storage.sql.exec<SigningReservation>(
      "SELECT idempotency_key AS idempotencyKey, role, operation, version, signed_sha256 AS signedSha256, guard_receipt_id AS guardReceiptId, guard_expires_at AS guardExpiresAt FROM signing_reservations WHERE idempotency_key = ?",
      reservation.idempotencyKey,
    ).toArray()[0];
    if (existing) {
      if (JSON.stringify(existing) !== JSON.stringify(reservation)) throw new ContractError("signing idempotency key was reused");
      return existing;
    }
    const latest = this.ctx.storage.sql.exec<{ version: number; signedSha256: string }>(
      "SELECT version, signed_sha256 AS signedSha256 FROM signing_reservations WHERE role = ? ORDER BY version DESC LIMIT 1",
      reservation.role,
    ).toArray()[0];
    if (latest && (reservation.version < latest.version || reservation.version === latest.version && reservation.signedSha256 !== latest.signedSha256)) {
      throw new ContractError("signing version is not monotonic");
    }
    this.ctx.storage.sql.exec(
      "INSERT INTO signing_reservations (idempotency_key, role, operation, version, signed_sha256, guard_receipt_id, guard_expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      reservation.idempotencyKey,
      reservation.role,
      reservation.operation,
      reservation.version,
      reservation.signedSha256,
      reservation.guardReceiptId,
      reservation.guardExpiresAt,
    );
    return reservation;
  }

  async commitTimestamp(input: TimestampCommitRequest): Promise<TimestampCommitReceipt> {
    const request = validateTimestampCommit(input);
    const prior = this.ctx.storage.sql.exec<TimestampCommitReceipt>(
      "SELECT idempotency_key AS idempotencyKey, version, signed_sha256 AS signedSha256, envelope_sha256 AS envelopeSha256, etag, committed_at AS committedAt FROM timestamp_commits WHERE idempotency_key = ?",
      request.idempotencyKey,
    ).toArray()[0];
    if (prior) {
      if (prior.envelopeSha256 !== request.envelopeSha256 || prior.version !== request.version) {
        throw new ContractError("timestamp idempotency key was reused");
      }
      return prior;
    }
    const reservation = this.ctx.storage.sql.exec<SigningReservation>(
      "SELECT idempotency_key AS idempotencyKey, role, operation, version, signed_sha256 AS signedSha256, guard_receipt_id AS guardReceiptId, guard_expires_at AS guardExpiresAt FROM signing_reservations WHERE idempotency_key = ?",
      request.idempotencyKey,
    ).toArray()[0];
    if (!reservation || reservation.role !== "timestamp" || JSON.stringify(reservation) !== JSON.stringify(signingFields(request))) {
      throw new ContractError("timestamp has no matching signing reservation");
    }
    if (Date.parse(reservation.guardExpiresAt) <= Date.now()) throw new ContractError("timestamp guard receipt expired before commit");

    const envelope = decodeBase64(request.envelopeBase64);
    if (await sha256Hex(envelope) !== request.envelopeSha256) throw new ContractError("timestamp envelope digest does not match");
    const key = `${safePrefix(this.env.SEEN_OBJECT_PREFIX)}/metadata/timestamp.json`;
    const current = await this.env.SEEN_METADATA.head(key);
    if ((current?.etag ?? null) !== request.expectedEtag) throw new ContractError("timestamp pointer changed after authorization");
    const written = await this.env.SEEN_METADATA.put(key, envelope, {
      onlyIf: request.expectedEtag === null
        ? { etagDoesNotMatch: "*" }
        : { etagMatches: request.expectedEtag },
      httpMetadata: {
        contentType: "application/vnd.seen.tuf+json",
        cacheControl: "public,max-age=300,must-revalidate",
      },
      customMetadata: {
        sha256: request.envelopeSha256,
        signedSha256: request.signedSha256,
        guardReceiptId: request.guardReceiptId,
      },
    });
    if (written === null) throw new ContractError("timestamp conditional write lost its compare-and-set race");
    const receipt: TimestampCommitReceipt = {
      idempotencyKey: request.idempotencyKey,
      version: request.version,
      signedSha256: request.signedSha256,
      envelopeSha256: request.envelopeSha256,
      etag: written.etag,
      committedAt: new Date().toISOString(),
    };
    this.ctx.storage.sql.exec(
      "INSERT INTO timestamp_commits (idempotency_key, version, signed_sha256, envelope_sha256, etag, committed_at) VALUES (?, ?, ?, ?, ?, ?)",
      receipt.idempotencyKey,
      receipt.version,
      receipt.signedSha256,
      receipt.envelopeSha256,
      receipt.etag,
      receipt.committedAt,
    );
    return receipt;
  }
}

export function validateReservation(input: ReservationRequest): ReservationRequest {
  requireId(input.idempotencyKey, "idempotencyKey");
  if (!/^[A-Za-z0-9][A-Za-z0-9:._/-]{2,255}$/.test(input.aggregateId)) throw new ContractError("aggregateId is invalid");
  if (!Number.isSafeInteger(input.expectedRevision) || input.expectedRevision < 0) throw new ContractError("expectedRevision is invalid");
  requireSha256(input.payloadSha256, "payloadSha256");
  const expiry = Date.parse(input.expiresAt);
  if (!Number.isFinite(expiry) || new Date(expiry).toISOString() !== input.expiresAt || expiry <= Date.now() || expiry > Date.now() + 15 * 60_000) {
    throw new ContractError("reservation expiry is invalid");
  }
  return { ...input };
}

export function validatePromotionClaim(input: PromotionClaim): PromotionClaim {
  requireId(input.idempotencyKey, "idempotencyKey");
  if (!/^[A-Za-z0-9][A-Za-z0-9:._/-]{2,255}$/.test(input.aggregateId)) throw new ContractError("aggregateId is invalid");
  if (!Number.isSafeInteger(input.expectedRevision) || input.expectedRevision < 0) throw new ContractError("expectedRevision is invalid");
  requireSha256(input.inputSha256, "inputSha256");
  return { ...input };
}

function validateSigningReservation(input: SigningReservation): SigningReservation {
  requireId(input.idempotencyKey, "idempotencyKey");
  requireId(input.guardReceiptId, "guardReceiptId");
  if (!Number.isSafeInteger(input.version) || input.version < 1 || input.version > Number.MAX_SAFE_INTEGER) {
    throw new ContractError("signing version is invalid");
  }
  requireSha256(input.signedSha256, "signedSha256");
  const expiry = Date.parse(input.guardExpiresAt);
  if (!Number.isFinite(expiry) || new Date(expiry).toISOString() !== input.guardExpiresAt || expiry <= Date.now() || expiry > Date.now() + 30_000) {
    throw new ContractError("guard receipt expiry is invalid");
  }
  return { ...input };
}

function validateTimestampCommit(input: TimestampCommitRequest): TimestampCommitRequest {
  validateSigningReservation(input);
  if (input.role !== "timestamp") throw new ContractError("only timestamp metadata can commit the timestamp pointer");
  requireSha256(input.envelopeSha256, "envelopeSha256");
  if (input.expectedEtag !== null && !/^[\x21-\x7e]{1,256}$/.test(input.expectedEtag)) throw new ContractError("expectedEtag is invalid");
  if (input.envelopeBase64.length === 0 || input.envelopeBase64.length > 1_398_104 || !/^[A-Za-z0-9+/]+={0,2}$/.test(input.envelopeBase64)) {
    throw new ContractError("timestamp envelope is invalid Base64");
  }
  return { ...input };
}

function validatePromotionState(state: string): PromotionReceipt["state"] {
  if (state === "claimed" || state === "complete" || state === "failed") return state;
  throw new ContractError("stored promotion state is invalid");
}

function sameReservation(left: ReservationReceipt, right: ReservationRequest): boolean {
  return left.idempotencyKey === right.idempotencyKey && left.aggregateId === right.aggregateId &&
    left.expectedRevision === right.expectedRevision && left.payloadSha256 === right.payloadSha256 &&
    left.expiresAt === right.expiresAt;
}

function samePromotion(left: PromotionReceipt, right: PromotionClaim): boolean {
  validatePromotionState(left.state);
  return left.idempotencyKey === right.idempotencyKey && left.aggregateId === right.aggregateId &&
    left.expectedRevision === right.expectedRevision && left.inputSha256 === right.inputSha256;
}

function signingFields(input: TimestampCommitRequest): SigningReservation {
  return {
    idempotencyKey: input.idempotencyKey,
    role: input.role,
    operation: input.operation,
    version: input.version,
    signedSha256: input.signedSha256,
    guardReceiptId: input.guardReceiptId,
    guardExpiresAt: input.guardExpiresAt,
  };
}

function decodeBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  if (bytes.byteLength === 0 || bytes.byteLength > 1024 * 1024) throw new ContractError("timestamp envelope size is invalid");
  return bytes;
}

function safePrefix(value: string): string {
  const prefix = value.replace(/^\/+|\/+$/g, "");
  if (!/^[A-Za-z0-9](?:[A-Za-z0-9._/-]{0,254}[A-Za-z0-9])?$/.test(prefix) || prefix.includes("//") ||
    prefix.split("/").some((part) => part === "." || part === "..")) {
    throw new ContractError("object prefix is invalid");
  }
  return prefix;
}
