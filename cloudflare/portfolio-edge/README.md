# Portfolio Cloudflare edge runtime

This package is the Wrangler-owned release half of the Cloudflare platform
contract in `infra/cloudflare/modules/platform/contract.json`. It does not own
R2 buckets, Queue identities, DNS, zones, routes, or custom domains. OpenTofu
owns only the durable bucket and queue identities; a future reviewed Wrangler
release owns Worker code, bindings, Container classes, Durable Object
migrations, the Workflow, and the Queue consumer.

The template defaults to deploy-disabled and contains no route, custom domain,
account ID, zone ID, or secret. The config generator can enable the account's
`workers.dev` route only for `dev`; production validation rejects it and Preview
URLs remain disabled in both environments. Config generation and validation make
no Cloudflare API calls. Custom domains and zone routes remain a separate phase.

## Runtime shape

- Existing files under `src/main/resources/static` are served by Workers Static
  Assets before the Worker runs. Missing ordinary GET/HEAD assets fall through
  to the Kotlin Summon/Aether SSR Container.
- Four deterministic Portfolio SSR Container IDs bound the maximum shard count.
  Container placement is constrained to `ME`, with `WEUR` fallback, and idle
  instances sleep after ten minutes. The forwarding RPC checks persisted
  Container health: healthy instances skip the SDK's redundant port-ready
  probe, while non-healthy instances retain a 60-second JVM cold-start budget.
  Dynamic responses expose only aggregate `Server-Timing` durations for the
  Container-state lookup, optional cold start, and origin request. They contain
  no route, identity, datastore, or payload values and support dev latency
  acceptance without enabling payload logs.
- `/playground`, `/playground/run`, and `/api/seen/run` use a separate two-ID
  Container class. It runs with local application storage, no internet access,
  and a one-minute sleep policy so Seen execution is isolated and scales to
  zero independently of SSR.
- `PortfolioSessionDO` owns optimistic, revisioned session state and Queue
  idempotency leases. The internal session API requires the Container-only
  origin token, and session/idempotency JSON bodies are bounded while streamed
  before parsing. No Worker isolate-global map is authoritative.
- Media and documentation reads use immutable, content-addressed R2 paths:
  `/cdn/{media|docs}/sha256/{digest}/{filename}`. Bucket listing and public
  mutation are not exposed. The Container-only
  `/internal/media/v1/asset` API authenticates every PUT/GET/DELETE, accepts
  only the supported photography content types, recomputes SHA-256 before a
  conditional create, and verifies stored metadata and bytes on internal
  reads. Upload bodies are consumed through a bounded stream, so a missing or
  misleading `Content-Length` cannot bypass the configured asset-size limit.
  A repeated identical create is idempotent; an attempted overwrite with
  conflicting metadata fails.
- FinOps invoice and receipt bodies use the separate private
  `FINOPS_RECEIPTS` binding. `/internal/finops/v1/receipt` requires the
  Container-only origin token, allowlisted content types, immutable SHA-256
  keys, and full stored-byte verification. It exposes only conditional create,
  read, and verification methods; receipt deletion is not a runtime capability.
  New uploads use
  `sha256/{digest}/uploads/{uploadId}/{filename}` so two attachment attempts
  never share lifecycle ownership; legacy `sha256/{digest}/{filename}` reads
  remain supported. No public route exposes this bucket.
  The owner dashboard can upload and download attachments through authenticated
  application routes; Firestore stores only the verified reference and audit
  metadata. The immutable ingest audit links new attachments by upload ID and
  digest. An authenticated, bounded expiry dry-run can inventory unique uploads
  older than seven days and classify each against the exact Firestore attachment
  projection. It never deletes or mutates R2, and it ignores legacy/shared keys.
  Automatic expiry remains disabled until attach and cleanup share an atomic
  coordination boundary; a point-in-time dry-run result is not deletion proof.
- Samurai user/provider/model transaction drill-down has a bounded Firestore
  projection ordered by `incurredAt`. New and replayed allocations create
  idempotent per-dimension projection records, and cursors are bound to the
  complete query filter. `FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY` defaults
  to `false`, preserving the existing read path until the composite index is
  ready and a complete coverage-window replay has reconciled projection counts and
  canonical hashes. The final reconciliation also fails on missing,
  unexpected, mismatched, malformed, or orphaned records. Enable the flag only
  when the authenticated internal proof reports `ready=true`; never use an
  empty projection result as proof of zero covered spend. Once enabled, the
  scheduled Worker skips the one-time coverage-window repair instead of
  rescanning the ledger every day.
- FinOps coverage begins at `2026-08-24T00:00:00Z`. The edge rejects earlier
  imports and entries, connector and dashboard windows are clamped to that
  boundary, and pre-coverage ranges render an explicit coverage gap rather than
  zero spend. Future cost messages use the dedicated `FINOPS_INGEST_QUEUE` and
  `FINOPS_INGEST_DLQ`; neither successful nor failed future cost traffic mixes
  with the legacy shared Portfolio/Samurai queues.
- The dev scheduler imports Cloudflare billing at 05:11 UTC, reads Stripe's
  restricted balance ledger at 05:21 UTC when `STRIPE_FINOPS_READ_KEY` is
  configured, materializes fixed recurring expenses at 05:31 UTC, and
  reconciles allocation residuals at 05:51 UTC. Stripe and recurring imports
  use deterministic source IDs and overlapping 35-day recovery windows, so
  retries cannot duplicate financial amounts. Stripe source expansion retains
  only an opaque Samurai user ID; payment details and identity fields are never
  queued or logged.
- The owner-only Samurai user view resolves display names and email addresses
  at read time through the exact `SAMURAI_FINOPS_IDENTITY_URL`. The endpoint
  uses the separate `FINOPS_IDENTITY_READ_TOKEN`, batches at most 100 opaque
  IDs, never stores the returned projection in the FinOps ledger, and degrades
  to opaque IDs if Samurai is unavailable. Configure both values together.
- Queue messages use the common mutation envelope, accept only bounded streamed
  JSON at the internal enqueue boundary, validate the canonical payload hash,
  and claim a Durable Object idempotency lease. Invalid messages are converted
  to metadata-only DLQ receipts; transient failures are retried and then
  handled by the configured Cloudflare DLQ.
- Owner FinOps mutations are byte-bounded at the edge before Container startup
  or Aether buffering. JSON/form/CSV/receipt routes retain separate limits, and
  the Worker replaces any declared length with the observed buffered length
  before forwarding. Missing, invalid, or misleading `Content-Length` values
  cannot bypass the streamed-byte boundary.
- `PortfolioMigrationWorkflow` remains fail-closed while
  `PORTFOLIO_MIGRATION_EXECUTION_ENABLED=false`. Enabling it is a migration
  cutover decision, not an application release default.
- Photography asset staging has its own authenticated internal job and remains
  fail-closed while `PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED=false`. When
  separately enabled, it copies only an approved, hash-and-size-bound inventory
  to content-addressed GCS safety keys and R2, verifies both copies, and returns
  immutable receipts. It does not update Firestore keys or delete legacy data.

The Worker injects `PORTFOLIO_EDGE_SESSIONS_ENABLED=true`, the exact
`PORTFOLIO_EDGE_SESSION_BASE_URL`, and `EDGE_ORIGIN_TOKEN` into both Container
classes. The Kotlin application therefore uses the Durable Object-backed Aether
session-store adapter in Cloudflare while local runs remain explicitly
in-memory by default. Production acceptance must still exercise concurrent
admin/building session mutation and instance replacement; it must never disable
the edge-session flag or fall back to JVM-local correctness state.

## Local verification

Use the pinned Node and pnpm versions, then run:

```sh
corepack pnpm install --frozen-lockfile
corepack pnpm verify
```

Generate an ignored, environment-specific Wrangler file only after the
matching OpenTofu output has been reviewed:

```sh
node scripts/generate-config.mjs \
  --environment dev \
  --canonical-origin https://dev.yousef.codes \
  --container-pool-version v1 \
  --firestore-project portfolio-476219 \
  --firestore-database portfolio-me-dev \
  --firestore-write-mode source \
  --photography-upload-bucket portfolio-476219-portfolio-uploads
  --workers-dev
```

The generator creates a new file with exclusive-create semantics and refuses
to overwrite an existing config. `wrangler.generated.*.json`, `.dev.vars*`,
Wrangler state, and package outputs are ignored. Provide
`FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64`, `EDGE_ORIGIN_TOKEN`, and
`FINOPS_INTERNAL_INGEST_TOKEN` only as Cloudflare
secret bindings during the separately authorized release phase.

`FINOPS_INTERNAL_INGEST_TOKEN` authenticates only the queue consumer's
privacy-minimized Samurai cost events to the Portfolio Container. It must be
distinct from the general edge-origin token. The shared environment-specific
Portfolio async Queue accepts the `finops.samurai.ingest` mutation envelope,
uses the existing Durable Object idempotency receipt boundary, and dead-letters
malformed or conflicting messages without logging their payloads.

### Keyless GCP billing export ingestion

The `1 5 * * *` daily trigger is inert unless the complete GCP FinOps and WIF
configuration is present. It never falls back to the Firestore service-account
key. The relay queries one 35-day overlap window from the standard Cloud
Billing export, requires both partition and usage-date predicates, disables the
query cache, caps `maximumBytesBilled` at no more than 1 GB, and publishes
append-only cost/correction entries through the dedicated future-only FinOps
Queue. The query window is clamped to `FINOPS_COVERAGE_START_DATE`, and a run
whose requested window ends before the cutoff exits without querying BigQuery.
Every
credential, BigQuery, provider, Container reconciliation, and mutation-receipt
response is consumed through an incremental byte cap before UTF-8/JSON parsing;
declared response lengths are an early rejection hint, not the safety boundary.

Generate a dev config with the non-secret half of the contract only after the
matching Access application, workload identity provider, and read-only service
account have been reviewed:

```sh
node scripts/generate-config.mjs \
  --environment dev \
  --canonical-origin https://dev.yousef.codes \
  --firestore-project portfolio-476219 \
  --firestore-database portfolio-me-dev \
  --photography-upload-bucket portfolio-476219-portfolio-uploads \
  --finops-sharded-rollup-writes-enabled \
  --workers-dev \
  --gcp-finops-billing-project felidai-dev \
  --gcp-finops-billing-dataset REPLACE_WITH_DATASET \
  --gcp-finops-billing-table REPLACE_WITH_STANDARD_EXPORT_TABLE \
  --gcp-finops-billing-location US \
  --gcp-finops-max-bytes-billed 50000000 \
  --gcp-wif-access-broker-url https://dev.yousef.codes/internal/gcp-wif/assertion \
  --gcp-wif-access-client-id REPLACE_WITH_32_HEX.access \
  --gcp-wif-access-issuer https://REPLACE_WITH_TEAM.cloudflareaccess.com \
  --gcp-wif-access-audience REPLACE_WITH_64_HEX_APP_AUD \
  --gcp-wif-provider-audience //iam.googleapis.com/projects/REPLACE_WITH_NUMBER/locations/global/workloadIdentityPools/cloudflare-dev/providers/access-dev \
  --gcp-wif-service-account finops-reader@felidai-dev.iam.gserviceaccount.com
```

Store `GCP_WIF_ACCESS_CLIENT_SECRET` only as a Worker secret. It and every
short-lived token must stay out of Wrangler vars, generated config, state
output, and logs. The dedicated path-scoped Access service credential
authenticates the broker self-call. The broker URL is runtime-pinned to the
exact canonical origin and
`/internal/gcp-wif/assertion` path so a bad configuration cannot exfiltrate
the Access credential, and the broker rejects requests delivered on any other
origin. The Worker enables Cloudflare's
`global_fetch_strictly_public` compatibility flag so this exact same-zone
broker request traverses the public Access boundary; without it, Cloudflare
rejects Worker-to-Worker public fetches on the same zone.

Cloudflare Access must protect only that broker route with a `Service Auth`
policy for the dedicated service token and a maximum one-hour application
session. Configure the Google OIDC workload identity provider for the exact
Cloudflare Access issuer and application audience, map
`google.subject=assertion.common_name`, and require the exact immutable service
token client ID in its attribute condition. Grant that principal only
`roles/iam.workloadIdentityUser` on the dedicated `finops-reader` service
account. Grant the service account `roles/bigquery.jobUser` on the query project
and dataset-scoped `roles/bigquery.dataViewer` on the billing-export dataset;
do not grant billing mutation or Firestore business-data roles.

The Worker exchanges the Access application JWT at Google STS and then calls
IAM Credentials for a 15-minute service-account access token. The Access JWT,
federated token, and Google token are bounded, never logged, and never written
to storage. Partial WIF/config groups fail deployment validation instead of
silently producing incomplete coverage.

`FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64` is the strict Base64 encoding of a
least-privilege Google service-account JSON document for `portfolio-476219`.
The Kotlin Container decodes it in memory, rejects malformed, oversized,
non-service-account, or cross-project credentials, and never writes the JSON
to disk. The legacy `GOOGLE_SERVICE_ACCOUNT_JSON_B64` name is rejected so a
misnamed secret cannot silently fall through to unavailable Container ADC.
Set `--firestore-write-mode dual` only after the external backfill utility
reports exact source/target parity and writes an identical migration proof to
both databases. Pass that proof with `--firestore-migration-proof-id`; use
`target` only at the reviewed authority cutover.

Photography migration is deliberately explicit:

- `--photography-write-mode source` is the default and keeps GCS authoritative.
- `dual` requires `--photography-upload-bucket`; new uploads receive
  `sha256/{digest}/{photoId}.{extension}` keys, are conditionally created in
  R2, and are then persisted to GCS. Reads remain GCS-authoritative.
- `target` reads only R2. Add `--photography-reverse-mirror` only for the
  reviewed rollback window; config validation requires a durable GCS bucket.

Existing mutable GCS keys must be copied to their verified SHA-256 R2 keys and
the corresponding Firestore `storageKey` fields reconciled before target mode.
This repository provides the runtime contract but deliberately does not run
that cloud backfill or mutate Firestore as part of config generation.

## Intentional limits

- Container instance counts are explicit; Cloudflare Containers do not supply
  Cloud Run-style autoscaling. Each color uses four SSR IDs and two playground
  IDs. The SSR application cap is eight so one four-shard replacement can
  coexist with one draining pool; bump `--container-pool-version` for a reviewed
  blue/green release instead of reusing live Durable Object IDs.
- The Seen playground image currently reuses the repository Dockerfile. It is
  compute- and network-isolated by a separate Container class. Portfolio SSR
  uses `Dockerfile.portfolio`, which deliberately omits LLVM, Clang, LLD,
  SDL/Vulkan, and the Seen compiler/runtime. Config validation pins these two
  image roles so the heavyweight toolchain cannot regress into ordinary SSR.
- The only enabled asynchronous operation is the migration reconciliation
  Workflow. Its Queue consumer requires a claim token, validates the durable
  Kotlin receipt, and acknowledges only a fully mirrored result. Worker and
  Container execution gates both default to false. Docs, media, and webhook
  producers remain absent until their durable origin handlers are implemented.
- R2 photography writes are wired, but target cutover remains blocked until the object
  backfill and Firestore storage-key reconciliation pass their count/hash gate.
  Target mode does not silently fall back to GCS or local storage.
