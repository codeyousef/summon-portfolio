import { authorizedInternalRequest } from "./internal-auth";
import { readBoundedBody } from "./bounded-body";

const MAX_RECEIPT_BYTES = 25 * 1024 * 1024;
const RECEIPT_KEY = /^sha256\/([a-f0-9]{64})\/(?:uploads\/([a-f0-9]{32})\/)?([A-Za-z0-9][A-Za-z0-9._-]{0,191})$/;
const ALLOWED_CONTENT_TYPES = new Set([
  "application/json",
  "application/pdf",
  "image/jpeg",
  "image/png",
  "image/webp",
  "text/csv",
  "text/plain",
]);

interface ReceiptKey {
  key: string;
  digest: string;
  uploadId: string | null;
  filename: string;
}

export async function handleFinOpsReceipt(
  request: Request,
  bucket: R2Bucket,
  expectedToken: string,
): Promise<Response> {
  if (!(await authorizedInternalRequest(request, expectedToken))) return receiptError("Unauthorized", 401);
  const parsed = parseReceiptKey(request.headers.get("x-finops-receipt-key"));
  if (!parsed || request.headers.get("x-finops-receipt-sha256") !== parsed.digest) {
    return receiptError("Invalid receipt key or digest", 400);
  }
  if (request.method === "PUT") return putReceipt(request, bucket, parsed);
  if (request.method === "GET") return getReceipt(request, bucket, parsed);
  if (request.method === "HEAD") return headReceipt(request, bucket, parsed);
  return new Response("Method not allowed", {
    status: 405,
    headers: { allow: "PUT, GET, HEAD", "cache-control": "no-store" },
  });
}

async function headReceipt(request: Request, bucket: R2Bucket, parsed: ReceiptKey): Promise<Response> {
  const expectedContentType = receiptContentType(request.headers.get("x-finops-receipt-content-type"));
  if (!expectedContentType) return receiptError("Expected receipt content type is required", 400);
  const object = await bucket.head(parsed.key);
  if (!object) return receiptError("Receipt not found", 404);
  if (!receiptMatches(object, parsed, expectedContentType, object.size)) {
    return receiptError("Stored receipt metadata failed integrity validation", 502);
  }
  return new Response(null, {
    status: 200,
    headers: {
      "content-type": expectedContentType,
      "content-length": String(object.size),
      "x-finops-receipt-sha256": parsed.digest,
      "cache-control": "private, no-store",
    },
  });
}

export function parseReceiptKey(value: string | null): ReceiptKey | null {
  if (!value) return null;
  const match = RECEIPT_KEY.exec(value);
  if (!match?.[1] || !match[3]) return null;
  return { key: value, digest: match[1], uploadId: match[2] ?? null, filename: match[3] };
}

async function putReceipt(request: Request, bucket: R2Bucket, parsed: ReceiptKey): Promise<Response> {
  if (request.headers.get("if-none-match") !== "*") return receiptError("Conditional create is required", 428);
  const contentType = receiptContentType(request.headers.get("content-type"));
  if (!contentType) return receiptError("Unsupported receipt content type", 415);
  const declaredLength = Number(request.headers.get("content-length"));
  if (Number.isFinite(declaredLength) && declaredLength > MAX_RECEIPT_BYTES) return receiptError("Receipt is too large", 413);
  const bytes = await readBoundedReceiptBody(request.body);
  if (bytes === null || bytes.byteLength === 0) {
    return receiptError(bytes === null ? "Receipt is too large" : "Receipt is empty", 413);
  }
  const digestBytes = await crypto.subtle.digest("SHA-256", bytes);
  if (hex(digestBytes) !== parsed.digest) return receiptError("Receipt body digest does not match its key", 422);
  const object = await bucket.put(parsed.key, bytes, {
    onlyIf: { etagDoesNotMatch: "*" },
    httpMetadata: {
      contentType,
      cacheControl: "private, no-store",
      contentDisposition: `attachment; filename="${parsed.filename}"`,
    },
    customMetadata: {
      sha256: parsed.digest,
      contentType,
      ...(parsed.uploadId ? { uploadId: parsed.uploadId, lifecycle: "pending" } : {}),
    },
    sha256: digestBytes,
  });
  if (object) {
    return Response.json(
      { key: parsed.key, sha256: parsed.digest, size: bytes.byteLength, contentType, created: true },
      { status: 201, headers: { "cache-control": "no-store" } },
    );
  }
  const existing = await bucket.head(parsed.key);
  if (existing && receiptMatches(existing, parsed, contentType, bytes.byteLength)) {
    return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
  }
  return receiptError("Immutable receipt key already exists with different metadata", 409);
}

export async function readBoundedReceiptBody(
  body: ReadableStream<Uint8Array> | null,
  maximumBytes: number = MAX_RECEIPT_BYTES,
): Promise<Uint8Array | null> {
  return readBoundedBody(body, maximumBytes);
}

async function getReceipt(request: Request, bucket: R2Bucket, parsed: ReceiptKey): Promise<Response> {
  const expectedContentType = receiptContentType(request.headers.get("x-finops-receipt-content-type"));
  if (!expectedContentType) return receiptError("Expected receipt content type is required", 400);
  const object = await bucket.get(parsed.key);
  if (!object) return receiptError("Receipt not found", 404);
  if (!receiptMatches(object, parsed, expectedContentType, object.size)) return receiptError("Stored receipt metadata failed integrity validation", 502);
  const bytes = await object.arrayBuffer();
  if (bytes.byteLength !== object.size || hex(await crypto.subtle.digest("SHA-256", bytes)) !== parsed.digest) {
    return receiptError("Stored receipt bytes failed integrity validation", 502);
  }
  return new Response(bytes, {
    headers: {
      "content-type": expectedContentType,
      "content-length": String(bytes.byteLength),
      "content-disposition": `attachment; filename="${parsed.filename}"`,
      "x-finops-receipt-sha256": parsed.digest,
      "cache-control": "private, no-store",
      "x-content-type-options": "nosniff",
    },
  });
}

function receiptMatches(
  object: R2Object,
  parsed: ReceiptKey,
  contentType: string,
  size: number,
): boolean {
  return object.key === parsed.key
    && object.size === size
    && size > 0
    && size <= MAX_RECEIPT_BYTES
    && object.customMetadata?.sha256 === parsed.digest
    && object.customMetadata?.contentType === contentType
    && (parsed.uploadId === null || (
      object.customMetadata?.uploadId === parsed.uploadId && object.customMetadata?.lifecycle === "pending"
    ))
    && object.checksums.sha256 != null
    && hex(object.checksums.sha256) === parsed.digest
    && receiptContentType(object.httpMetadata?.contentType ?? null) === contentType;
}

function receiptContentType(value: string | null): string | null {
  const normalized = value?.split(";", 1)[0]?.trim().toLowerCase();
  return normalized && ALLOWED_CONTENT_TYPES.has(normalized) ? normalized : null;
}

function hex(value: ArrayBuffer): string {
  return [...new Uint8Array(value)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function receiptError(error: string, status: number): Response {
  return Response.json({ error }, { status, headers: { "cache-control": "no-store" } });
}
