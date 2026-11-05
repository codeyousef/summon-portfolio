export const MAX_BUFFERED_BODY_BYTES = 32 * 1024 * 1024;

export async function readBoundedBody(
  body: ReadableStream<Uint8Array> | null,
  maximumBytes: number,
): Promise<Uint8Array | null> {
  if (
    !Number.isSafeInteger(maximumBytes)
    || maximumBytes < 1
    || maximumBytes > MAX_BUFFERED_BODY_BYTES
  ) {
    throw new Error("invalid buffered body limit");
  }
  if (body === null) return new Uint8Array();

  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maximumBytes) {
        await reader.cancel("request body exceeds its byte limit").catch(() => undefined);
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const result = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return result;
}

export async function readBoundedJsonRequest(request: Request, maximumBytes: number): Promise<unknown> {
  const declaredLength = Number(request.headers.get("content-length"));
  if (Number.isFinite(declaredLength) && declaredLength > maximumBytes) {
    throw new Error("request body too large");
  }
  const bytes = await readBoundedBody(request.body, maximumBytes);
  if (bytes === null) throw new Error("request body too large");
  return JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes)) as unknown;
}
