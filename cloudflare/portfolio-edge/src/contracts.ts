import type {
  DeadLetterReceipt,
  MutationEnvelope,
  MutationReceipt,
  PhotographyStagingAssetReceipt,
  PhotographyStagingReceipt,
} from "./types";
import { readBoundedBody } from "./bounded-body";

const ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const MAX_PAYLOAD_BYTES = 128 * 1024;

export class ContractError extends Error {}

export function canonicalJson(value: unknown): string {
  if (value === null || typeof value === "boolean" || typeof value === "string") {
    return JSON.stringify(value);
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) throw new ContractError("payload contains a non-finite number");
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(",")}]`;
  if (typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>)
      .filter(([, entry]) => entry !== undefined)
      .sort(([left], [right]) => left.localeCompare(right));
    return `{${entries.map(([key, entry]) => `${JSON.stringify(key)}:${canonicalJson(entry)}`).join(",")}}`;
  }
  throw new ContractError(`payload contains unsupported ${typeof value} value`);
}

export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export async function validateMutationEnvelope(value: unknown): Promise<MutationEnvelope> {
  if (!isRecord(value)) throw new ContractError("mutation envelope must be an object");
  const envelope: MutationEnvelope = {
    id: requiredId(value.id, "id"),
    aggregateType: requiredId(value.aggregateType, "aggregateType"),
    aggregateId: requiredId(value.aggregateId, "aggregateId"),
    expectedRevision: requiredNonNegativeInteger(value.expectedRevision, "expectedRevision"),
    authorityEpoch: requiredNonNegativeInteger(value.authorityEpoch, "authorityEpoch"),
    occurredAt: requiredTimestamp(value.occurredAt),
    payloadSha256: requiredSha(value.payloadSha256),
    payload: value.payload,
  };
  const canonicalPayload = canonicalJson(envelope.payload);
  if (new TextEncoder().encode(canonicalPayload).byteLength > MAX_PAYLOAD_BYTES) {
    throw new ContractError(`payload exceeds ${MAX_PAYLOAD_BYTES} bytes`);
  }
  const actualSha = await sha256Hex(canonicalPayload);
  if (actualSha !== envelope.payloadSha256) throw new ContractError("payloadSha256 does not match payload");
  return envelope;
}

export async function makeMutationEnvelope(
  value: Omit<MutationEnvelope, "payloadSha256">,
): Promise<MutationEnvelope> {
  return validateMutationEnvelope({
    ...value,
    payloadSha256: await sha256Hex(canonicalJson(value.payload)),
  });
}

export function validateMutationReceipt(value: unknown, envelope: MutationEnvelope): MutationReceipt {
  if (!isRecord(value)) throw new ContractError("mutation receipt must be an object");
  const receipt: MutationReceipt = {
    id: requiredId(value.id, "receipt.id"),
    committedEpoch: requiredNonNegativeInteger(value.committedEpoch, "committedEpoch"),
    newRevision: requiredNonNegativeInteger(value.newRevision, "newRevision"),
    firestoreCommitTime: requiredTimestampField(value.firestoreCommitTime, "firestoreCommitTime"),
    mirrorState: requiredMirrorState(value.mirrorState),
  };
  if (receipt.id !== envelope.id) throw new ContractError("receipt id does not match envelope id");
  if (receipt.committedEpoch < envelope.authorityEpoch) throw new ContractError("receipt epoch is stale");
  if (receipt.newRevision < envelope.expectedRevision) throw new ContractError("receipt revision is stale");
  return receipt;
}

export function validateDeadLetterReceipt(value: unknown): DeadLetterReceipt {
  if (!isRecord(value)) throw new ContractError("dead-letter receipt must be an object");
  return {
    envelopeId: nullableId(value.envelopeId, "envelopeId"),
    aggregateType: nullableId(value.aggregateType, "aggregateType"),
    payloadSha256: nullableSha(value.payloadSha256),
    sourceMessageId: requiredId(value.sourceMessageId, "sourceMessageId"),
    attempts: requiredNonNegativeInteger(value.attempts, "attempts"),
    reason: requiredBoundedString(value.reason, "reason", 512),
    failedAt: requiredTimestampField(value.failedAt, "failedAt"),
  };
}

export async function readMutationReceipt(
  response: Response,
  envelope: MutationEnvelope,
): Promise<MutationReceipt | null> {
  if (!response.ok) return null;
  const declaredLength = Number(response.headers.get("content-length") ?? "0");
  if (declaredLength > 16 * 1024) return null;
  const bytes = await readBoundedBody(response.body, 16 * 1024);
  if (bytes === null) return null;
  try {
    const text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes);
    return validateMutationReceipt(JSON.parse(text) as unknown, envelope);
  } catch {
    return null;
  }
}

export function validatePhotographyStagingReceipt(
  value: unknown,
  envelope: MutationEnvelope,
): PhotographyStagingReceipt {
  if (!isRecord(value)) throw new ContractError("photography staging receipt must be an object");
  if (value.mirrorState !== "staged") throw new ContractError("mirrorState is invalid");
  const receipt: PhotographyStagingReceipt = {
    id: requiredId(value.id, "receipt.id"),
    committedEpoch: requiredNonNegativeInteger(value.committedEpoch, "committedEpoch"),
    newRevision: requiredNonNegativeInteger(value.newRevision, "newRevision"),
    stagedAt: requiredTimestampField(value.stagedAt, "stagedAt"),
    mirrorState: "staged",
    assets: validatePhotographyStagingAssets(value.assets, envelope),
  };
  if (receipt.id !== envelope.id) throw new ContractError("receipt id does not match envelope id");
  if (receipt.committedEpoch < envelope.authorityEpoch) throw new ContractError("receipt epoch is stale");
  if (receipt.newRevision < envelope.expectedRevision) throw new ContractError("receipt revision is stale");
  return receipt;
}

function validatePhotographyStagingAssets(
  value: unknown,
  envelope: MutationEnvelope,
): PhotographyStagingAssetReceipt[] {
  if (!isRecord(envelope.payload) || !Array.isArray(envelope.payload.assets) || !Array.isArray(value)) {
    throw new ContractError("photography staging assets are invalid");
  }
  const expectedAssets = envelope.payload.assets;
  if (value.length !== expectedAssets.length || value.length < 1 || value.length > 25) {
    throw new ContractError("photography staging asset count does not match the envelope");
  }
  return value.map((rawReceipt, index) => {
    const rawExpected = expectedAssets[index];
    if (!isRecord(rawReceipt) || !isRecord(rawExpected)) {
      throw new ContractError("photography staging asset is invalid");
    }
    const photoId = requiredBoundedString(rawReceipt.photoId, "photoId", 128);
    const sourceStorageKey = requiredBoundedString(rawReceipt.sourceStorageKey, "sourceStorageKey", 512);
    const sha256 = requiredSha(rawReceipt.sha256);
    const sizeBytes = requiredNonNegativeInteger(rawReceipt.sizeBytes, "sizeBytes");
    const contentType = requiredBoundedString(rawReceipt.contentType, "contentType", 64);
    const contentAddressedStorageKey = requiredBoundedString(
      rawReceipt.contentAddressedStorageKey,
      "contentAddressedStorageKey",
      512,
    );
    const extension = sourceStorageKey.slice(sourceStorageKey.lastIndexOf(".") + 1).toLowerCase();
    const expectedTargetKey = `sha256/${sha256}/${photoId}.${extension}`;
    if (
      photoId !== rawExpected.photoId ||
      sourceStorageKey !== rawExpected.sourceStorageKey ||
      sha256 !== rawExpected.expectedSha256 ||
      sizeBytes !== rawExpected.expectedSizeBytes ||
      contentType !== rawExpected.contentType ||
      contentAddressedStorageKey !== expectedTargetKey
    ) {
      throw new ContractError("photography staging receipt does not match the approved envelope");
    }
    return { photoId, sourceStorageKey, contentAddressedStorageKey, sha256, sizeBytes, contentType };
  });
}

export async function readPhotographyStagingReceipt(
  response: Response,
  envelope: MutationEnvelope,
): Promise<PhotographyStagingReceipt | null> {
  if (!response.ok) return null;
  const declaredLength = Number(response.headers.get("content-length") ?? "0");
  if (declaredLength > 16 * 1024) return null;
  const bytes = await readBoundedBody(response.body, 16 * 1024);
  if (bytes === null) return null;
  try {
    const text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes);
    return validatePhotographyStagingReceipt(JSON.parse(text) as unknown, envelope);
  } catch {
    return null;
  }
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requiredId(value: unknown, field: string): string {
  if (typeof value !== "string" || !ID_PATTERN.test(value)) {
    throw new ContractError(`${field} is invalid`);
  }
  return value;
}

function nullableId(value: unknown, field: string): string | null {
  return value === null ? null : requiredId(value, field);
}

function nullableSha(value: unknown): string | null {
  return value === null ? null : requiredSha(value);
}

function requiredBoundedString(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
    throw new ContractError(`${field} is invalid`);
  }
  return value;
}

function requiredNonNegativeInteger(value: unknown, field: string): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0) {
    throw new ContractError(`${field} must be a non-negative safe integer`);
  }
  return value as number;
}

function requiredTimestamp(value: unknown): string {
  if (typeof value !== "string" || !Number.isFinite(Date.parse(value))) {
    throw new ContractError("occurredAt must be an ISO-8601 timestamp");
  }
  return value;
}

function requiredTimestampField(value: unknown, field: string): string {
  if (typeof value !== "string" || !Number.isFinite(Date.parse(value))) {
    throw new ContractError(`${field} must be an ISO-8601 timestamp`);
  }
  return value;
}

function requiredMirrorState(value: unknown): MutationReceipt["mirrorState"] {
  if (value !== "pending" && value !== "mirrored" && value !== "failed") {
    throw new ContractError("mirrorState is invalid");
  }
  return value;
}

function requiredSha(value: unknown): string {
  if (typeof value !== "string" || !SHA256_PATTERN.test(value)) {
    throw new ContractError("payloadSha256 must be a lowercase SHA-256 digest");
  }
  return value;
}
