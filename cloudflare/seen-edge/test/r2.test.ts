import { describe, expect, it, vi } from "vitest";
import { readImmutableRegistryObject } from "../src/r2";

function object(bytes: Uint8Array): R2ObjectBody {
  return {
    key: "v1/blobs/sha256/digest",
    version: "version-1",
    size: bytes.byteLength,
    etag: "r2-etag",
    httpEtag: '"r2-etag"',
    checksums: { toJSON: () => ({}) },
    uploaded: new Date("2026-08-21T00:00:00.000Z"),
    storageClass: "Standard",
    body: new ReadableStream({ start(controller) { controller.enqueue(bytes); controller.close(); } }),
    bodyUsed: false,
    writeHttpMetadata() {},
    arrayBuffer: async () => bytes.slice().buffer,
    bytes: async () => bytes,
    text: async () => new TextDecoder().decode(bytes),
    json: async <T>() => JSON.parse(new TextDecoder().decode(bytes)) as T,
    blob: async () => new Blob([bytes]),
  };
}

describe("global immutable R2 reads", () => {
  it("streams a content-addressed public blob with immutable headers", async () => {
    const digest = "a".repeat(64);
    const stored = object(new TextEncoder().encode("archive"));
    const get = vi.fn(async () => stored);
    const response = await readImmutableRegistryObject(
      new Request(`https://seen.example/packages/api/v1/blobs/sha256/${digest}`),
      { SEEN_PUBLIC: bucket(get), SEEN_METADATA: bucket(get), SEEN_OBJECT_PREFIX: "v1" },
    );
    expect(get).toHaveBeenCalledWith(`v1/blobs/sha256/${digest}`, expect.any(Object));
    expect(response?.status).toBe(200);
    expect(response?.headers.get("Cache-Control")).toBe("public,max-age=31536000,immutable");
    expect(response?.headers.get("ETag")).toBe(`"sha256:${digest}"`);
    expect(await response?.text()).toBe("archive");
  });

  it("keeps timestamp metadata short-lived and supports HEAD", async () => {
    const stored = object(new TextEncoder().encode("{}"));
    const get = vi.fn(async () => stored);
    const response = await readImmutableRegistryObject(
      new Request("https://seen.example/packages/api/v1/metadata/timestamp.json", { method: "HEAD" }),
      { SEEN_PUBLIC: bucket(get), SEEN_METADATA: bucket(get), SEEN_OBJECT_PREFIX: "v1" },
    );
    expect(response?.headers.get("Cache-Control")).toBe("public,max-age=300,must-revalidate");
    expect(await response?.text()).toBe("");
  });

  it("does not expose arbitrary R2 keys", async () => {
    expect(await readImmutableRegistryObject(
      new Request("https://seen.example/packages/api/v1/metadata/../../private/key"),
      { SEEN_PUBLIC: {} as R2Bucket, SEEN_METADATA: {} as R2Bucket, SEEN_OBJECT_PREFIX: "v1" },
    )).toBeNull();
  });
});

function bucket(get: ReturnType<typeof vi.fn>): R2Bucket {
  const partial: Partial<R2Bucket> = { get: get as R2Bucket["get"] };
  return partial as R2Bucket;
}
