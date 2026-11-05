# Yousef — Portfolio · Blog · Docs

A single Ktor + Summon stack powering three surfaces:

- Portfolio: featured work, services, contact
- Blog: long-form notes and release write-ups (EN/AR)
- Docs: multi-page product manuals with sidebar, search, and deep links

Live:

- https://www.yousef.codes — portfolio, services, blog, admin
- https://summon.yousef.codes — docs (Summon, more coming)

Tech:

- Kotlin (JVM, JS), Ktor, Summon UI, SSR + hydration
- Type-safe modifiers, portals, async validation, and responsive components

## Running Locally

```bash
# Development mode (uses local file storage)
./gradlew run

# Build and run with Docker (with persistent storage)
docker-compose up -d

# Or manual Docker with named volume for persistence
docker build -t portfolio .
docker run -d -p 8080:8080 -v portfolio-data:/app/storage portfolio
```

## Deployment Notes

**Data Persistence**: In local file-storage mode, admin credentials and content are stored in `/app/storage/` inside the container. To persist data across container restarts:

- **Docker Compose**: Uses a named volume `portfolio-data` by default
- **Docker**: Use `-v portfolio-data:/app/storage` or bind mount a host directory
- **Kubernetes**: Create a PersistentVolumeClaim mounted at `/app/storage`

The current Cloud Run deployment continues to use Firestore for content and GCS
for photography uploads. The Cloudflare Container migration can move those
assets to R2 without changing the default path: `source` retains current
GCS/local behavior, `dual` writes immutable content-addressed objects to both
R2 and the durable GCS source while reading GCS, and `target` reads and writes
R2. Target-mode reverse mirroring is an explicit rollback-window option and is
rejected unless a durable GCS bucket is configured.

The isolated Seen registry has a public [signing operations runbook](registry-service/docs/signing-operations.md) covering offline custody, ceremonies, renewal, rotation, compromise recovery, IAM policy gates, and development drills.

Environment variables:
- `PORTFOLIO_CONTENT_PATH` - Path to content.json (default: `/app/storage/content.json`)
- `ADMIN_CREDENTIALS_PATH` - Path to admin-credentials.json (default: `/app/storage/admin-credentials.json`)
- `USE_LOCAL_STORE` - Set to `true` to use file storage instead of Firestore
- `FIRESTORE_SEED_ON_START` - Explicitly set to `true` only for a one-instance
  bootstrap. It defaults to `false`; scalable runtimes must migrate or seed data
  as a separate operation instead of mutating Firestore during every startup.
- `FIRESTORE_DATABASE_ID` - Named migration target database (for example `portfolio-me-prod`); defaults to `(default)`
- `FIRESTORE_WRITE_MODE` - `source` (default), `dual`, or `target`. `dual` and `target` require a named database and an exact migration proof produced after full source/target parity verification
- `FIRESTORE_MIGRATION_PROOF_ID` - Required in `dual` and `target`; identifies matching proof documents stored in both databases after the allowlisted business data hashes exactly and replication-health checks pass

Migration proofs are create-only and must use a new explicit ID for every
verification. Dual mode accepts proofs for at most seven days; a target
authority cutover requires a proof no older than 24 hours. Generate one only
after an exact read-only parity scan with
`firestoreDevBackfill --args='--write-proof <unique-proof-id>'` and the exact
dev execution confirmation variable. Operators whose local Application Default
Credentials cannot refresh may pass a short-lived `gcloud auth
print-access-token` value through `PORTFOLIO_DEV_FIRESTORE_ACCESS_TOKEN`; the
CLI keeps it in memory, rejects malformed values, and does not persist it.
Proof hashes cover business collections, not replication bookkeeping:
authority outbox records, receipt lifecycle/timestamps, and aggregate-state
timestamps are intentionally asymmetric. Monitor pending outbox age separately.
The dev-only `--reconcile-pending` mode settles an old source record only after
the exact source/target business document, semantic revision state, and target
receipt prove that it already landed; it never writes business data or deletes
documents.
- `FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64` - Optional strict Base64 service-account JSON used in Cloudflare Containers where ADC is unavailable; decoded only in memory and required to match `GOOGLE_CLOUD_PROJECT`
- `PHOTOGRAPHY_UPLOAD_BUCKET` - Optional GCS bucket for durable photo uploads in production
- `PHOTOGRAPHY_UPLOAD_PREFIX` - GCS object prefix for photo uploads (default: `photography`)
- `PHOTOGRAPHY_UPLOAD_DIR` - Local photo upload directory when no bucket is configured
- `PHOTOGRAPHY_MAX_UPLOAD_BYTES` - Maximum single photo upload size in bytes
- `PHOTOGRAPHY_WRITE_MODE` - `source` (default), `dual`, or `target`; dual mode requires a GCS source bucket
- `PHOTOGRAPHY_R2_BASE_URL` - Authenticated Portfolio edge media endpoint; dual and target modes require it or `PORTFOLIO_S3_MEDIA_BUCKET` (never both)
- `PHOTOGRAPHY_R2_REVERSE_MIRROR` - `true` only during a target-mode rollback window with a configured GCS bucket
- `EDGE_ORIGIN_TOKEN` - Secret bearer token shared only by the Portfolio Container and edge Worker; required for R2 media access. Nothing in AWS mode uses it

### AWS mode (opt-in)

Firestore, local files and the Cloudflare edge remain the default. AWS mode is
selected entirely by environment and uses the AWS SDK default credential chain
(the ECS task role); the application reads no static AWS keys.

- `PORTFOLIO_DATA_BACKEND` - `firestore` (default), `local` (same as
  `USE_LOCAL_STORE=true`) or `dynamodb`. `dynamodb` disables Firestore, the
  `/internal/edge/jobs/*` routes and the `/internal/finops/*` routes
- `PORTFOLIO_DYNAMODB_DOCUMENTS_TABLE` - Single-table document store: partition
  key `pk` (collection), sort key `sk` (document id), and two sparse GSIs with
  projection `ALL`: `order-index` (`opk`, `osk`) for ordered/range pages and
  `lookup-index` (`lpk`, `sk`) for equality lookups. Document values round-trip
  exactly (integers vs doubles, timestamps, blobs, nested maps, lists, nulls)
- `PORTFOLIO_SESSION_BACKEND` - `memory` (default), `edge` (same as
  `PORTFOLIO_EDGE_SESSIONS_ENABLED=true`) or `dynamodb`
- `PORTFOLIO_DYNAMODB_SESSIONS_TABLE` - Sessions and job idempotency leases:
  partition key `pk`, TTL attribute `expires_at` (epoch seconds)
- `PORTFOLIO_SESSION_COOKIE_SECURE` - Defaults to `true` with DynamoDB sessions
- `PORTFOLIO_S3_MEDIA_BUCKET` / `PORTFOLIO_S3_MEDIA_PREFIX` - Content-addressed
  photography media (`sha256/<digest>/<file>`, no prefix by default). Public URLs
  stay `/cdn/media/<key>`; CloudFront maps them to object key `<key>`
- `PORTFOLIO_S3_DOCS_BUCKET` - Public docs objects served at `/cdn/docs/<key>`
- `PORTFOLIO_S3_RECEIPTS_BUCKET` / `PORTFOLIO_S3_RECEIPTS_PREFIX` - Private,
  immutable FinOps receipts (`sha256/<digest>/uploads/<uploadId>/<file>`)
- `PORTFOLIO_CDN_PROXY_ENABLED` - `true` only where `/cdn/media/*` and
  `/cdn/docs/*` must be served by the application (for example an
  authenticated dev environment); streams from the S3 buckets above
- `PORTFOLIO_SQS_JOBS_QUEUE_URL` - Long-polled job queue replacing the Worker
  cron triggers and Queues. Scheduled messages are
  `{"job":"finops.gcp-billing|finops.cloudflare-billing|finops.stripe|finops.recurring-expenses|finops.reconcile","scheduledAt":"<ISO-8601>"}`;
  asynchronous work is `{"job":"async.finops.ingest|async.finops.import-run|async.finops.rollups.v2.backfill|async.finops.rollups.v2.reconcile","id":"<unique id>","payload":{...}}`.
  Jobs are idempotent and leased per `job:scheduledAt` / `job:id`; failures are
  retried with backoff and dead-lettered by the queue's redrive policy
- `PORTFOLIO_SQS_JOBS_VISIBILITY_TIMEOUT_SECONDS` - Default `300`; extended
  while a job runs
- `PORTFOLIO_ENV` (`dev`/`prod`) and `FINOPS_COVERAGE_START_DATE` - Required by
  the scheduled FinOps jobs; billing ingestion and reconciliation stay dev-only
- `GCP_FINOPS_BILLING_PROJECT_ID`, `GCP_FINOPS_BILLING_DATASET`,
  `GCP_FINOPS_BILLING_TABLE`, `GCP_FINOPS_BILLING_LOCATION`,
  `GCP_FINOPS_MAX_BYTES_BILLED` - BigQuery billing export
- `GCP_FINOPS_WIF_CREDENTIAL_CONFIG_JSON` - Google external-account (AWS source)
  credential configuration; federates from the task role, no Google key
- `CLOUDFLARE_BILLING_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` - Cloudflare billing
- `STRIPE_FINOPS_READ_KEY` - Restricted Stripe key for balance transactions
- `PORTFOLIO_AWS_ENDPOINT_URL` - Local emulator endpoint override only

`/health` (liveness) and `/health/ready` (readiness; probes DynamoDB in AWS
mode) are served by the application before host routing and sessions.

`firestoreToDynamoBackfill` copies the business-data collections from Firestore
(read-only) into the DynamoDB documents table and compares exact per-collection
and overall parity hashes. It is a dry run unless invoked with
`-PbackfillArgs=--execute` and `PORTFOLIO_DYNAMO_BACKFILL_CONFIRM` set to
`<project>:<database>-><table>`; it never deletes and refuses DynamoDB-only
documents. Configure it with `PORTFOLIO_BACKFILL_FIRESTORE_PROJECT`,
`PORTFOLIO_BACKFILL_FIRESTORE_DATABASE` and `PORTFOLIO_BACKFILL_DYNAMODB_TABLE`.

DynamoDB integration tests use DynamoDB Local in Docker and are skipped when
Docker is unavailable (set `PORTFOLIO_REQUIRE_DYNAMODB_LOCAL=true` to fail
instead). The FinOps connector tests compare against vectors generated from the
original TypeScript connectors by `scripts/finops-connector-golden`.

The checked-in `firestore.indexes.json` contains the ordered migration-outbox
index and the password-reset invalidation query index. Deploy it independently
to the source and named Dammam databases before enabling `dual`; index deployment
is a GCP mutation and is not part of a Cloudflare Worker deployment.

Contributing:

- Open issues with a clear scope; include URLs/sections and screenshots if visual
- Localization in EN/AR; match copy tone and direction

© 2025 Yousef. All rights reserved.
