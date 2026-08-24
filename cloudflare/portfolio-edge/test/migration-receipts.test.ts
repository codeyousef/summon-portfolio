import { describe, expect, it } from "vitest";
import { storePhotographyStagingReceipt } from "../src/migration-receipts";
import type { PhotographyStagingReceipt } from "../src/types";

describe("migration receipts", () => {
  it("conditionally creates and exactly replays an immutable photography receipt", async () => {
    const bucket = new FakeReceiptBucket();

    const created = await storePhotographyStagingReceipt(bucket.asR2(), RECEIPT);
    const replay = await storePhotographyStagingReceipt(bucket.asR2(), RECEIPT);

    expect(created.created).toBe(true);
    expect(replay).toEqual({ ...created, created: false });
    expect(bucket.lastOnlyIf).toEqual({ etagDoesNotMatch: "*" });
    expect(bucket.objects.size).toBe(1);
  });

  it("fails closed when an immutable receipt key contains different bytes", async () => {
    const bucket = new FakeReceiptBucket();
    await storePhotographyStagingReceipt(bucket.asR2(), RECEIPT);
    bucket.objects.set(`migration-receipts/photography/${RECEIPT.id}.json`, new TextEncoder().encode("conflict"));

    await expect(storePhotographyStagingReceipt(bucket.asR2(), RECEIPT)).rejects.toThrow("conflicts");
  });
});

class FakeReceiptBucket {
  readonly objects = new Map<string, Uint8Array>();
  lastOnlyIf: R2Conditional | Headers | undefined;

  asR2(): R2Bucket {
    return {
      put: async (key: string, value: ReadableStream | ArrayBuffer | ArrayBufferView | string | Blob | null, options?: R2PutOptions) => {
        this.lastOnlyIf = options?.onlyIf;
        const bytes = value instanceof Uint8Array ? value : new Uint8Array(value as ArrayBuffer);
        if (this.objects.has(key) && !(options?.onlyIf instanceof Headers) && options?.onlyIf?.etagDoesNotMatch === "*") {
          return null;
        }
        this.objects.set(key, bytes.slice());
        return { key } as R2Object;
      },
      get: async (key: string) => {
        const bytes = this.objects.get(key);
        if (!bytes) return null;
        return { body: new Response(bytes).body! } as R2ObjectBody;
      },
    } as unknown as R2Bucket;
  }
}

const RECEIPT: PhotographyStagingReceipt = {
  id: "photography:stage:dev-two-object-v1",
  committedEpoch: 1,
  newRevision: 2,
  stagedAt: "2026-08-23T03:30:00Z",
  mirrorState: "staged",
  assets: [
    {
      photoId: "photo-1",
      sourceStorageKey: "photography/portfolio-dev/photo-1.jpg",
      contentAddressedStorageKey: `sha256/${"a".repeat(64)}/photo-1.jpg`,
      sha256: "a".repeat(64),
      sizeBytes: 10,
      contentType: "image/jpeg",
    },
  ],
};
