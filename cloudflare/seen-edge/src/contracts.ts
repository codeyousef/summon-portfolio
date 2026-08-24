import type {
  MaintenanceCommand,
  SeenDeadLetter,
  SeenOperation,
  TufOperation,
  TufRole,
} from "./types";

const ID = /^[A-Za-z0-9][A-Za-z0-9:_-]{7,127}$/;
const AGGREGATE_ID = /^[A-Za-z0-9][A-Za-z0-9:._/-]{2,255}$/;
const SHA256 = /^[0-9a-f]{64}$/;
const METADATA_FILENAME = /^(?:root\.json|timestamp\.json|[1-9][0-9]*\.(?:root|targets|releases|security|snapshot)\.json)$/;
const MAINTENANCE_COMMANDS = new Set<MaintenanceCommand>([
  "refresh-releases-once",
  "refresh-security-once",
  "recover-expired-releases-once",
  "recover-expired-security-once",
  "verify-root-chain",
]);

export class ContractError extends Error {}

export function parseSeenOperation(value: unknown): SeenOperation {
  const record = object(value, "operation");
  const kind = string(record.kind, "kind");
  const id = identifier(record.id, "id");
  const requestedAt = canonicalInstant(record.requestedAt, "requestedAt");
  if (kind === "promotion") {
    const aggregateId = string(record.aggregateId, "aggregateId");
    if (!AGGREGATE_ID.test(aggregateId)) throw new ContractError("aggregateId is invalid");
    const expectedRevision = nonNegativeInteger(record.expectedRevision, "expectedRevision");
    exactKeys(record, ["kind", "id", "aggregateId", "expectedRevision", "requestedAt"]);
    return { kind, id, aggregateId, expectedRevision, requestedAt };
  }
  if (kind === "maintenance") {
    const command = string(record.command, "command") as MaintenanceCommand;
    if (!MAINTENANCE_COMMANDS.has(command)) throw new ContractError("maintenance command is invalid");
    exactKeys(record, ["kind", "id", "command", "requestedAt"]);
    return { kind, id, command, requestedAt };
  }
  throw new ContractError("operation kind is invalid");
}

export function deadLetter(messageId: string, body: unknown, reason: string, now = new Date()): SeenDeadLetter {
  if (!messageId || messageId.length > 256) throw new ContractError("messageId is invalid");
  const sanitizedReason = reason.replace(/[\u0000-\u001f\u007f]/g, "").slice(0, 256) || "invalid operation";
  return { messageId, failedAt: now.toISOString(), reason: sanitizedReason, body };
}

export function requireMetadataFilename(value: string): string {
  if (!METADATA_FILENAME.test(value)) throw new ContractError("metadata filename is invalid");
  return value;
}

export function requireSha256(value: string, label = "digest"): string {
  if (!SHA256.test(value)) throw new ContractError(`${label} is invalid`);
  return value;
}

export function requireId(value: string, label = "id"): string {
  if (!ID.test(value)) throw new ContractError(`${label} is invalid`);
  return value;
}

export function operationPermitsRole(operation: TufOperation, role: TufRole): boolean {
  switch (operation) {
    case "release":
      return role === "releases" || role === "snapshot" || role === "timestamp";
    case "security":
      return role === "security" || role === "snapshot" || role === "timestamp";
    case "bootstrap":
      return true;
    case "targets-renewal":
      return role === "snapshot" || role === "timestamp";
    case "targets-rotation:releases":
      return role === "releases" || role === "snapshot" || role === "timestamp";
    case "targets-rotation:security":
      return role === "security" || role === "snapshot" || role === "timestamp";
  }
}

export function isTufOperation(value: string): value is TufOperation {
  return [
    "release",
    "security",
    "bootstrap",
    "targets-renewal",
    "targets-rotation:releases",
    "targets-rotation:security",
  ].includes(value);
}

export function hashLocation(value: string): "enam" | "wnam" {
  let hash = 0x811c9dc5;
  for (const byte of new TextEncoder().encode(value)) {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return (hash & 1) === 0 ? "enam" : "wnam";
}

export async function sha256Hex(bytes: ArrayBuffer | ArrayBufferView): Promise<string> {
  const view = ArrayBuffer.isView(bytes)
    ? new Uint8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength)
    : new Uint8Array(bytes);
  const source = view.slice().buffer;
  const digest = await crypto.subtle.digest("SHA-256", source);
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, "0")).join("");
}

function object(value: unknown, label: string): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) throw new ContractError(`${label} must be an object`);
  return value as Record<string, unknown>;
}

function string(value: unknown, label: string): string {
  if (typeof value !== "string" || value.length === 0) throw new ContractError(`${label} must be a non-empty string`);
  return value;
}

function identifier(value: unknown, label: string): string {
  const parsed = string(value, label);
  if (!ID.test(parsed)) throw new ContractError(`${label} is invalid`);
  return parsed;
}

function canonicalInstant(value: unknown, label: string): string {
  const parsed = string(value, label);
  const date = new Date(parsed);
  if (!Number.isFinite(date.getTime()) || date.toISOString() !== parsed) throw new ContractError(`${label} is not canonical UTC`);
  return parsed;
}

function nonNegativeInteger(value: unknown, label: string): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0) throw new ContractError(`${label} is invalid`);
  return value as number;
}

function exactKeys(value: Record<string, unknown>, expected: string[]): void {
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  if (JSON.stringify(actual) !== JSON.stringify(wanted)) throw new ContractError("operation fields are invalid");
}
