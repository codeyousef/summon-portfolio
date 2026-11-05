import { describe, expect, it } from "vitest";
import { readBoundedJsonRequest } from "../src/bounded-body";

describe("bounded JSON requests", () => {
  it("parses a body at its exact byte limit", async () => {
    const body = new TextEncoder().encode('{"ok":true}');
    await expect(readBoundedJsonRequest(request(body), body.byteLength)).resolves.toEqual({ ok: true });
  });

  it("rejects streamed bodies that exceed an absent or misleading content length", async () => {
    const body = new TextEncoder().encode('{"too":"large"}');
    await expect(readBoundedJsonRequest(request(body), body.byteLength - 1)).rejects.toThrow("request body too large");
    await expect(readBoundedJsonRequest(request(body, "1"), body.byteLength - 1)).rejects.toThrow("request body too large");
  });

  it("rejects invalid UTF-8 and malformed JSON", async () => {
    await expect(readBoundedJsonRequest(request(new Uint8Array([0xff])), 1)).rejects.toThrow();
    await expect(readBoundedJsonRequest(request(new TextEncoder().encode("{")), 1)).rejects.toThrow();
  });
});

function request(body: Uint8Array, contentLength?: string): Request {
  const headers = new Headers({ "content-type": "application/json" });
  if (contentLength !== undefined) headers.set("content-length", contentLength);
  return new Request("https://internal.example/control", { method: "POST", headers, body });
}
