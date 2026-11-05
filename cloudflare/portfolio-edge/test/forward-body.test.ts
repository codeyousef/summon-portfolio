import { describe, expect, it } from "vitest";
import { finOpsForwardBodyLimit, prepareBoundedFinOpsForward } from "../src/forward-body";

describe("FinOps Container forwarding body limits", () => {
  it("maps every FinOps mutation surface to its route-specific limit", () => {
    expect(limit("/admin/spending/manual-entry")).toBe(28_311_552);
    expect(limit("/admin/spending/import.csv")).toBe(1_114_112);
    expect(limit("/api/admin/finops/receipt")).toBe(25 * 1024 * 1024);
    expect(limit("/api/admin/finops/manual-entry")).toBe(1024 * 1024);
    expect(limit("/admin/spending/recurring")).toBe(1024 * 1024);
    expect(limit("/admin/spending/budget")).toBe(1024 * 1024);
    expect(finOpsForwardBodyLimit(request("GET", "/api/admin/finops/summary"))).toBeNull();
    expect(finOpsForwardBodyLimit(request("POST", "/contact"))).toBeNull();
  });

  it("buffers an allowed body and replaces its declared length with the observed length", async () => {
    const input = request("POST", "/api/admin/finops/manual-entry", new Uint8Array([1, 2, 3]), "1");
    const prepared = await prepareBoundedFinOpsForward(input);
    expect(prepared).toBeInstanceOf(Request);
    const forwarded = prepared as Request;
    expect(forwarded.headers.get("content-length")).toBe("3");
    expect(new Uint8Array(await forwarded.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
  });

  it("rejects an oversized streamed body before Container forwarding", async () => {
    const body = new Uint8Array(1024 * 1024 + 1);
    const prepared = await prepareBoundedFinOpsForward(
      request("POST", "/api/admin/finops/manual-entry", body, "1"),
    );
    expect(prepared).toBeInstanceOf(Response);
    expect((prepared as Response).status).toBe(413);
    expect((prepared as Response).headers.get("cache-control")).toBe("private, no-store");
  });

  it("rejects an invalid or oversized declared length without consuming the body", async () => {
    for (const declared of ["invalid", String(1024 * 1024 + 1)]) {
      const prepared = await prepareBoundedFinOpsForward(
        request("POST", "/api/admin/finops/budget", new Uint8Array([1]), declared),
      );
      expect(prepared).toBeInstanceOf(Response);
      expect((prepared as Response).status).toBe(413);
    }
  });
});

function limit(pathname: string): number | null {
  return finOpsForwardBodyLimit(request("POST", pathname));
}

function request(method: string, pathname: string, body?: Uint8Array, contentLength?: string): Request {
  const headers = new Headers();
  if (contentLength !== undefined) headers.set("content-length", contentLength);
  return new Request(`https://yousef.codes${pathname}`, {
    method,
    headers,
    ...(body === undefined ? {} : { body }),
  });
}
