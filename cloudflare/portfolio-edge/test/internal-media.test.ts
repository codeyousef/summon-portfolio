import { describe, expect, it } from "vitest";
import { handleInternalMedia } from "../src/r2";

const token = "edge-origin-token-with-at-least-32-characters";
const bytes = new TextEncoder().encode("immutable portfolio media");
const digest = "953ed960580e648dd89671316e65f3a64bb8c5b6fe0365b36b1ecf965fc67368";
const key = `sha256/${digest}/photo-1.webp`;

describe("internal R2 media API", () => {
  it("requires the internal bearer token", async () => {
    const bucket = new FakeR2Bucket();
    const response = await handleInternalMedia(
      mediaRequest("PUT", bytes, { authorization: undefined }),
      bucket as unknown as R2Bucket,
      15_728_640,
      token,
    );
    expect(response.status).toBe(401);
  });

  it("conditionally creates, verifies, reads, and deletes immutable objects", async () => {
    const bucket = new FakeR2Bucket();
    const created = await execute(bucket, mediaRequest("PUT", bytes));
    expect(created.status).toBe(201);
    expect(bucket.lastPutOnlyIf).toEqual({ etagDoesNotMatch: "*" });

    const replayed = await execute(bucket, mediaRequest("PUT", bytes));
    expect(replayed.status).toBe(204);

    const read = await execute(bucket, mediaRequest("GET"));
    expect(read.status).toBe(200);
    expect(read.headers.get("x-portfolio-media-sha256")).toBe(digest);
    expect(new Uint8Array(await read.arrayBuffer())).toEqual(bytes);

    const removed = await execute(bucket, mediaRequest("DELETE"));
    expect(removed.status).toBe(204);
    expect(bucket.objects.size).toBe(0);
  });

  it("rejects digest mismatches and immutable-key metadata conflicts", async () => {
    const bucket = new FakeR2Bucket();
    const wrongDigestRequest = mediaRequest("PUT", new TextEncoder().encode("different"));
    expect((await execute(bucket, wrongDigestRequest)).status).toBe(422);

    expect((await execute(bucket, mediaRequest("PUT", bytes))).status).toBe(201);
    const conflicting = mediaRequest("PUT", bytes, { "content-type": "image/png" });
    expect((await execute(bucket, conflicting)).status).toBe(409);
  });

  it("bounds streamed uploads when content length is absent or misleading", async () => {
    const bucket = new FakeR2Bucket();
    const overLimit = mediaRequest("PUT", new Uint8Array([1, 2, 3, 4]), { "content-length": undefined });
    expect((await handleInternalMedia(overLimit, bucket as unknown as R2Bucket, 3, token)).status).toBe(413);

    const misleadingLength = mediaRequest("PUT", new Uint8Array([1, 2, 3, 4]), { "content-length": "1" });
    expect((await handleInternalMedia(misleadingLength, bucket as unknown as R2Bucket, 3, token)).status).toBe(413);
    expect(bucket.objects.size).toBe(0);
  });

  it("rejects unsafe buffered-upload limits", async () => {
    const bucket = new FakeR2Bucket();
    expect((await handleInternalMedia(
      mediaRequest("PUT", bytes),
      bucket as unknown as R2Bucket,
      25 * 1024 * 1024 + 1,
      token,
    )).status).toBe(503);
  });
});

function mediaRequest(
  method: string,
  body?: Uint8Array,
  overrides: Record<string, string | undefined> = {},
): Request {
  const headers = new Headers({
    authorization: `Bearer ${token}`,
    "x-portfolio-media-key": key,
    "x-portfolio-media-sha256": digest,
    "x-portfolio-media-content-type": "image/webp",
    "content-type": "image/webp",
    "if-none-match": "*",
  });
  for (const [name, value] of Object.entries(overrides)) {
    if (value === undefined) headers.delete(name);
    else headers.set(name, value);
  }
  const init: RequestInit = {
    method,
    headers,
  };
  if (method === "PUT" && body) init.body = body;
  return new Request("https://yousef.codes/internal/media/v1/asset", init);
}

function execute(bucket: FakeR2Bucket, request: Request): Promise<Response> {
  return handleInternalMedia(request, bucket as unknown as R2Bucket, 15_728_640, token);
}

interface StoredObject {
  key: string;
  bytes: Uint8Array;
  size: number;
  httpMetadata: R2HTTPMetadata;
  customMetadata: Record<string, string>;
  sha256: ArrayBuffer;
}

class FakeR2Bucket {
  readonly objects = new Map<string, StoredObject>();
  lastPutOnlyIf: R2Conditional | Headers | undefined;

  async put(keyValue: string, value: ArrayBuffer, options?: R2PutOptions): Promise<R2Object | null> {
    this.lastPutOnlyIf = options?.onlyIf;
    if (this.objects.has(keyValue) && !(options?.onlyIf instanceof Headers) && options?.onlyIf?.etagDoesNotMatch === "*") {
      return null;
    }
    const object: StoredObject = {
      key: keyValue,
      bytes: new Uint8Array(value),
      size: value.byteLength,
      httpMetadata: options?.httpMetadata as R2HTTPMetadata,
      customMetadata: options?.customMetadata ?? {},
      sha256: options?.sha256 as ArrayBuffer,
    };
    this.objects.set(keyValue, object);
    return this.asR2Object(object);
  }

  async head(keyValue: string): Promise<R2Object | null> {
    const object = this.objects.get(keyValue);
    return object ? this.asR2Object(object) : null;
  }

  async get(keyValue: string): Promise<R2ObjectBody | null> {
    const object = this.objects.get(keyValue);
    if (!object) return null;
    return {
      ...this.asR2Object(object),
      body: new ReadableStream({ start(controller) { controller.enqueue(object.bytes); controller.close(); } }),
      bodyUsed: false,
      arrayBuffer: async () => object.bytes.slice().buffer,
      bytes: async () => object.bytes.slice(),
      text: async () => new TextDecoder().decode(object.bytes),
      json: async <T>() => JSON.parse(new TextDecoder().decode(object.bytes)) as T,
      blob: async () => new Blob([object.bytes]),
    } as R2ObjectBody;
  }

  async delete(keyValue: string): Promise<void> {
    this.objects.delete(keyValue);
  }

  private asR2Object(object: StoredObject): R2Object {
    return {
      key: object.key,
      version: "1",
      size: object.size,
      etag: "etag",
      httpEtag: '"etag"',
      checksums: {
        sha256: object.sha256,
        toJSON: () => ({ sha256: digest }),
      },
      uploaded: new Date(0),
      httpMetadata: object.httpMetadata,
      customMetadata: object.customMetadata,
      storageClass: "Standard",
      writeHttpMetadata(headers: Headers) {
        if (object.httpMetadata.contentType) headers.set("content-type", object.httpMetadata.contentType);
      },
    } as R2Object;
  }
}
