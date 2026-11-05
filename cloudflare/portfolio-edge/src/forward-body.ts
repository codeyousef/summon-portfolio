import { readBoundedBody } from "./bounded-body";

const MAX_FINOPS_JSON_BYTES = 1024 * 1024;
const MAX_FINOPS_RECEIPT_BYTES = 25 * 1024 * 1024;
const MAX_FINOPS_CSV_MULTIPART_BYTES = 1_114_112;
const MAX_FINOPS_MANUAL_MULTIPART_BYTES = 28_311_552;

export function finOpsForwardBodyLimit(request: Request): number | null {
  if (request.method !== "POST") return null;
  const pathname = new URL(request.url).pathname;
  if (pathname === "/admin/spending/manual-entry") return MAX_FINOPS_MANUAL_MULTIPART_BYTES;
  if (pathname === "/admin/spending/import.csv") return MAX_FINOPS_CSV_MULTIPART_BYTES;
  if (pathname === "/api/admin/finops/receipt") return MAX_FINOPS_RECEIPT_BYTES;
  if (pathname === "/admin/spending/budget" || pathname === "/admin/spending/recurring" || pathname.startsWith("/api/admin/finops/")) {
    return MAX_FINOPS_JSON_BYTES;
  }
  return null;
}

export async function prepareBoundedFinOpsForward(
  request: Request,
): Promise<Request | Response> {
  const maximumBytes = finOpsForwardBodyLimit(request);
  if (maximumBytes === null || request.body === null) return request;

  const declaredLength = request.headers.get("content-length");
  if (declaredLength !== null) {
    const declared = Number(declaredLength);
    if (!Number.isSafeInteger(declared) || declared < 0 || declared > maximumBytes) {
      return bodyTooLarge();
    }
  }
  const bytes = await readBoundedBody(request.body, maximumBytes);
  if (bytes === null) return bodyTooLarge();

  const headers = new Headers(request.headers);
  headers.set("content-length", String(bytes.byteLength));
  return new Request(request, { body: bytes, headers });
}

function bodyTooLarge(): Response {
  return Response.json({ error: "request body is too large" }, {
    status: 413,
    headers: { "cache-control": "private, no-store" },
  });
}
