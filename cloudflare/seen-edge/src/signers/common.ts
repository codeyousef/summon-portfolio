import { ContractError, hashLocation, isTufOperation, operationPermitsRole, requireId, requireSha256, sha256Hex } from "../contracts";
import type {
  ReleasesSignerEnv,
  SecuritySignerEnv,
  SignerBaseEnv,
  SnapshotSignerEnv,
  TimestampSignerEnv,
  TufOperation,
  TufRole,
} from "../types";

type SignerEnv = ReleasesSignerEnv | SecuritySignerEnv | SnapshotSignerEnv | TimestampSignerEnv;
type SigningKeyBinding =
  | "SEEN_RELEASES_SIGNING_KEY"
  | "SEEN_SECURITY_SIGNING_KEY"
  | "SEEN_SNAPSHOT_SIGNING_KEY"
  | "SEEN_TIMESTAMP_SIGNING_KEY";
type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

interface ValidatedMetadata {
  value: { [key: string]: JsonValue };
  version: number;
  expiresAt: string;
}

interface GuardReceipt {
  schemaVersion: 1;
  receiptId: string;
  role: TufRole;
  operation: TufOperation;
  audience: string;
  environment: string;
  repositoryId: string;
  version: number;
  signedSha256: string;
  expectedTimestampEtag: string | null;
  expiresAt: string;
}

const SIGNED_CONTENT_TYPE = "application/vnd.seen.tuf-signed+json";
const SIGNATURE_CONTENT_TYPE = "application/vnd.seen.ed25519-signature";
const MAX_REQUEST_BYTES = 1024 * 1024;
const CALLER_ORIGIN = "https://seen-edge.internal";
const COMMON_FIELDS = new Set(["_type", "spec_version", "version", "expires", "environment", "repository_id"]);
const SHA256 = /^[0-9a-f]{64}$/;
const TARGET_PATH = /^packages\/[a-z0-9][a-z0-9-]{0,62}\/[a-z0-9][a-z0-9-]{0,62}\/[^/]{1,128}\/[0-9a-f]{64}\/[^/]{1,256}$/;
const INCIDENT_ID = /^inc_[A-Za-z0-9_-]{8,96}$/;

export function createSignerHandler(role: TufRole, keyBinding: SigningKeyBinding): ExportedHandler<SignerEnv> {
  return {
    async fetch(request, env): Promise<Response> {
      try {
        return await signRequest(request, env, role, keyBinding);
      } catch (error) {
        if (error instanceof SignerRejection) return empty(error.status, error.retry);
        console.error("seen signer failed", { role, error: safeError(error) });
        return empty(503, true);
      }
    },
  };
}

async function signRequest(request: Request, env: SignerEnv, role: TufRole, keyBinding: SigningKeyBinding): Promise<Response> {
  validateRoute(request, env, role);
  const body = await boundedBody(request);
  const metadata = validateSignedMetadata(body, role, env.SEEN_ENVIRONMENT, env.SEEN_REPOSITORY_ID);
  const operationValue = singleHeader(request.headers, "X-Seen-Tuf-Operation");
  if (!operationValue || !isTufOperation(operationValue) || !operationPermitsRole(operationValue, role)) reject(403);
  const operation = operationValue;
  const signedSha256 = await sha256Hex(body);
  const idempotencyKey = `sign:${role}:${signedSha256}`;
  const guard = await authorizeCommittedState(env, {
    role,
    operation,
    body,
    signedSha256,
    version: metadata.version,
    idempotencyKey,
  });
  const order = signingOrderStub(env, signedSha256);
  await order.reserveSigning({
    idempotencyKey,
    role,
    operation,
    version: metadata.version,
    signedSha256,
    guardReceiptId: guard.receiptId,
    guardExpiresAt: guard.expiresAt,
  });

  const key = signingKey(env, keyBinding);
  if (!(key instanceof CryptoKey) || key.type !== "private" || key.extractable || !key.usages.includes("sign") || key.algorithm.name !== "Ed25519") {
    throw new Error("signing key binding is not a non-extractable Ed25519 private CryptoKey");
  }
  const signature = new Uint8Array(await crypto.subtle.sign("Ed25519", key, body));
  if (signature.byteLength !== 64) throw new Error("WebCrypto returned a non-Ed25519 signature");

  if (role === "timestamp") {
    const timestampEnv = env as TimestampSignerEnv;
    const keyId = requireSha256(timestampEnv.SEEN_SIGNER_KEY_ID, "signer key id");
    const envelope = canonicalJsonBytes({
      signatures: [{ keyid: keyId, sig: hex(signature) }],
      signed: metadata.value,
    });
    await order.commitTimestamp({
      idempotencyKey,
      role,
      operation,
      version: metadata.version,
      signedSha256,
      guardReceiptId: guard.receiptId,
      guardExpiresAt: guard.expiresAt,
      envelopeBase64: base64(envelope),
      envelopeSha256: await sha256Hex(envelope),
      expectedEtag: guard.expectedTimestampEtag,
    });
  }

  return new Response(signature, {
    status: 200,
    headers: {
      "Content-Type": SIGNATURE_CONTENT_TYPE,
      "Content-Length": "64",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "X-Seen-Signing-Receipt": guard.receiptId,
    },
  });
}

function validateRoute(request: Request, env: SignerBaseEnv, role: TufRole): void {
  const url = new URL(request.url);
  if (url.pathname !== "/sign" || url.search || url.hash) reject(404);
  if (request.method !== "POST") reject(405);
  if (env.SEEN_SIGNER_ROLE !== role) throw new Error("signer role variable does not match its entrypoint");
  if (singleHeader(request.headers, "Content-Type") !== SIGNED_CONTENT_TYPE) reject(415);
  if (singleHeader(request.headers, "Accept") !== SIGNATURE_CONTENT_TYPE) reject(406);
  if (singleHeader(request.headers, "Origin") !== CALLER_ORIGIN) reject(403);
  if (singleHeader(request.headers, "X-Seen-Signer-Audience") !== env.SEEN_SIGNER_AUDIENCE) reject(403);
  if (singleHeader(request.headers, "X-Seen-Tuf-Role") !== role) reject(403);
  const authorization = singleHeader(request.headers, "Authorization");
  if (!authorization?.startsWith("Bearer ") || !constantTimeEqual(authorization.slice(7), env.SEEN_SIGNER_CALL_TOKEN)) reject(401);
}

async function boundedBody(request: Request): Promise<Uint8Array> {
  const declared = request.headers.get("Content-Length");
  if (declared !== null && (!/^[1-9][0-9]*$/.test(declared) || Number(declared) > MAX_REQUEST_BYTES)) reject(413);
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.byteLength === 0 || body.byteLength > MAX_REQUEST_BYTES || declared !== null && Number(declared) !== body.byteLength) reject(413);
  return body;
}

export function validateSignedMetadata(
  body: Uint8Array,
  role: TufRole,
  environment: "development" | "production",
  repositoryId: string,
  now = new Date(),
): ValidatedMetadata {
  const text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(body);
  let value: JsonValue;
  try {
    value = JSON.parse(text) as JsonValue;
  } catch {
    throw new SignerRejection(422);
  }
  if (!isJsonObject(value) || !bytesEqual(canonicalJsonBytes(value), body)) throw new SignerRejection(422);
  const roleField = role === "releases" || role === "security" ? "targets" : "meta";
  exactKeys(value, [...COMMON_FIELDS, roleField]);
  const expectedType = role === "releases" || role === "security" ? "targets" : role;
  requiredString(value, "_type", expectedType);
  requiredString(value, "spec_version", "1.0");
  requiredString(value, "environment", environment);
  requiredString(value, "repository_id", repositoryId);
  const version = positiveInteger(value.version, "version");
  const expiresAt = requiredString(value, "expires");
  const expires = new Date(expiresAt);
  const maxExpiry = role === "releases" ? 7 * 86_400_000
    : role === "snapshot" ? 86_400_000
    : 6 * 3_600_000;
  if (!Number.isFinite(expires.getTime()) || expires.toISOString() !== expiresAt || expires.getTime() <= now.getTime() || expires.getTime() - now.getTime() > maxExpiry) {
    throw new SignerRejection(422);
  }
  if (role === "releases" || role === "security") validateTargets(requiredObject(value, "targets"), role === "security");
  else if (role === "snapshot") validateSnapshot(requiredObject(value, "meta"), version);
  else validateTimestamp(requiredObject(value, "meta"), version);
  return { value, version, expiresAt };
}

async function authorizeCommittedState(
  env: SignerBaseEnv,
  request: { role: TufRole; operation: TufOperation; body: Uint8Array; signedSha256: string; version: number; idempotencyKey: string },
): Promise<GuardReceipt> {
  const response = await env.SEEN_SIGNER_GUARD.fetch(new Request("https://seen-signer-guard.internal/authorize", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.SEEN_SIGNER_CALL_TOKEN}`,
      Origin: CALLER_ORIGIN,
      "Content-Type": SIGNED_CONTENT_TYPE,
      "X-Seen-Tuf-Role": request.role,
      "X-Seen-Tuf-Operation": request.operation,
      "X-Seen-Signer-Audience": env.SEEN_SIGNER_AUDIENCE,
      "X-Seen-Signed-Sha256": request.signedSha256,
      "X-Seen-Idempotency-Key": request.idempotencyKey,
    },
    body: request.body,
  }));
  if (response.status !== 200) throw new SignerRejection(503, true);
  const contentType = response.headers.get("Content-Type")?.split(";", 1)[0];
  if (contentType !== "application/json") throw new Error("signer guard returned an invalid content type");
  const raw = await limitedJson(response, 16 * 1024);
  const receipt = validateGuardReceipt(raw, env, request);
  return receipt;
}

function validateGuardReceipt(
  value: unknown,
  env: SignerBaseEnv,
  request: { role: TufRole; operation: TufOperation; signedSha256: string; version: number },
): GuardReceipt {
  if (!isRecord(value)) throw new Error("signer guard receipt is not an object");
  exactKeys(value, [
    "schemaVersion", "receiptId", "role", "operation", "audience", "environment", "repositoryId",
    "version", "signedSha256", "expectedTimestampEtag", "expiresAt",
  ]);
  if (value.schemaVersion !== 1 || value.role !== request.role || value.operation !== request.operation ||
    value.audience !== env.SEEN_SIGNER_AUDIENCE || value.environment !== env.SEEN_ENVIRONMENT ||
    value.repositoryId !== env.SEEN_REPOSITORY_ID || value.version !== request.version ||
    value.signedSha256 !== request.signedSha256 || typeof value.receiptId !== "string" ||
    typeof value.expiresAt !== "string") {
    throw new Error("signer guard receipt is not bound to the signing request");
  }
  requireId(value.receiptId, "guard receipt id");
  requireSha256(value.signedSha256, "guard signed digest");
  const expiry = Date.parse(value.expiresAt);
  if (!Number.isFinite(expiry) || new Date(expiry).toISOString() !== value.expiresAt || expiry <= Date.now() || expiry > Date.now() + 30_000) {
    throw new Error("signer guard receipt expiry is invalid");
  }
  if (request.role === "timestamp") {
    if (value.expectedTimestampEtag !== null && (typeof value.expectedTimestampEtag !== "string" || !/^[\x21-\x7e]{1,256}$/.test(value.expectedTimestampEtag))) {
      throw new Error("timestamp guard receipt ETag is invalid");
    }
  } else if (value.expectedTimestampEtag !== null) {
    throw new Error("non-timestamp guard receipt contains timestamp authority");
  }
  return {
    schemaVersion: 1,
    receiptId: value.receiptId,
    role: request.role,
    operation: request.operation,
    audience: env.SEEN_SIGNER_AUDIENCE,
    environment: env.SEEN_ENVIRONMENT,
    repositoryId: env.SEEN_REPOSITORY_ID,
    version: request.version,
    signedSha256: request.signedSha256,
    expectedTimestampEtag: value.expectedTimestampEtag as string | null,
    expiresAt: value.expiresAt,
  };
}

function signingOrderStub(env: SignerBaseEnv, digest: string): DurableObjectStub<import("../coordinators").SeenSigningOrderDO> {
  const namespace = env.SEEN_SIGNING_ORDER.jurisdiction("us");
  const name = `signing:${env.SEEN_ENVIRONMENT}:${env.SEEN_REPOSITORY_ID}`;
  return namespace.getByName(name, { locationHint: hashLocation(digest) });
}

function validateTargets(targets: { [key: string]: JsonValue }, security: boolean): void {
  for (const [path, raw] of Object.entries(targets)) {
    if (!TARGET_PATH.test(path) || !isJsonObject(raw)) throw new SignerRejection(422);
    exactKeys(raw, ["length", "hashes", "custom"]);
    positiveInteger(raw.length, "target length");
    validateHashes(requiredObject(raw, "hashes"));
    const custom = requiredObject(raw, "custom");
    const availability = requiredString(custom, "availability");
    if (security) {
      if (availability !== "security-quarantined" || requiredString(custom, "security_action") !== "quarantine" ||
        !INCIDENT_ID.test(requiredString(custom, "incident_id"))) throw new SignerRejection(422);
    } else if (!new Set(["available", "yanked"]).has(availability) || "incident_id" in custom || "security_action" in custom) {
      throw new SignerRejection(422);
    }
  }
}

function validateSnapshot(meta: { [key: string]: JsonValue }, maximumVersion: number): void {
  exactKeys(meta, ["targets.json", "releases.json", "security.json"]);
  for (const reference of Object.values(meta)) validateReference(asObject(reference), maximumVersion);
}

function validateTimestamp(meta: { [key: string]: JsonValue }, version: number): void {
  exactKeys(meta, ["snapshot.json"]);
  const reference = asObject(meta["snapshot.json"]);
  validateReference(reference, version);
  if (positiveInteger(reference.version, "snapshot version") !== version) throw new SignerRejection(422);
}

function validateReference(reference: { [key: string]: JsonValue }, maximumVersion: number): void {
  exactKeys(reference, ["version", "length", "hashes"]);
  const version = positiveInteger(reference.version, "reference version");
  if (version > maximumVersion) throw new SignerRejection(422);
  positiveInteger(reference.length, "reference length");
  validateHashes(requiredObject(reference, "hashes"));
}

function validateHashes(hashes: { [key: string]: JsonValue }): void {
  exactKeys(hashes, ["sha256"]);
  const digest = requiredString(hashes, "sha256");
  if (!SHA256.test(digest)) throw new SignerRejection(422);
}

export function canonicalJson(value: JsonValue): string {
  if (value === null) return "null";
  if (typeof value === "string") return JSON.stringify(value);
  if (typeof value === "boolean") return value ? "true" : "false";
  if (typeof value === "number") {
    if (!Number.isSafeInteger(value)) throw new ContractError("canonical TUF JSON forbids non-integer or unsafe numbers");
    return String(value);
  }
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(",")}]`;
  const keys = Object.keys(value).sort(compareUtf8);
  return `{${keys.map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key] ?? null)}`).join(",")}}`;
}

export function canonicalJsonBytes(value: JsonValue): Uint8Array {
  return new TextEncoder().encode(canonicalJson(value));
}

function compareUtf8(left: string, right: string): number {
  const a = new TextEncoder().encode(left);
  const b = new TextEncoder().encode(right);
  for (let index = 0; index < Math.min(a.length, b.length); index += 1) {
    const compared = (a[index] ?? 0) - (b[index] ?? 0);
    if (compared !== 0) return compared;
  }
  return a.length - b.length;
}

function isJsonObject(value: JsonValue): value is { [key: string]: JsonValue } {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function asObject(value: JsonValue | undefined): { [key: string]: JsonValue } {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new SignerRejection(422);
  return value;
}

function requiredObject(value: { [key: string]: JsonValue }, key: string): { [key: string]: JsonValue } {
  return asObject(value[key]);
}

function requiredString(value: { [key: string]: JsonValue }, key: string, expected?: string): string {
  const actual = value[key];
  if (typeof actual !== "string" || expected !== undefined && actual !== expected) throw new SignerRejection(422);
  return actual;
}

function positiveInteger(value: JsonValue | undefined, _label: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 1) throw new SignerRejection(422);
  return value;
}

function exactKeys(value: Record<string, unknown>, keys: Iterable<string>): void {
  const expected = [...keys].sort(compareUtf8);
  const actual = Object.keys(value).sort(compareUtf8);
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new SignerRejection(422);
}

function singleHeader(headers: Headers, name: string): string | null {
  const value = headers.get(name);
  if (value === null || value.includes(",")) return null;
  return value;
}

function constantTimeEqual(left: string, right: string): boolean {
  const encoder = new TextEncoder();
  return constantTimeBytes(encoder.encode(left), encoder.encode(right));
}

async function limitedJson(response: Response, maximumBytes: number): Promise<unknown> {
  const body = new Uint8Array(await response.arrayBuffer());
  if (body.byteLength === 0 || body.byteLength > maximumBytes) throw new Error("signer guard receipt size is invalid");
  return JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(body)) as unknown;
}

function signingKey(env: SignerEnv, binding: SigningKeyBinding): CryptoKey {
  switch (binding) {
    case "SEEN_RELEASES_SIGNING_KEY":
      if (!("SEEN_RELEASES_SIGNING_KEY" in env)) throw new Error("releases key binding is unavailable");
      return env.SEEN_RELEASES_SIGNING_KEY;
    case "SEEN_SECURITY_SIGNING_KEY":
      if (!("SEEN_SECURITY_SIGNING_KEY" in env)) throw new Error("security key binding is unavailable");
      return env.SEEN_SECURITY_SIGNING_KEY;
    case "SEEN_SNAPSHOT_SIGNING_KEY":
      if (!("SEEN_SNAPSHOT_SIGNING_KEY" in env)) throw new Error("snapshot key binding is unavailable");
      return env.SEEN_SNAPSHOT_SIGNING_KEY;
    case "SEEN_TIMESTAMP_SIGNING_KEY":
      if (!("SEEN_TIMESTAMP_SIGNING_KEY" in env)) throw new Error("timestamp key binding is unavailable");
      return env.SEEN_TIMESTAMP_SIGNING_KEY;
  }
}

function base64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function hex(bytes: Uint8Array): string {
  return [...bytes].map((value) => value.toString(16).padStart(2, "0")).join("");
}

function bytesEqual(left: Uint8Array, right: Uint8Array): boolean {
  return constantTimeBytes(left, right);
}

function constantTimeBytes(left: Uint8Array, right: Uint8Array): boolean {
  if (left.byteLength !== right.byteLength) return false;
  let difference = 0;
  for (let index = 0; index < left.byteLength; index += 1) {
    difference |= (left[index] ?? 0) ^ (right[index] ?? 0);
  }
  return difference === 0;
}

function empty(status: number, retry = false): Response {
  const headers = new Headers({ "Content-Length": "0", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" });
  if (status === 405) headers.set("Allow", "POST");
  if (status === 401) headers.set("WWW-Authenticate", "Bearer realm=\"seen-tuf-signer\"");
  if (retry) headers.set("Retry-After", "1");
  return new Response(null, { status, headers });
}

function reject(status: number, retry = false): never {
  throw new SignerRejection(status, retry);
}

class SignerRejection extends Error {
  constructor(readonly status: number, readonly retry = false) {
    super(`signing request rejected with ${status}`);
  }
}

function safeError(error: unknown): string {
  if (!(error instanceof Error)) return "unknown";
  return error.message.replace(/[\u0000-\u001f\u007f]/g, "").slice(0, 256) || "unknown";
}
