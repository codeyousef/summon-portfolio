import { readBoundedBody } from "./bounded-body";
import type { PhotographyStagingReceipt } from "./types";

const MAX_MIGRATION_RECEIPT_BYTES = 16 * 1024;

export interface StoredMigrationReceipt {
  key: string;
  sha256: string;
  created: boolean;
}

export async function storePhotographyStagingReceipt(
  bucket: R2Bucket,
  receipt: PhotographyStagingReceipt,
): Promise<StoredMigrationReceipt> {
  const key = `migration-receipts/photography/${receipt.id}.json`;
  const bytes = new TextEncoder().encode(JSON.stringify(receipt));
  if (bytes.byteLength > MAX_MIGRATION_RECEIPT_BYTES) throw new Error("photography staging receipt is too large");
  const digestBuffer = await crypto.subtle.digest("SHA-256", bytes);
  const digest = hex(digestBuffer);
  const created = await bucket.put(key, bytes, {
    onlyIf: { etagDoesNotMatch: "*" },
    httpMetadata: { contentType: "application/json", cacheControl: "private, no-store" },
    customMetadata: { sha256: digest, receiptType: "portfolio.photography.backfill.v1" },
    sha256: digestBuffer,
  });
  if (created) return { key, sha256: digest, created: true };

  const existing = await bucket.get(key);
  if (!existing) throw new Error("immutable photography staging receipt disappeared");
  const existingBytes = await readBoundedBody(existing.body, MAX_MIGRATION_RECEIPT_BYTES);
  if (existingBytes === null || !equalBytes(existingBytes, bytes)) {
    throw new Error("immutable photography staging receipt conflicts with an existing object");
  }
  return { key, sha256: digest, created: false };
}

function equalBytes(left: Uint8Array, right: Uint8Array): boolean {
  if (left.byteLength !== right.byteLength) return false;
  let difference = 0;
  for (let index = 0; index < left.byteLength; index += 1) difference |= left[index]! ^ right[index]!;
  return difference === 0;
}

function hex(value: ArrayBuffer): string {
  return [...new Uint8Array(value)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
