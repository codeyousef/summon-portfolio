import { describe, expect, it } from "vitest";
import { replaceSessionCookie } from "../src/session-cookie";

describe("edge session cookie forwarding", () => {
  const sessionId = "abcdefghijklmnopqrstuvwxyzABCDEF";

  it("injects the edge-selected Aether session id", () => {
    const headers = new Headers();

    replaceSessionCookie(headers, "admin_session", sessionId);

    expect(headers.get("cookie")).toBe(`admin_session=${sessionId}`);
  });

  it("replaces stale session values without dropping unrelated cookies", () => {
    const headers = new Headers({ cookie: "theme=dark; admin_session=stale-uuid; locale=en" });

    replaceSessionCookie(headers, "admin_session", sessionId);

    expect(headers.get("cookie")).toBe(`theme=dark; locale=en; admin_session=${sessionId}`);
  });

  it("removes a stale session so Aether can issue the canonical new id", () => {
    const headers = new Headers({ cookie: "theme=dark; admin_session=stale-uuid" });

    replaceSessionCookie(headers, "admin_session", null);

    expect(headers.get("cookie")).toBe("theme=dark");
  });

  it("removes the cookie header when the stale session was its only value", () => {
    const headers = new Headers({ cookie: "admin_session=stale-uuid" });

    replaceSessionCookie(headers, "admin_session", null);

    expect(headers.has("cookie")).toBe(false);
  });

  it("rejects ids outside the shared 32-character contract", () => {
    expect(() => replaceSessionCookie(new Headers(), "admin_session", crypto.randomUUID()))
      .toThrow("Invalid edge session id");
  });
});
