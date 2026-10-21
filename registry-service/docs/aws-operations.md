# Seen registry on AWS

This public note describes the AWS runtime contract for the registry service:
DynamoDB persistence, native S3 object storage, AWS KMS Ed25519 online
signers, and the ceremony changes needed to delegate the offline root and
targets roles to those keys. It contains no account IDs, key material, key
IDs, ARNs, operator identity, or ceremony inputs; placeholders such as
`<account>` stand for values recorded only in the private ticket.
[Signing operations](signing-operations.md) remains the authority for the
signing boundary, custody, and ceremony controls; this note only states what
changes on AWS.

The GCP (`REGISTRY_STORAGE_MODE=gcp`, Firestore, GCS or R2, Cloud KMS) and
memory modes are unchanged. AWS is opt-in per runtime by configuration.

## Selecting AWS

| Setting | Value | Notes |
| --- | --- | --- |
| `REGISTRY_STORAGE_MODE` | `dynamodb` | Repository is DynamoDB; rejects `GOOGLE_CLOUD_PROJECT`, `REGISTRY_FIRESTORE_DATABASE`, `GOOGLE_APPLICATION_CREDENTIALS`, and Firestore credential variables. |
| `AWS_REGION` | e.g. `eu-north-1` | Required. Fargate sets it automatically. |
| `REGISTRY_OBJECT_STORE_PROVIDER` | `s3` (default in DynamoDB mode) | `r2` remains possible; `gcs` is rejected. S3 rejects every `REGISTRY_R2_*` variable. |
| `REGISTRY_DYNAMODB_TABLE_PREFIX` | optional | Must equal `seen-registry-dev` or `seen-registry-prod` for the selected environment. |
| `REGISTRY_TUF_SIGNER_PROVIDER` | `aws-kms` | Signer processes only. |

All AWS credentials come from the default provider chain, which on ECS is the
task role. No runtime accepts static AWS or R2 keys in S3 mode; signer
processes additionally reject `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
`AWS_SESSION_TOKEN`, `AWS_PROFILE`, `AWS_SHARED_CREDENTIALS_FILE`, and
`AWS_CONFIG_FILE`.

## DynamoDB layout

One table per former Firestore collection, so IAM can grant each runtime only
the tables it touches. DynamoDB global secondary indexes are only eventually
consistent, while the Firestore queries they would replace are strongly
consistent; two auxiliary tables therefore hold the lookups, written in the
same `TransactWriteItems` call as the primary item. No GSI is used. Every
read is `ConsistentRead`.

| Table (`<prefix>-…`) | Partition key | Sort key | Holds |
| --- | --- | --- | --- |
| `packages-v1` | `pk` (S) package identity | — | package JSON, `etag` |
| `releases-v1` | `pk` (S) package identity | `sk` (S) version | release JSON, `revision` (N), `upload_id` |
| `release-uploads-v1` | `pk` (S) upload ID | — | `release_package`, `release_version` |
| `state-v1` | `pk` (S) `metadata` | — | `version` (N), publication lease |
| `idempotency-v1` | `pk` (S) scope | — | record JSON, `write_token`, `ttl` (N) |
| `review-artifacts-v1` | `pk` (S) artifact ID | — | artifact JSON and binding fields |
| `review-artifact-index-v1` | `pk` (S) `<package>@<version>` | `sk` (S) artifact ID | copy of each package/version-bound artifact |

- Billing: on-demand is sufficient. Enable point-in-time recovery and
  deletion protection on every table; use server-side encryption with an
  AWS managed or customer managed key.
- TTL: enable on `idempotency-v1` with attribute `ttl` (epoch seconds, one day
  after the record's own expiry). The service already treats expired records
  as absent; TTL only bounds growth. No other table uses TTL.
- Payloads above 256 KiB are stored gzip-compressed in `json_gz` (B); a record
  that still exceeds 320 KiB compressed fails closed.

Preserved semantics: package, release, upload-pointer and evidence creation are
`attribute_not_exists` create-only writes; release transitions compare and set
on `revision`; promotion activation commits release, package and audit
evidence atomically, guarded by revision, package `etag` and evidence absence,
and re-evaluates from fresh reads on contention; idempotency records compare
and set on `write_token`; the publication lease is a conditional update on its
expiry; the metadata version is an atomic `ADD`. Transactions carry a client
request token so SDK retries are idempotent. Nothing deletes items.

## S3 buckets

Bucket roles are the existing logical roles. Names must be distinct, lowercase,
and contain no dots.

| Role | Variable | Versioning | Object Lock | Notes |
| --- | --- | --- | --- | --- |
| quarantine | `REGISTRY_QUARANTINE_BUCKET` | optional | no | Upload staging; objects are deleted after promotion. Expire noncurrent versions and incomplete uploads. |
| public | `REGISTRY_PUBLIC_BUCKET` | required | optional, governance mode | SHA-256-addressed archives, create-only. |
| metadata | `REGISTRY_METADATA_BUCKET` | required | optional, governance mode | Versioned TUF files are create-only; `root.json` and `timestamp.json` are compare-and-set pointers. |
| private, evidence, backup | `REGISTRY_PRIVATE_BUCKET`, `REGISTRY_EVIDENCE_BUCKET`, `REGISTRY_BACKUP_BUCKET` | required | backup: recommended | Not used by any current AWS runtime; reserved for migration and backup tooling. |

- Immutability is enforced by the application's conditional writes:
  `If-None-Match: *` for archives and versioned metadata, `If-Match: <ETag>`
  for the two pointers. Infrastructure should also deny unconditional writes
  with a bucket policy: deny `s3:PutObject` on `<prefix>/blobs/*` and on
  `<prefix>/metadata/*` except `root.json` and `timestamp.json` when
  `s3:if-none-match` is absent, and deny `s3:PutObject` on the two pointers
  when both `s3:if-match` and `s3:if-none-match` are absent.
- Versioning makes any operator-level overwrite or delete recoverable. Object
  Lock protects versions from deletion; it does not stop a new version from
  being written, so it complements rather than replaces the conditional
  writes. If enabled, use governance mode with a retention period that fits
  the backup policy; never enable it on quarantine.
- Block all public access on every bucket. The public read path is served
  through the gateway, not by public bucket policy. Require TLS
  (`aws:SecureTransport`) and use SSE-S3 or SSE-KMS with bucket keys.
- Grant `s3:ListBucket` (scoped by `s3:prefix` to `<prefix>/`) to every
  runtime that reads a bucket. Without it S3 answers a missing key with 403
  instead of 404, and the service, which probes for absent objects, would fail
  closed.

## Runtimes

The object prefix defaults to `v1` (`REGISTRY_OBJECT_PREFIX`). Every server and
job needs all four `REGISTRY_KMS_<ROLE>_PUBLIC_KEY_HEX` values (public keys,
used for verification). No API, worker, or job accepts a
`REGISTRY_AWS_KMS_*` key ARN. HTTP services listen on `PORT` (default 8080).

| Runtime | Kind | Environments |
| --- | --- | --- |
| `serve-read-only-public-api` | long-running service | production only |
| `serve-public-api` | long-running service | development only |
| `serve-release-actions`, `serve-security-actions` | long-running private services | development only |
| `serve-tuf-signer` (×4, one per role) | long-running private services | both |
| `verify-source-once`, `scan-once`, `promote-once` | scheduled one-shot jobs | development only |
| `refresh-releases-once`, `refresh-security-once` | scheduled one-shot jobs | both, separately gated |
| `import-offline-bootstrap`, `bootstrap-online`, `import-offline-targets-renewal`, `import-offline-targets-rotation`, `verify-root-chain`, `import-offline-root-rotation`, `recover-expired-*-once` | ephemeral one-shot ceremony jobs | both |

Run one task, zero retries, for every one-shot job. Scheduled jobs follow the
existing schedule-activation rules in `infra/registry/AGENTS.md`.

### Health

- Public, read-only, and action APIs: `GET /health` (liveness). The public and
  read-only APIs also expose `GET /health/ready`, which returns 503 until a
  valid, unexpired TUF chain is published.
- Signers: `GET /healthz` returns 204 with no body. `/sign` requires
  `X-Forwarded-Proto: https`, so signers must sit behind an internal
  HTTPS-terminating load balancer (an internal ALB with an ACM certificate).
  The signer's audience is its HTTPS origin.

### IAM per runtime (DynamoDB and S3)

DynamoDB actions are `GetItem`, `PutItem`, `UpdateItem`, `Query`, and `Scan`;
transactions are authorized by `PutItem` on each table. No runtime needs
`DeleteItem`. S3 reads are `GetObject` plus prefix-scoped `ListBucket`.

| Runtime | DynamoDB | S3 |
| --- | --- | --- |
| read-only public API | `GetItem`, `Query`, `Scan` on packages, releases, review-artifacts, review-artifact-index | public and metadata: read |
| public API (dev) | all five actions on every table | quarantine: read, `PutObject`; public: read; metadata: read |
| release / security actions | all five actions on every table except release-uploads (read only) | metadata: read, `PutObject` on versioned metadata (never `root.json` or `timestamp.json`) |
| source / scan workers | all five actions on packages, releases, review-artifacts, review-artifact-index | quarantine: read |
| promote worker | all five actions on every table | quarantine: read, `DeleteObject`; public: read, `PutObject`; metadata: read, `PutObject` on versioned metadata |
| refresh, recovery, bootstrap-online, targets import, root import | state: `GetItem`, `UpdateItem` | metadata: read, `PutObject` on versioned metadata; root import also `root.json` |
| import-offline-bootstrap | none | metadata: read, `PutObject` on versioned metadata and `root.json` |
| verify-root-chain | none | metadata: read |

No coordinator, API, worker, or job receives any KMS permission. Only the
timestamp signer may write `timestamp.json`.

### Role-locked signers

Each online role has its own ECS service, task role, internal endpoint, and
KMS key.

| Variable | Value |
| --- | --- |
| `REGISTRY_TUF_SIGNER_PROVIDER` | `aws-kms` |
| `REGISTRY_TUF_SIGNER_SERVICE_NAME` | service name, e.g. `seen-tuf-releases-dev` (replaces Cloud Run's `K_SERVICE`) |
| `REGISTRY_TUF_SIGNER_ROLE` | `releases`, `security`, `snapshot`, or `timestamp` |
| `REGISTRY_AWS_KMS_<ROLE>_KEY_ARN` | key ARN for this role only; any other role's variable is rejected; aliases are rejected; region must equal `AWS_REGION` |
| `REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX` | the reviewed 32-byte public key |
| `REGISTRY_TUF_SIGNER_OBJECT_STORE_PROVIDER` | `s3` |
| `REGISTRY_TUF_SIGNER_METADATA_BUCKET`, `REGISTRY_TUF_SIGNER_OBJECT_PREFIX` | metadata bucket, prefix |
| `REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256` | pinned `1.root.json` digest |
| `REGISTRY_TUF_SIGNER_AUDIENCE` | this signer's HTTPS origin |
| `REGISTRY_TUF_SIGNER_CALLER_BINDINGS` | `operation=arn:aws:iam::<account>:role/<name>,…` (path-less role ARNs) |

KMS key: `KeySpec=ECC_NIST_EDWARDS25519`, `KeyUsage=SIGN_VERIFY`, single-region,
one key per role and environment, never shared. The signer task role gets only
`kms:Sign` and `kms:GetPublicKey` on its own key ARN, with key-policy
conditions `kms:SigningAlgorithm=ED25519_SHA_512` and `kms:MessageType=RAW`.
At startup the signer calls `GetPublicKey` and refuses to serve unless the key
spec, usage, algorithm and DER public key equal
`REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX`; it verifies every returned signature
locally. Key administrators must not hold `kms:Sign`. Each signer role reads
the metadata bucket; the timestamp role alone also has `s3:PutObject` on
`<prefix>/metadata/timestamp.json`.

**Size limit.** KMS signs `RAW` messages of at most 4096 bytes, and
`ED25519_PH_SHA_512` is Ed25519ph, which TUF Ed25519 clients do not accept.
The signer therefore rejects larger canonical metadata. Timestamp and snapshot
metadata stay well below the limit; releases and security metadata grow with
the number of targets and can exceed it. Measure the largest live delegated
metadata before cutover, and do not move a role whose metadata may exceed the
limit until the delegation layout is changed (for example hashed-bin
delegation) through a reviewed targets rotation.

**Caller identity.** Cloud Run IAM and Google OIDC are replaced by a
presigned STS `GetCallerIdentity` request. The coordinator signs it with its
task role, with the signer audience as a signed header and a 60-second
lifetime; the signer forwards it with its own configured audience to the pinned
regional STS endpoint and matches the returned role ARN against the
operation's caller bindings. A token minted for one signer fails signature
validation at another. Coordinators in DynamoDB mode choose this scheme
automatically and reject `SEEN_SIGNER_CALL_TOKEN`; the coordinator role needs
no IAM permission for it. Restrict signer ingress to the coordinator security
groups as defence in depth; the token check stays authoritative.

### Network egress

| Runtime | Destinations (HTTPS 443) |
| --- | --- |
| all | `dynamodb.<region>.amazonaws.com`, `s3.<region>.amazonaws.com` and bucket virtual hosts, as the runtime needs them (gateway VPC endpoints work) |
| signers | `kms.<region>.amazonaws.com`, `sts.<region>.amazonaws.com` (interface endpoints with private DNS work), S3 |
| coordinators (action APIs, promote worker, refresh and ceremony jobs) | the internal signer endpoints |
| source worker | `api.github.com`, `gitlab.com` |
| scan worker | none beyond DynamoDB and S3; the scanner runs as a local child process |

No runtime calls any Google endpoint in AWS mode.

## Ceremony changes for AWS online keys

TUF key IDs are derived from public-key bytes. Moving to AWS KMS creates four
new online public keys, so trust must be delegated to them explicitly; there is
no way to import the Cloud KMS private keys. The offline host, quorum,
custody, checklist passes and transfer rules in
[Signing operations](signing-operations.md) apply unchanged.

### Gathering the online public keys

1. Under the separately approved KMS phase, create the four keys described
   above and record their ARNs in the private ticket only.
2. For each key, retrieve the public key with `GetPublicKey` from two
   independent sessions, confirm `ECC_NIST_EDWARDS25519` and
   `ED25519_SHA_512`, and convert the DER SubjectPublicKeyInfo to the raw
   32-byte key (it is the final 32 bytes of the fixed 44-byte Ed25519 encoding).
3. Record the raw key hex and derived key ID per role as public ceremony
   inputs, and verify in a separate checklist pass that all four are distinct.
   An image build or CI artifact is not the source of trust for these values.

### A trust root that has not been bootstrapped yet

Run the initial ceremony exactly as documented, supplying the four AWS public
keys through `REGISTRY_KMS_<ROLE>_PUBLIC_KEY_HEX`. Root authorizes the
snapshot and timestamp keys; top-level targets delegates releases and security.
Then import with `import-offline-bootstrap` and `bootstrap-online` as AWS
one-shot jobs. This is the expected path for production, whose launch
identity is generated fresh.

### An existing trust root

Clients already pinned to the current root must update sequentially, so
existing trust is moved by monotonic transitions, never by re-bootstrapping
the same repository identity.

1. **Releases and security.** For each role in turn, run the existing targets
   rotation (`prepare-offline-targets-rotation` with the AWS public key as the
   replacement, then `import-offline-targets-rotation <role>`). Import runs on
   AWS against the role's new AWS signer plus snapshot and timestamp signers
   that are still trusted by root.
2. **Snapshot and timestamp.** Their keys are authorized by root. Replacing
   them needs the dual-threshold next-root transition described under
   "Targets authority, snapshot/timestamp key, or threshold policy". The
   fixed-policy root rotation helper deliberately cannot do this, so the
   dedicated transition tooling must exist, be reviewed and be exercised in the
   development drill before an existing chain moves. Until then, stay
   fail-closed on the current signers rather than hand-editing root.
3. During the move, each import must reach signers whose keys the committed
   chain still trusts: copy the verified metadata objects to the AWS metadata
   bucket first, confirm byte-identical SHA-256 for every object, and only
   then invoke AWS signers. Never run GCP and AWS timestamp signers against the
   same pointer.
4. After a client verifies the complete chain from the oldest supported root,
   disable the replaced Cloud KMS key versions. Keep all versioned metadata.

Record in the ceremony evidence, in addition to the existing fields, the KMS
key spec and algorithm checks, the raw-key derivation result, and the signer
startup log line confirming the `GetPublicKey` match. Never record ARNs or
account IDs in public evidence.

### Logs

Retain CloudTrail management events for KMS (`Sign`, `GetPublicKey`, key
policy, enable/disable/schedule-deletion), IAM, and ECS task definition
changes, and S3 data events for the metadata bucket. Alert on a `kms:Sign` by
any principal other than the matching signer role, any write to `root.json` or
`timestamp.json` by an unexpected role, and any rejected STS caller identity.
