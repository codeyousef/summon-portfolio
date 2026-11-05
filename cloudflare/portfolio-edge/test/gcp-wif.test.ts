import { afterEach, describe, expect, it, vi } from "vitest";
import { brokerAccessAssertion, googleServiceAccountAccessToken } from "../src/gcp-wif";
import type { Env } from "../src/types";

afterEach(() => vi.restoreAllMocks());

const nowSeconds = 1_800_000_000;
const clientId = `${"a".repeat(32)}.access`;
const accessAudience = "b".repeat(64);

function env(overrides: Partial<Env> = {}): Env {
  return {
    PORTFOLIO_ENV: "dev",
    PORTFOLIO_CANONICAL_ORIGIN: "https://dev.yousef.codes",
    EDGE_ORIGIN_TOKEN: "edge-origin-token-that-is-long-enough-123",
    GCP_WIF_ACCESS_BROKER_URL: "https://dev.yousef.codes/internal/gcp-wif/assertion",
    GCP_WIF_ACCESS_CLIENT_ID: clientId,
    GCP_WIF_ACCESS_CLIENT_SECRET: "access-secret-that-is-long-enough-123",
    GCP_WIF_ACCESS_ISSUER: "https://felidai.cloudflareaccess.com",
    GCP_WIF_ACCESS_AUDIENCE: accessAudience,
    GCP_WIF_PROVIDER_AUDIENCE: "//iam.googleapis.com/projects/123456789/locations/global/workloadIdentityPools/cloudflare-dev/providers/access-dev",
    GCP_WIF_SERVICE_ACCOUNT: "finops-reader@felidai-dev.iam.gserviceaccount.com",
    ...overrides,
  } as Env;
}

function assertion(overrides: Record<string, unknown> = {}): string {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `${encode({ alg: "RS256", typ: "JWT" })}.${encode({
    iss: "https://felidai.cloudflareaccess.com",
    aud: [accessAudience],
    common_name: clientId,
    sub: "",
    type: "app",
    iat: nowSeconds - 30,
    exp: nowSeconds + 300,
    ...overrides,
  })}.signature`;
}

describe("Cloudflare Access to GCP WIF", () => {
  it("brokers only the expected short-lived Access service assertion", async () => {
    const response = brokerAccessAssertion(new Request(
      "https://dev.yousef.codes/internal/gcp-wif/assertion",
      { method: "POST", headers: { "cf-access-jwt-assertion": assertion() } },
    ), env(), nowSeconds);

    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toBe("private, no-store");
    expect(await response.json()).toEqual({ subjectToken: assertion() });

    const forged = brokerAccessAssertion(new Request(
      "https://dev.yousef.codes/internal/gcp-wif/assertion",
      { method: "POST", headers: { "cf-access-jwt-assertion": assertion({ common_name: `${"c".repeat(32)}.access` }) } },
    ), env(), nowSeconds);
    expect(forged.status).toBe(401);

    const wrongOrigin = brokerAccessAssertion(new Request(
      "https://other.example/internal/gcp-wif/assertion",
      { method: "POST", headers: { "cf-access-jwt-assertion": assertion() } },
    ), env(), nowSeconds);
    expect(wrongOrigin.status).toBe(404);
  });

  it("exchanges Access identity through STS and service-account impersonation without a Google key", async () => {
    vi.spyOn(Date, "now").mockReturnValue(nowSeconds * 1_000);
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: assertion() }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "short-lived-google-token", expireTime: "2027-01-15T08:15:00Z" }));

    expect(await googleServiceAccountAccessToken(env())).toBe("short-lived-google-token");
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0]![1]).toMatchObject({
      method: "POST",
      headers: expect.objectContaining({
        "cf-access-client-id": clientId,
        "cf-access-client-secret": "access-secret-that-is-long-enough-123",
      }),
    });
    expect(fetchMock.mock.calls[0]![1]?.headers).not.toEqual(expect.objectContaining({ authorization: expect.anything() }));
    expect(JSON.parse(String(fetchMock.mock.calls[1]![1]?.body))).toMatchObject({
      audience: env().GCP_WIF_PROVIDER_AUDIENCE,
      subjectTokenType: "urn:ietf:params:oauth:token-type:id_token",
    });
    expect(String(fetchMock.mock.calls[2]![0])).toContain(encodeURIComponent("finops-reader@felidai-dev.iam.gserviceaccount.com"));
    expect(fetchMock.mock.calls[2]![1]).toMatchObject({
      headers: expect.objectContaining({ authorization: "Bearer federated-token" }),
    });
  });

  it("allows only the exact dev Workers.dev broker fallback", async () => {
    vi.spyOn(Date, "now").mockReturnValue(nowSeconds * 1_000);
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ subjectToken: assertion() }))
      .mockResolvedValueOnce(Response.json({ access_token: "federated-token", expires_in: 600, token_type: "Bearer" }))
      .mockResolvedValueOnce(Response.json({ accessToken: "short-lived-google-token", expireTime: "2027-01-15T08:15:00Z" }));

    await expect(googleServiceAccountAccessToken(env({
      GCP_WIF_ACCESS_BROKER_URL: "https://yousef-portfolio-edge-dev.yousef-d2f.workers.dev/internal/gcp-wif/assertion",
    }))).resolves.toBe("short-lived-google-token");

    await expect(googleServiceAccountAccessToken(env({
      GCP_WIF_ACCESS_BROKER_URL: "https://attacker.example/internal/gcp-wif/assertion",
    }))).rejects.toThrow("invalid GCP WIF Access broker URL");
  });

  it("is inert when entirely absent and rejects partial configuration", async () => {
    expect(await googleServiceAccountAccessToken({} as Env)).toBeNull();
    const partial = env();
    delete partial.GCP_WIF_SERVICE_ACCOUNT;
    await expect(googleServiceAccountAccessToken(partial)).rejects.toThrow(
      "incomplete GCP WIF configuration",
    );
  });

  it("stops an oversized streamed credential response despite a misleading length", async () => {
    vi.spyOn(Date, "now").mockReturnValue(nowSeconds * 1_000);
    const chunk = new Uint8Array(8 * 1024 + 1).fill(0x20);
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(chunk);
        controller.enqueue(chunk);
        controller.close();
      },
    });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(new Response(body, {
      headers: { "content-length": "1", "content-type": "application/json" },
    }));

    await expect(googleServiceAccountAccessToken(env())).rejects.toThrow("credential response is too large");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
