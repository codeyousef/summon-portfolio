import { authorizedInternalRequest } from "./internal-auth";
import { readBoundedBody } from "./bounded-body";

const CONTENT_ADDRESSED_PATH = /^\/cdn\/(media|docs)\/sha256\/([a-f0-9]{64})\/([A-Za-z0-9][A-Za-z0-9._-]{0,191})$/;
const CONTENT_ADDRESSED_MEDIA_KEY = /^sha256\/([a-f0-9]{64})\/([A-Za-z0-9][A-Za-z0-9._-]{0,191})$/;
const ALLOWED_MEDIA_CONTENT_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "video/mp4",
  "video/webm",
  "video/quicktime",
]);
const MAX_MEDIA_ASSET_BYTES = 25 * 1024 * 1024;

export interface PublicObjectRoute {
  bucket: "media" | "docs";
  key: string;
}

export function parsePublicObjectRoute(pathname: string): PublicObjectRoute | null {
  const match = CONTENT_ADDRESSED_PATH.exec(pathname);
  if (!match) return null;
  const [, bucket, digest, filename] = match;
  if ((bucket !== "media" && bucket !== "docs") || !digest || !filename) return null;
  return { bucket, key: `sha256/${digest}/${filename}` };
}

interface InternalMediaKey {
  key: string;
  digest: string;
  filename: string;
}

export function parseInternalMediaKey(value: string | null): InternalMediaKey | null {
  if (!value) return null;
  const match = CONTENT_ADDRESSED_MEDIA_KEY.exec(value);
  if (!match) return null;
  const [, digest, filename] = match;
  if (!digest || !filename) return null;
  return { key: value, digest, filename };
}

export async function handleInternalMedia(
  request: Request,
  bucket: R2Bucket,
  maxAssetBytes: number,
  expectedToken: string,
): Promise<Response> {
  if (!(await authorizedInternalRequest(request, expectedToken))) {
    return new Response("Unauthorized", { status: 401, headers: { "cache-control": "no-store" } });
  }
  if (
    !Number.isSafeInteger(maxAssetBytes)
    || maxAssetBytes <= 0
    || maxAssetBytes > MAX_MEDIA_ASSET_BYTES
  ) {
    return internalMediaError("Media storage is not configured", 503);
  }
  const key = parseInternalMediaKey(request.headers.get("x-portfolio-media-key"));
  const claimedDigest = request.headers.get("x-portfolio-media-sha256");
  if (!key || claimedDigest !== key.digest) return internalMediaError("Invalid media key or digest", 400);

  if (request.method === "PUT") return putInternalMedia(request, bucket, key, maxAssetBytes);
  if (request.method === "GET") return getInternalMedia(request, bucket, key, maxAssetBytes);
  if (request.method === "DELETE") return deleteInternalMedia(bucket, key, maxAssetBytes);
  return new Response("Method not allowed", {
    status: 405,
    headers: { allow: "PUT, GET, DELETE", "cache-control": "no-store" },
  });
}

async function putInternalMedia(
  request: Request,
  bucket: R2Bucket,
  key: InternalMediaKey,
  maxAssetBytes: number,
): Promise<Response> {
  if (request.headers.get("if-none-match") !== "*") {
    return internalMediaError("Conditional create is required", 428);
  }
  const contentType = parseMediaContentType(request.headers.get("content-type"));
  if (!contentType) return internalMediaError("Unsupported media content type", 415);
  const declaredLength = Number(request.headers.get("content-length"));
  if (Number.isFinite(declaredLength) && declaredLength > maxAssetBytes) {
    return internalMediaError("Media asset is too large", 413);
  }
  const bytes = await readBoundedBody(request.body, maxAssetBytes);
  if (bytes === null || bytes.byteLength === 0) {
    return internalMediaError(bytes === null ? "Media asset is too large" : "Media asset is empty", 413);
  }
  const digestBytes = await crypto.subtle.digest("SHA-256", bytes);
  if (hex(digestBytes) !== key.digest) return internalMediaError("Media body digest does not match its key", 422);

  const object = await bucket.put(key.key, bytes, {
    onlyIf: { etagDoesNotMatch: "*" },
    httpMetadata: {
      contentType,
      cacheControl: "public, max-age=31536000, immutable",
    },
    customMetadata: {
      sha256: key.digest,
      contentType,
    },
    sha256: digestBytes,
  });
  if (object) {
    return Response.json(
      { key: key.key, sha256: key.digest, size: bytes.byteLength, created: true },
      { status: 201, headers: { "cache-control": "no-store" } },
    );
  }

  const existing = await bucket.head(key.key);
  if (existing && storedObjectMatches(existing, key, contentType, bytes.byteLength, maxAssetBytes)) {
    return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
  }
  return internalMediaError("Immutable media key already exists with different metadata", 409);
}

async function getInternalMedia(
  request: Request,
  bucket: R2Bucket,
  key: InternalMediaKey,
  maxAssetBytes: number,
): Promise<Response> {
  const expectedContentType = parseMediaContentType(request.headers.get("x-portfolio-media-content-type"));
  if (!expectedContentType) return internalMediaError("Expected media content type is required", 400);
  const object = await bucket.get(key.key);
  if (!object) return internalMediaError("Media asset not found", 404);
  if (!storedObjectMatches(object, key, expectedContentType, object.size, maxAssetBytes)) {
    return internalMediaError("Stored media metadata failed integrity validation", 502);
  }
  const bytes = await object.arrayBuffer();
  if (bytes.byteLength !== object.size || hex(await crypto.subtle.digest("SHA-256", bytes)) !== key.digest) {
    return internalMediaError("Stored media bytes failed integrity validation", 502);
  }
  return new Response(bytes, {
    status: 200,
    headers: {
      "content-type": expectedContentType,
      "content-length": String(bytes.byteLength),
      "x-portfolio-media-sha256": key.digest,
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}

async function deleteInternalMedia(
  bucket: R2Bucket,
  key: InternalMediaKey,
  maxAssetBytes: number,
): Promise<Response> {
  const existing = await bucket.head(key.key);
  if (!existing) return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
  const storedContentType = parseMediaContentType(existing.httpMetadata?.contentType ?? null);
  if (!storedContentType || !storedObjectMatches(existing, key, storedContentType, existing.size, maxAssetBytes)) {
    return internalMediaError("Stored media metadata failed integrity validation", 502);
  }
  await bucket.delete(key.key);
  return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
}

function storedObjectMatches(
  object: R2Object,
  key: InternalMediaKey,
  contentType: string,
  expectedSize: number,
  maxAssetBytes: number,
): boolean {
  return object.key === key.key
    && object.size === expectedSize
    && object.size > 0
    && object.size <= maxAssetBytes
    && object.customMetadata?.sha256 === key.digest
    && object.customMetadata?.contentType === contentType
    && object.checksums.sha256 != null
    && hex(object.checksums.sha256) === key.digest
    && parseMediaContentType(object.httpMetadata?.contentType ?? null) === contentType;
}

function parseMediaContentType(value: string | null): string | null {
  const normalized = value?.split(";", 1)[0]?.trim().toLowerCase();
  return normalized && ALLOWED_MEDIA_CONTENT_TYPES.has(normalized) ? normalized : null;
}

function hex(value: ArrayBuffer): string {
  return [...new Uint8Array(value)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function internalMediaError(error: string, status: number): Response {
  return Response.json({ error }, { status, headers: { "cache-control": "no-store" } });
}

export async function servePublicObject(request: Request, bucket: R2Bucket): Promise<Response> {
  const route = parsePublicObjectRoute(new URL(request.url).pathname);
  if (!route) return new Response("Not found", { status: 404 });
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method not allowed", { status: 405, headers: { allow: "GET, HEAD" } });
  }

  if (request.method === "HEAD") {
    const head = await bucket.head(route.key);
    if (!head) return new Response("Not found", { status: 404 });
    if (route.bucket === "media" && !publicMediaMetadataMatches(head, route.key)) {
      return new Response("Media integrity check failed", { status: 502, headers: { "cache-control": "no-store" } });
    }
    return new Response(null, { headers: objectHeaders(head, route.bucket) });
  }

  const object = await bucket.get(route.key, {
    onlyIf: request.headers,
    range: request.headers,
  });
  if (!object) return new Response("Not found", { status: 404 });
  if (route.bucket === "media" && !publicMediaMetadataMatches(object, route.key)) {
    return new Response("Media integrity check failed", { status: 502, headers: { "cache-control": "no-store" } });
  }
  const headers = objectHeaders(object, route.bucket);
  if (!("body" in object)) return new Response(null, { status: 304, headers });
  if (object.range) {
    const offset = "suffix" in object.range ? object.size - object.range.suffix : (object.range.offset ?? 0);
    const length = "suffix" in object.range ? object.range.suffix : (object.range.length ?? object.size - offset);
    headers.set("content-range", `bytes ${offset}-${offset + length - 1}/${object.size}`);
    headers.set("content-length", String(length));
  }
  return new Response(object.body, { status: object.range ? 206 : 200, headers });
}

function publicMediaMetadataMatches(object: R2Object, key: string): boolean {
  const parsed = parseInternalMediaKey(key);
  const contentType = parseMediaContentType(object.httpMetadata?.contentType ?? null);
  return parsed != null
    && object.key === key
    && contentType != null
    && object.customMetadata?.sha256 === parsed.digest
    && object.customMetadata?.contentType === contentType
    && object.checksums.sha256 != null
    && hex(object.checksums.sha256) === parsed.digest;
}

function objectHeaders(object: R2Object, kind: "media" | "docs"): Headers {
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("etag", object.httpEtag);
  headers.set("cache-control", "public, max-age=31536000, immutable");
  headers.set("x-content-type-options", "nosniff");
  headers.set("cross-origin-resource-policy", "same-origin");
  headers.set("accept-ranges", "bytes");
  if (kind === "docs" && headers.get("content-type")?.startsWith("text/html")) {
    headers.set("content-security-policy", "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'");
  }
  return headers;
}
