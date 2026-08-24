import { describe, expect, it } from "vitest";
import { handleFinOpsReceipt, parseReceiptKey, readBoundedReceiptBody } from "../src/receipts";

const token = "edge-origin-token-with-at-least-32-characters";
const bytes = new TextEncoder().encode("invoice receipt");
const digest = "d8bfc77145636793e968b94a050f21c6152c9090171baf97b6bc4694bdd0c494";
const uploadId = "0123456789abcdef0123456789abcdef";
const key = `sha256/${digest}/uploads/${uploadId}/cloudflare-2026-08.pdf`;

describe("private FinOps receipt storage", () => {
  it("accepts only safe content-addressed keys and internal authentication", async () => {
    expect(parseReceiptKey(key)).toEqual({ key, digest, uploadId, filename: "cloudflare-2026-08.pdf" });
    const legacyKey = `sha256/${digest}/cloudflare-2026-08.pdf`;
    expect(parseReceiptKey(legacyKey)).toEqual({ key: legacyKey, digest, uploadId: null, filename: "cloudflare-2026-08.pdf" });
    expect(parseReceiptKey(`../${key}`)).toBeNull();
    const response = await execute(new FakeReceiptBucket(), request("PUT", bytes, { authorization: undefined }));
    expect(response.status).toBe(401);
  });

  it("conditionally creates, replays, reads, and rejects deletion of a private receipt", async () => {
    const bucket = new FakeReceiptBucket();
    expect((await execute(bucket, request("PUT", bytes))).status).toBe(201);
    expect(bucket.lastOnlyIf).toEqual({ etagDoesNotMatch: "*" });
    expect(bucket.objects.get(key)?.customMetadata).toMatchObject({ uploadId, lifecycle: "pending" });
    expect((await execute(bucket, request("PUT", bytes))).status).toBe(204);

    const head = await execute(bucket, request("HEAD"));
    expect(head.status).toBe(200);
    expect(head.headers.get("content-length")).toBe(String(bytes.byteLength));

    const read = await execute(bucket, request("GET"));
    expect(read.status).toBe(200);
    expect(read.headers.get("cache-control")).toBe("private, no-store");
    expect(read.headers.get("content-disposition")).toContain("cloudflare-2026-08.pdf");
    expect(new Uint8Array(await read.arrayBuffer())).toEqual(bytes);

    const deletion = await execute(bucket, request("DELETE"));
    expect(deletion.status).toBe(405);
    expect(deletion.headers.get("allow")).toBe("PUT, GET, HEAD");
    expect(bucket.objects.size).toBe(1);
  });

  it("rejects unsupported content types and digest mismatches", async () => {
    const bucket = new FakeReceiptBucket();
    expect((await execute(bucket, request("PUT", bytes, { "content-type": "text/html" }))).status).toBe(415);
    expect((await execute(bucket, request("PUT", new TextEncoder().encode("different")))).status).toBe(422);
  });

  it("bounds streamed bodies before buffering even when content length is absent", async () => {
    const withinLimit = stream([new Uint8Array([1, 2]), new Uint8Array([3, 4])]);
    expect(await readBoundedReceiptBody(withinLimit, 4)).toEqual(new Uint8Array([1, 2, 3, 4]));

    const overLimit = stream([new Uint8Array([1, 2, 3]), new Uint8Array([4, 5, 6])]);
    expect(await readBoundedReceiptBody(overLimit, 5)).toBeNull();
    await expect(readBoundedReceiptBody(stream([]), 0)).rejects.toThrow("invalid buffered body limit");
  });
});

function stream(chunks: Uint8Array[]): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(chunk);
      controller.close();
    },
  });
}

function request(method: string, body?: Uint8Array, overrides: Record<string, string | undefined> = {}): Request {
  const headers = new Headers({
    authorization: `Bearer ${token}`,
    "content-type": "application/pdf",
    "if-none-match": "*",
    "x-finops-receipt-key": key,
    "x-finops-receipt-sha256": digest,
    "x-finops-receipt-content-type": "application/pdf",
  });
  for (const [name, value] of Object.entries(overrides)) {
    if (value === undefined) headers.delete(name); else headers.set(name, value);
  }
  const init: RequestInit = { method, headers };
  if (method === "PUT" && body) init.body = body;
  return new Request("https://yousef.codes/internal/finops/v1/receipt", init);
}

function execute(bucket: FakeReceiptBucket, input: Request): Promise<Response> {
  return handleFinOpsReceipt(input, bucket as unknown as R2Bucket, token);
}

interface StoredReceipt {
  key: string;
  bytes: Uint8Array;
  size: number;
  httpMetadata: R2HTTPMetadata;
  customMetadata: Record<string, string>;
  sha256: ArrayBuffer;
}

class FakeReceiptBucket {
  readonly objects = new Map<string, StoredReceipt>();
  lastOnlyIf: R2Conditional | Headers | undefined;

  async put(keyValue: string, value: ArrayBuffer, options?: R2PutOptions): Promise<R2Object | null> {
    this.lastOnlyIf = options?.onlyIf;
    if (this.objects.has(keyValue) && !(options?.onlyIf instanceof Headers) && options?.onlyIf?.etagDoesNotMatch === "*") return null;
    const stored: StoredReceipt = {
      key: keyValue,
      bytes: new Uint8Array(value),
      size: value.byteLength,
      httpMetadata: options?.httpMetadata as R2HTTPMetadata,
      customMetadata: options?.customMetadata ?? {},
      sha256: options?.sha256 as ArrayBuffer,
    };
    this.objects.set(keyValue, stored);
    return this.object(stored);
  }

  async head(keyValue: string): Promise<R2Object | null> {
    const stored = this.objects.get(keyValue);
    return stored ? this.object(stored) : null;
  }

  async get(keyValue: string): Promise<R2ObjectBody | null> {
    const stored = this.objects.get(keyValue);
    if (!stored) return null;
    return {
      ...this.object(stored),
      body: new ReadableStream({ start(controller) { controller.enqueue(stored.bytes); controller.close(); } }),
      bodyUsed: false,
      arrayBuffer: async () => stored.bytes.slice().buffer,
      bytes: async () => stored.bytes.slice(),
      text: async () => new TextDecoder().decode(stored.bytes),
      json: async <T>() => JSON.parse(new TextDecoder().decode(stored.bytes)) as T,
      blob: async () => new Blob([stored.bytes]),
    } as R2ObjectBody;
  }

  private object(stored: StoredReceipt): R2Object {
    return {
      key: stored.key,
      version: "1",
      size: stored.size,
      etag: "etag",
      httpEtag: '"etag"',
      checksums: { sha256: stored.sha256, toJSON: () => ({ sha256: digest }) },
      uploaded: new Date(0),
      httpMetadata: stored.httpMetadata,
      customMetadata: stored.customMetadata,
      storageClass: "Standard",
      writeHttpMetadata() {},
    } as R2Object;
  }
}
