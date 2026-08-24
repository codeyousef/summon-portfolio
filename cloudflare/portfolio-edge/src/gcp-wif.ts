import type { Env } from "./types";
import { readBoundedBody } from "./bounded-body";

const STS_URL = "https://sts.googleapis.com/v1/token";
const IAM_CREDENTIALS_ROOT = "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts";
const MAX_TOKEN_RESPONSE_BYTES = 16 * 1024;
const SERVICE_ACCOUNT = /^[a-z][a-z0-9-]{4,61}[a-z0-9]@[a-z][a-z0-9-]{4,61}[a-z0-9]\.iam\.gserviceaccount\.com$/;
const ACCESS_CLIENT_ID = /^[a-f0-9]{32}\.access$/;
const DEV_ACCESS_BROKER_ORIGIN = "https://yousef-portfolio-edge-dev.yousef-d2f.workers.dev";

interface WifConfig {
  brokerUrl: string;
  accessClientId: string;
  accessClientSecret: string;
  accessIssuer: string;
  accessAudience: string;
  providerAudience: string;
  serviceAccount: string;
}

interface AccessClaims {
  iss?: unknown;
  aud?: unknown;
  common_name?: unknown;
  exp?: unknown;
  iat?: unknown;
  sub?: unknown;
  type?: unknown;
}

export async function googleServiceAccountAccessToken(env: Env): Promise<string | null> {
  const config = readWifConfig(env);
  if (config === null) return null;
  const assertion = await fetchAccessAssertion(config);
  validateAccessAssertion(assertion, config, Math.floor(Date.now() / 1_000));
  const federatedToken = await exchangeFederatedToken(assertion, config.providerAudience);
  return impersonateServiceAccount(federatedToken, config.serviceAccount);
}

export function brokerAccessAssertion(request: Request, env: Env, nowSeconds = Math.floor(Date.now() / 1_000)): Response {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405, headers: { allow: "POST" } });
  const config = readWifConfig(env);
  if (config === null) return Response.json({ error: "WIF is not configured" }, { status: 503, headers: noStore() });
  if (new URL(request.url).origin !== new URL(config.brokerUrl).origin) {
    return Response.json({ error: "invalid broker origin" }, { status: 404, headers: noStore() });
  }
  const assertion = request.headers.get("cf-access-jwt-assertion")?.trim() ?? "";
  try {
    validateAccessAssertion(assertion, config, nowSeconds);
    return Response.json({ subjectToken: assertion }, { headers: noStore() });
  } catch {
    return Response.json({ error: "invalid Access assertion" }, { status: 401, headers: noStore() });
  }
}

function readWifConfig(env: Env): WifConfig | null {
  const values = [
    env.GCP_WIF_ACCESS_BROKER_URL,
    env.GCP_WIF_ACCESS_CLIENT_ID,
    env.GCP_WIF_ACCESS_CLIENT_SECRET,
    env.GCP_WIF_ACCESS_ISSUER,
    env.GCP_WIF_ACCESS_AUDIENCE,
    env.GCP_WIF_PROVIDER_AUDIENCE,
    env.GCP_WIF_SERVICE_ACCOUNT,
  ].map((value) => value?.trim() ?? "");
  if (values.every((value) => value === "")) return null;
  if (values.some((value) => value === "")) throw new Error("incomplete GCP WIF configuration");
  const [brokerUrl, accessClientId, accessClientSecret, accessIssuer, accessAudience, providerAudience, serviceAccount] = values as [string, string, string, string, string, string, string];
  const broker = new URL(brokerUrl);
  const canonicalOrigin = new URL(env.PORTFOLIO_CANONICAL_ORIGIN);
  const allowedBrokerOrigins = env.PORTFOLIO_ENV === "dev"
    ? new Set([canonicalOrigin.origin, DEV_ACCESS_BROKER_ORIGIN])
    : new Set([canonicalOrigin.origin]);
  if (broker.protocol !== "https:" || !allowedBrokerOrigins.has(broker.origin) || broker.pathname !== "/internal/gcp-wif/assertion" || broker.username || broker.password || broker.search || broker.hash) {
    throw new Error("invalid GCP WIF Access broker URL");
  }
  const issuer = new URL(accessIssuer);
  if (issuer.protocol !== "https:" || issuer.pathname !== "/" || issuer.username || issuer.password || issuer.search || issuer.hash || !issuer.hostname.endsWith(".cloudflareaccess.com")) {
    throw new Error("invalid Cloudflare Access issuer");
  }
  if (!ACCESS_CLIENT_ID.test(accessClientId) || accessClientSecret.length < 32 || accessClientSecret.length > 512) {
    throw new Error("invalid Cloudflare Access service credential");
  }
  if (!/^[a-f0-9]{64}$/.test(accessAudience)) throw new Error("invalid Cloudflare Access audience");
  if (!/^\/\/iam\.googleapis\.com\/projects\/[0-9]+\/locations\/global\/workloadIdentityPools\/[a-z0-9-]+\/providers\/[a-z0-9-]+$/.test(providerAudience)) {
    throw new Error("invalid GCP WIF provider audience");
  }
  if (!SERVICE_ACCOUNT.test(serviceAccount)) throw new Error("invalid GCP WIF service account");
  return { brokerUrl: broker.toString(), accessClientId, accessClientSecret, accessIssuer: issuer.toString().replace(/\/$/, ""), accessAudience, providerAudience, serviceAccount };
}

async function fetchAccessAssertion(config: WifConfig): Promise<string> {
  const response = await fetch(config.brokerUrl, {
    method: "POST",
    headers: {
      "cf-access-client-id": config.accessClientId,
      "cf-access-client-secret": config.accessClientSecret,
      accept: "application/json",
    },
  });
  const body = await boundedJson(response) as { subjectToken?: unknown };
  if (!response.ok || typeof body.subjectToken !== "string") throw new Error(`Access assertion broker failed with HTTP ${response.status}`);
  return body.subjectToken;
}

async function exchangeFederatedToken(assertion: string, audience: string): Promise<string> {
  const response = await fetch(STS_URL, {
    method: "POST",
    headers: { "content-type": "application/json", accept: "application/json" },
    body: JSON.stringify({
      grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
      audience,
      scope: "https://www.googleapis.com/auth/cloud-platform",
      requestedTokenType: "urn:ietf:params:oauth:token-type:access_token",
      subjectToken: assertion,
      subjectTokenType: "urn:ietf:params:oauth:token-type:id_token",
    }),
  });
  const body = await boundedJson(response) as { access_token?: unknown; expires_in?: unknown; token_type?: unknown };
  if (!response.ok || typeof body.access_token !== "string" || body.token_type !== "Bearer" || !Number.isFinite(body.expires_in) || Number(body.expires_in) <= 0) {
    throw new Error(`Google STS exchange failed with HTTP ${response.status}`);
  }
  return body.access_token;
}

async function impersonateServiceAccount(federatedToken: string, serviceAccount: string): Promise<string> {
  const response = await fetch(`${IAM_CREDENTIALS_ROOT}/${encodeURIComponent(serviceAccount)}:generateAccessToken`, {
    method: "POST",
    headers: { authorization: `Bearer ${federatedToken}`, "content-type": "application/json", accept: "application/json" },
    body: JSON.stringify({ scope: ["https://www.googleapis.com/auth/cloud-platform"], lifetime: "900s" }),
  });
  const body = await boundedJson(response) as { accessToken?: unknown; expireTime?: unknown };
  if (!response.ok || typeof body.accessToken !== "string" || typeof body.expireTime !== "string" || !Number.isFinite(Date.parse(body.expireTime))) {
    throw new Error(`service-account impersonation failed with HTTP ${response.status}`);
  }
  return body.accessToken;
}

function validateAccessAssertion(assertion: string, config: WifConfig, nowSeconds: number): void {
  if (assertion.length < 64 || assertion.length > 12_000) throw new Error("invalid Access assertion length");
  const parts = assertion.split(".");
  if (parts.length !== 3 || parts.some((part) => !/^[A-Za-z0-9_-]+$/.test(part))) throw new Error("invalid Access assertion format");
  const header = decodeJwtPart(parts[0]!) as { alg?: unknown; typ?: unknown };
  const claims = decodeJwtPart(parts[1]!) as AccessClaims;
  if (header.alg !== "RS256" || header.typ !== "JWT") throw new Error("unexpected Access assertion algorithm");
  if (claims.iss !== config.accessIssuer || claims.common_name !== config.accessClientId || claims.sub !== "" || claims.type !== "app") {
    throw new Error("unexpected Access assertion identity");
  }
  const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  if (!audiences.includes(config.accessAudience)) throw new Error("unexpected Access assertion audience");
  if (!Number.isSafeInteger(claims.exp) || !Number.isSafeInteger(claims.iat)) throw new Error("invalid Access assertion time claims");
  if ((claims.exp as number) <= nowSeconds || (claims.exp as number) > nowSeconds + 3_600 || (claims.iat as number) > nowSeconds + 60 || (claims.iat as number) < nowSeconds - 600) {
    throw new Error("Access assertion is expired or outside its allowed lifetime");
  }
}

function decodeJwtPart(value: string): unknown {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  try {
    return JSON.parse(atob(base64)) as unknown;
  } catch {
    throw new Error("invalid Access assertion JSON");
  }
}

async function boundedJson(response: Response): Promise<unknown> {
  const declared = Number(response.headers.get("content-length") ?? "0");
  if (Number.isFinite(declared) && declared > MAX_TOKEN_RESPONSE_BYTES) throw new Error("credential response is too large");
  const bytes = await readBoundedBody(response.body, MAX_TOKEN_RESPONSE_BYTES);
  if (bytes === null) throw new Error("credential response is too large");
  try {
    return JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes)) as unknown;
  } catch {
    throw new Error(`credential response HTTP ${response.status} is not JSON`);
  }
}

function noStore(): HeadersInit {
  return { "cache-control": "private, no-store", "content-type": "application/json" };
}
