// Generates golden vectors from the original TypeScript FinOps connectors so the Kotlin
// port (src/main/kotlin/code/yousef/aws/jobs) can be checked byte for byte.
// From this directory (Node 22.18+ with built-in type stripping):
//   node --no-warnings --import ./register.mjs golden.mjs \
//     ../../cloudflare/portfolio-edge/src ../../src/test/resources/aws/finops-connectors-golden.json
import { writeFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

const [srcDir, outFile] = process.argv.slice(2);
const mod = (name) => import(pathToFileURL(`${srcDir}/${name}.ts`).href);
const { canonicalJson } = await mod("contracts");
const { relayGcpBilling, billingExportCoverage } = await mod("gcp-billing");
const { relayCloudflareBilling, decimalMicros } = await mod("cloudflare-billing");
const { relayStripeBilling } = await mod("stripe-billing");

const FIXED = Date.parse("2026-10-21T05:11:00.000Z");
const RealDate = Date;
class FixedDate extends RealDate {
  constructor(...args) { super(...(args.length === 0 ? [FIXED] : args)); }
  static now() { return FIXED; }
}
globalThis.Date = FixedDate;

const canonicalInputs = [
  { b: 1, a: [true, false, null, "x"], c: { z: 0.1, y: -0.0, x: 1e21, w: 1e-7, v: 123456789012, u: 0.000001 } },
  { x_ZoneName: "a", ZoneName: "b", SkuPriceId: "c", ServiceName: "d", BilledCost: 0.0123, BillingCurrency: "USD" },
  { source: 1, sourceHash: 2, sourceRecordId: 3, Source: 4, "a-b": 5, a_b: 6, ab: 7, A1: 8, a1: 9, "": 10 },
  { text: "quote\" back\\ nl\n tab\t ctl\u0001 bell\u0007 unié  emoji😀 lone\ud800" },
  [1.5, 2.25, -3, 100, 1e300, 5e-324, 0.1 + 0.2],
];

const gcpRows = [
  ["1".repeat(64), "AAAA-BBBB", "202610", "seen-prod-123", "Compute Engine", "N2 core", "2026-10-10T01:00:00.000Z", "2026-10-19T03:00:00.000Z", "USD", "1234567", "1234567", "cost", "regular"],
  ["2".repeat(64), "AAAA-BBBB", "202610", "portfolio-476219", "Cloud Run", "CPU", "2026-10-11T00:00:00.000Z", "2026-10-19T03:00:00.000Z", "EUR", "-50000", "-54000", "credit", "credit"],
  ["ABCDEF".padEnd(64, "0"), "AAAA-BBBB", "2026-10", "samurai-ai", "Vertex AI", "Tokens\tin", "2026-10-12T00:00:00.000Z", "2026-10-20T03:00:00.000Z", "USD", "99", "99", "cost", "TAX"],
];
const gcpFields = ["source_record_id", "billing_account_id", "invoice_month", "project_id", "service", "sku", "incurred_at", "export_time", "currency", "signed_original_micros", "signed_usd_micros", "line_kind", "cost_type"];
const gcpPages = [
  { jobComplete: true, jobReference: { projectId: "billing-proj", jobId: "job-1", location: "EU" }, schema: { fields: gcpFields.map((name) => ({ name })) }, rows: gcpRows.slice(0, 2).map((r) => ({ f: r.map((v) => ({ v })) })), pageToken: "page-2", totalBytesProcessed: "1024" },
  { jobComplete: true, jobReference: { projectId: "billing-proj", jobId: "job-1", location: "EU" }, schema: { fields: gcpFields.map((name) => ({ name })) }, rows: gcpRows.slice(2).map((r) => ({ f: r.map((v) => ({ v })) })), totalBytesProcessed: "1024" },
];

const cloudflare = {
  accountId: "0123456789abcdef0123456789abcdef",
  accountSubscriptions: [
    { id: "acct-sub-1", currency: "USD", price: 20, current_period_start: "2026-10-01T00:00:00Z", frequency: "monthly", state: "Paid", rate_plan: { id: "workers_paid", public_name: "Workers Paid" } },
    { id: "acct-sub-2", currency: "EUR", price: 5, current_period_start: "2026-10-01T00:00:00Z" },
    { id: "acct-sub-3", current_period_start: "2026-10-01T00:00:00Z", rate_plan: { id: "free" } },
  ],
  zones: [{ id: "fedcba9876543210fedcba9876543210", name: "yousef.codes" }],
  zoneSubscriptions: [
    { id: "zone-sub-1", price: "5.5", currency: "USD", current_period_start: "2026-10-01T00:00:00.000Z", frequency: "monthly", rate_plan: { id: "pro", scope: "zone" } },
  ],
  usage: [
    { BilledCost: 0.0123, BillingCurrency: "USD", ChargePeriodStart: "2026-10-02T00:00:00Z", x_ZoneName: "felidai.com", ZoneName: "ignored", ServiceName: "Workers AI", x_ZoneId: "zone-x", SkuPriceId: "sku-1" },
    { EffectiveCost: "-1.0000005", ChargePeriodStart: "2026-10-03T00:00:00Z", x_ProductFamilyName: "Seen Registry", PricingUnit: "requests" },
    { ListCost: 0, ChargePeriodStart: "2026-10-03T00:00:00Z" },
  ],
};

const stripe = {
  key: "rk_test_" + "a".repeat(24),
  pages: [
    { object: "list", has_more: true, data: [
      { id: "txn_1", amount: 2500, fee: 103, currency: "usd", created: 1792000000, type: "charge", reporting_category: "charge", source: { id: "ch_1", metadata: { samurai_user_id: " user-42 " } } },
      { id: "txn_2", amount: -1000, fee: 0, currency: "usd", created: 1792100000, type: "refund", reporting_category: "refund", source: "re_1" },
    ] },
    { object: "list", has_more: false, data: [
      { id: "txn_3", amount: -50000, fee: 0, currency: "usd", created: 1792200000, type: "payout", reporting_category: "payout", source: { id: "po_1", metadata: { samurai_user_id: "user-42" } } },
      { id: "txn_4", amount: 900, fee: 30, currency: "eur", created: 1792300000, type: "charge", reporting_category: "charge", source: null },
      { id: "txn_5", amount: 700, fee: -20, currency: "usd", created: 1792400000, type: "adjustment" },
    ] },
  ],
};

function queueCapture() {
  const sent = [];
  return {
    sent,
    queue: {
      async send(body) { sent.push(body); },
      async sendBatch(messages) { for (const message of messages) sent.push(message.body); },
    },
  };
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json" } });
}

function b64url(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

const wifEnv = {
  PORTFOLIO_CANONICAL_ORIGIN: "https://dev.yousef.codes",
  GCP_WIF_ACCESS_BROKER_URL: "https://dev.yousef.codes/internal/gcp-wif/assertion",
  GCP_WIF_ACCESS_CLIENT_ID: "0123456789abcdef0123456789abcdef.access",
  GCP_WIF_ACCESS_CLIENT_SECRET: "s".repeat(40),
  GCP_WIF_ACCESS_ISSUER: "https://team.cloudflareaccess.com",
  GCP_WIF_ACCESS_AUDIENCE: "a".repeat(64),
  GCP_WIF_PROVIDER_AUDIENCE: "//iam.googleapis.com/projects/123/locations/global/workloadIdentityPools/pool/providers/provider",
  GCP_WIF_SERVICE_ACCOUNT: "finops-reader@billing-proj.iam.gserviceaccount.com",
};

async function runGcp() {
  const { sent, queue } = queueCapture();
  let bigQueryCalls = 0;
  const nowSeconds = Math.floor(FIXED / 1000);
  const jwt = `${b64url({ alg: "RS256", typ: "JWT" })}.${b64url({ iss: wifEnv.GCP_WIF_ACCESS_ISSUER, aud: [wifEnv.GCP_WIF_ACCESS_AUDIENCE], common_name: wifEnv.GCP_WIF_ACCESS_CLIENT_ID, sub: "", type: "app", exp: nowSeconds + 600, iat: nowSeconds - 10 })}.signature`;
  globalThis.fetch = async (input) => {
    const url = String(input instanceof Request ? input.url : input);
    if (url.startsWith(wifEnv.GCP_WIF_ACCESS_BROKER_URL)) return json({ subjectToken: jwt });
    if (url.startsWith("https://sts.googleapis.com/")) return json({ access_token: "federated", expires_in: 3600, token_type: "Bearer" });
    if (url.startsWith("https://iamcredentials.googleapis.com/")) return json({ accessToken: "gcp-token", expireTime: "2026-10-21T06:00:00Z" });
    if (url.startsWith("https://bigquery.googleapis.com/")) return json(gcpPages[bigQueryCalls++]);
    throw new Error(`unexpected fetch ${url}`);
  };
  await relayGcpBilling({
    PORTFOLIO_ENV: "dev",
    FINOPS_COVERAGE_START_DATE: "2026-09-01",
    GCP_FINOPS_BILLING_PROJECT_ID: "billing-proj",
    GCP_FINOPS_BILLING_DATASET: "billing_export",
    GCP_FINOPS_BILLING_TABLE: "gcp_billing_export_v1_X",
    GCP_FINOPS_BILLING_LOCATION: "EU",
    GCP_FINOPS_MAX_BYTES_BILLED: "100000000",
    ...wifEnv,
    FINOPS_INGEST_QUEUE: queue,
  });
  return sent;
}

async function runCloudflare() {
  const { sent, queue } = queueCapture();
  globalThis.fetch = async (input) => {
    const url = new URL(String(input instanceof Request ? input.url : input));
    const path = url.pathname;
    if (path.endsWith(`/accounts/${cloudflare.accountId}/subscriptions`)) return json({ success: true, result: cloudflare.accountSubscriptions });
    if (path.endsWith("/zones")) return json({ success: true, result: cloudflare.zones });
    if (path.endsWith(`/zones/${cloudflare.zones[0].id}/subscriptions`)) return json({ success: true, result: cloudflare.zoneSubscriptions });
    if (path.endsWith("/billable/usage")) return json({ success: true, result: cloudflare.usage });
    throw new Error(`unexpected fetch ${url}`);
  };
  await relayCloudflareBilling({
    PORTFOLIO_ENV: "dev",
    FINOPS_COVERAGE_START_DATE: "2026-09-01",
    CLOUDFLARE_BILLING_API_TOKEN: "cf-token",
    CLOUDFLARE_ACCOUNT_ID: cloudflare.accountId,
    FINOPS_INGEST_QUEUE: queue,
  });
  return sent;
}

async function runStripe() {
  const { sent, queue } = queueCapture();
  let page = 0;
  globalThis.fetch = async () => json(stripe.pages[page++]);
  await relayStripeBilling({
    PORTFOLIO_ENV: "dev",
    FINOPS_COVERAGE_START_DATE: "2026-09-01",
    STRIPE_FINOPS_READ_KEY: stripe.key,
    FINOPS_INGEST_QUEUE: queue,
  });
  return sent;
}

const out = {
  now: new RealDate(FIXED).toISOString(),
  canonical: canonicalInputs.map((input) => ({ input: JSON.parse(JSON.stringify(input)), output: canonicalJson(input) })),
  decimalMicros: ["5.5", "0.0000005", "-1.0000005", "1e-7", "12.345678901", "3"].map((value) => [value, decimalMicros(value)]),
  gcp: { fields: gcpFields, rows: gcpRows, pages: gcpPages, sent: await runGcp() },
  cloudflare: { ...cloudflare, sent: await runCloudflare() },
  stripe: { ...stripe, sent: await runStripe() },
  exportCoverage: billingExportCoverage(gcpRows.map((row) => Object.fromEntries(gcpFields.map((f, i) => [f, row[i]]))), "2026-10-22"),
};
writeFileSync(outFile, JSON.stringify(out, null, 2));
console.log("wrote", outFile);
