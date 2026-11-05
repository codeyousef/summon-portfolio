import { parseReceiptKey } from "./receipts";

const RECEIPT_EXPIRY_MIN_AGE_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_RECEIPT_EXPIRY_SCAN = 100;

export interface ReceiptAttachmentProbe {
  attached: boolean;
  exactMatch: boolean;
}

export interface ReceiptExpiryCandidate {
  uploadId: string;
  key: string;
  sha256: string;
  uploadedAt: string;
  state: "attached" | "unattached" | "attachment-mismatch" | "probe-failed";
}

export interface ReceiptExpiryInventory {
  dryRun: true;
  scanned: number;
  eligible: number;
  legacyOrInvalid: number;
  tooRecent: number;
  attached: number;
  unattached: number;
  attachmentMismatches: number;
  probeFailures: number;
  truncated: boolean;
  cursor: string | null;
  candidates: ReceiptExpiryCandidate[];
}

/**
 * Produces a bounded, read-only inventory. It deliberately has no delete
 * capability: a successful probe is only a point-in-time observation and is
 * not a sufficient coordination boundary for safe expiry.
 */
export async function inventoryReceiptExpiry(
  bucket: R2Bucket,
  probeAttachment: (uploadId: string, key: string, sha256: string) => Promise<ReceiptAttachmentProbe>,
  options: { now?: Date; limit?: number; cursor?: string } = {},
): Promise<ReceiptExpiryInventory> {
  const now = options.now ?? new Date();
  const limit = Math.min(Math.max(options.limit ?? MAX_RECEIPT_EXPIRY_SCAN, 1), MAX_RECEIPT_EXPIRY_SCAN);
  const page = await bucket.list({
    prefix: "sha256/",
    limit,
    ...(options.cursor ? { cursor: options.cursor } : {}),
    include: ["customMetadata"],
  });
  const inventory: ReceiptExpiryInventory = {
    dryRun: true,
    scanned: page.objects.length,
    eligible: 0,
    legacyOrInvalid: 0,
    tooRecent: 0,
    attached: 0,
    unattached: 0,
    attachmentMismatches: 0,
    probeFailures: 0,
    truncated: page.truncated,
    cursor: page.truncated ? page.cursor : null,
    candidates: [],
  };
  const cutoff = now.getTime() - RECEIPT_EXPIRY_MIN_AGE_MS;
  for (const object of page.objects) {
    const parsed = parseReceiptKey(object.key);
    if (!parsed?.uploadId || object.customMetadata?.uploadId !== parsed.uploadId
      || object.customMetadata?.sha256 !== parsed.digest || object.customMetadata?.lifecycle !== "pending") {
      inventory.legacyOrInvalid += 1;
      continue;
    }
    if (object.uploaded.getTime() > cutoff) {
      inventory.tooRecent += 1;
      continue;
    }
    inventory.eligible += 1;
    let state: ReceiptExpiryCandidate["state"];
    try {
      const attachment = await probeAttachment(parsed.uploadId, parsed.key, parsed.digest);
      if (!attachment.attached) {
        state = "unattached";
        inventory.unattached += 1;
      } else if (!attachment.exactMatch) {
        state = "attachment-mismatch";
        inventory.attachmentMismatches += 1;
      } else {
        state = "attached";
        inventory.attached += 1;
      }
    } catch {
      state = "probe-failed";
      inventory.probeFailures += 1;
    }
    inventory.candidates.push({
      uploadId: parsed.uploadId,
      key: parsed.key,
      sha256: parsed.digest,
      uploadedAt: object.uploaded.toISOString(),
      state,
    });
  }
  return inventory;
}
