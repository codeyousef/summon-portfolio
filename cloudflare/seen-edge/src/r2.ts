import { ContractError, requireMetadataFilename, requireSha256 } from "./contracts";

export interface PublicReadEnv {
  SEEN_PUBLIC: R2Bucket;
  SEEN_METADATA: R2Bucket;
  SEEN_OBJECT_PREFIX: string;
}

export async function readImmutableRegistryObject(request: Request, env: PublicReadEnv): Promise<Response | null> {
  const url = new URL(request.url);
  const metadataMatch = /^\/packages\/api\/v1\/metadata\/([^/]+)$/.exec(url.pathname);
  const blobMatch = /^\/packages\/api\/v1\/blobs\/sha256\/([0-9a-f]{64})$/.exec(url.pathname);
  if (!metadataMatch && !blobMatch) return null;
  if (request.method !== "GET" && request.method !== "HEAD") {
    return emptyResponse(405, { Allow: "GET, HEAD" });
  }

  try {
    if (metadataMatch) {
      const filename = requireMetadataFilename(decodeURIComponent(requireNotNull(metadataMatch[1])));
      const key = `${safePrefix(env.SEEN_OBJECT_PREFIX)}/metadata/${filename}`;
      return serveR2(request, env.SEEN_METADATA, key, {
        contentType: "application/vnd.seen.tuf+json",
        digestHeader: "X-Seen-Metadata-Sha256",
        expectedDigest: null,
        cacheControl: filename === "timestamp.json" || filename === "root.json"
          ? "public,max-age=300,must-revalidate"
          : "public,max-age=31536000,immutable",
      });
    }
    const digest = requireSha256(requireNotNull(blobMatch?.[1]));
    const key = `${safePrefix(env.SEEN_OBJECT_PREFIX)}/blobs/sha256/${digest}`;
    return serveR2(request, env.SEEN_PUBLIC, key, {
      contentType: "application/gzip",
      digestHeader: "X-Seen-Archive-Sha256",
      expectedDigest: digest,
      cacheControl: "public,max-age=31536000,immutable",
    });
  } catch (error) {
    if (error instanceof ContractError || error instanceof URIError) return emptyResponse(404);
    throw error;
  }
}

interface ServeOptions {
  contentType: string;
  digestHeader: string;
  expectedDigest: string | null;
  cacheControl: string;
}

async function serveR2(request: Request, bucket: R2Bucket, key: string, options: ServeOptions): Promise<Response> {
  const object = await bucket.get(key, { onlyIf: request.headers, range: request.headers });
  if (object === null) return emptyResponse(404);
  if (!("body" in object)) return emptyResponse(304, { ETag: object.httpEtag });

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Content-Type", options.contentType);
  headers.set("Cache-Control", options.cacheControl);
  headers.set("ETag", options.expectedDigest ? `"sha256:${options.expectedDigest}"` : object.httpEtag);
  headers.set("X-Content-Type-Options", "nosniff");
  if (options.expectedDigest) headers.set(options.digestHeader, options.expectedDigest);

  let status = 200;
  if (object.range) {
    const offset = "suffix" in object.range ? object.size - object.range.suffix : object.range.offset ?? 0;
    const length = "suffix" in object.range ? object.range.suffix : object.range.length ?? object.size - offset;
    headers.set("Content-Range", `bytes ${offset}-${offset + length - 1}/${object.size}`);
    headers.set("Content-Length", String(length));
    status = 206;
  } else {
    headers.set("Content-Length", String(object.size));
  }
  return new Response(request.method === "HEAD" ? null : object.body, { status, headers });
}

function safePrefix(value: string): string {
  const prefix = value.replace(/^\/+|\/+$/g, "");
  if (!/^[A-Za-z0-9](?:[A-Za-z0-9._/-]{0,254}[A-Za-z0-9])?$/.test(prefix) || prefix.includes("//")) {
    throw new ContractError("object prefix is invalid");
  }
  if (prefix.split("/").some((part) => part === "." || part === "..")) throw new ContractError("object prefix is invalid");
  return prefix;
}

function emptyResponse(status: number, values: Record<string, string> = {}): Response {
  const headers = new Headers(values);
  headers.set("Cache-Control", "no-store");
  headers.set("Content-Length", "0");
  headers.set("X-Content-Type-Options", "nosniff");
  return new Response(null, { status, headers });
}

function requireNotNull<T>(value: T | null | undefined): T {
  if (value === null || value === undefined) throw new ContractError("route value is missing");
  return value;
}
