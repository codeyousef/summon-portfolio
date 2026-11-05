import { describe, expect, it } from "vitest";
import { inventoryReceiptExpiry } from "../src/receipt-expiry";

const digest = "d8bfc77145636793e968b94a050f21c6152c9090171baf97b6bc4694bdd0c494";
const uploadId = "0123456789abcdef0123456789abcdef";
const key = `sha256/${digest}/uploads/${uploadId}/invoice.pdf`;

describe("receipt expiry inventory", () => {
  it("is bounded, paginated, and never treats legacy or recent objects as expiry candidates", async () => {
    const old = new Date("2026-08-01T00:00:00Z");
    const recent = new Date("2026-08-20T00:00:00Z");
    const bucket = new FakeExpiryBucket([
      object(key, old, { uploadId, sha256: digest, lifecycle: "pending" }),
      object(`sha256/${digest}/legacy.pdf`, old, { sha256: digest }),
      object(`sha256/${digest}/uploads/${"a".repeat(32)}/recent.pdf`, recent, {
        uploadId: "a".repeat(32), sha256: digest, lifecycle: "pending",
      }),
    ], true, "next-page");
    const probes: string[] = [];
    const result = await inventoryReceiptExpiry(bucket as unknown as R2Bucket, async (id) => {
      probes.push(id);
      return { attached: false, exactMatch: false };
    }, { now: new Date("2026-08-22T00:00:00Z"), limit: 3 });

    expect(result).toMatchObject({
      dryRun: true, scanned: 3, eligible: 1, legacyOrInvalid: 1, tooRecent: 1,
      unattached: 1, truncated: true, cursor: "next-page",
    });
    expect(probes).toEqual([uploadId]);
    expect(bucket.lastOptions).toMatchObject({ prefix: "sha256/", limit: 3, include: ["customMetadata"] });
    expect(bucket.deleted).toEqual([]);
  });

  it("separates exact attachments, mismatches, absent projections, and failed probes", async () => {
    const ids = ["1", "2", "3", "4"].map((value) => value.repeat(32));
    const objects = ids.map((id) => object(
      `sha256/${digest}/uploads/${id}/${id}.pdf`,
      new Date("2026-08-01T00:00:00Z"),
      { uploadId: id, sha256: digest, lifecycle: "pending" },
    ));
    const result = await inventoryReceiptExpiry(new FakeExpiryBucket(objects) as unknown as R2Bucket, async (id) => {
      if (id === ids[0]) return { attached: true, exactMatch: true };
      if (id === ids[1]) return { attached: true, exactMatch: false };
      if (id === ids[2]) return { attached: false, exactMatch: false };
      throw new Error("Firestore unavailable");
    }, { now: new Date("2026-08-22T00:00:00Z") });

    expect(result).toMatchObject({
      eligible: 4, attached: 1, attachmentMismatches: 1, unattached: 1, probeFailures: 1,
    });
    expect(result.candidates.map((candidate) => candidate.state)).toEqual([
      "attached", "attachment-mismatch", "unattached", "probe-failed",
    ]);
  });
});

function object(keyValue: string, uploaded: Date, customMetadata: Record<string, string>): R2Object {
  return { key: keyValue, uploaded, customMetadata } as R2Object;
}

class FakeExpiryBucket {
  readonly deleted: string[] = [];
  lastOptions: R2ListOptions | undefined;

  constructor(
    private readonly objects: R2Object[],
    private readonly truncated = false,
    private readonly cursor = "",
  ) {}

  async list(options?: R2ListOptions): Promise<R2Objects> {
    this.lastOptions = options;
    return { objects: this.objects, truncated: this.truncated, cursor: this.cursor, delimitedPrefixes: [] };
  }

  async delete(keyValue: string): Promise<void> { this.deleted.push(keyValue); }
}
