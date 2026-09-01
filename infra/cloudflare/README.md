# Cloudflare platform foundation

This tree defines the account-independent Cloudflare foundation for Portfolio
and Seen. It is intentionally safe by default: both environment roots create
nothing until `resources_enabled = true`, and no configuration in this tree
owns a zone, DNS record, Worker route, custom domain, Worker version, or secret
payload.

No Cloudflare API call, OpenTofu initialization, plan, or apply is part of this
change.

## Ownership boundary

| Surface | Owner | Reason |
| --- | --- | --- |
| R2 bucket identities and reviewed Bucket Locks | OpenTofu | Durable, environment-scoped data resources and immutable-prefix policy |
| Queue identities and DLQs | OpenTofu | Durable resources shared by Worker releases |
| Development Access applications, policies, and service tokens | OpenTofu | Exact owner, machine, remote-client, and WIF-broker boundaries require durable reviewable identity policy |
| Worker versions and bindings | pinned Wrangler | Release artifacts and bindings change together |
| Durable Object classes/migrations | pinned Wrangler | Class lifecycle must match the deployed Worker code |
| Workflows and Containers | pinned Wrangler | Both are coupled to Worker exports and container images |
| Queue consumers | pinned Wrangler | Consumer settings are coupled to the Worker version |
| Zones, DNS records, routes, custom domains | unmanaged until import | Nameserver import and origin cutover are separate reviewed phases |
| Firestore | GCP | It remains the authoritative business database |

The machine-readable contract is
[`modules/platform/contract.json`](modules/platform/contract.json). OpenTofu
uses it to name R2 buckets and Queues and exports the corresponding Wrangler
binding contract. This keeps a single source of truth without letting both
tools own the same Cloudflare object.

## Layout

- `state-bootstrap/` creates only the private R2 state bucket. Its local state
  is an explicit bootstrap exception and must be stored encrypted and offline.
- `environments/dev/` and `environments/prod/` instantiate the same platform
  module with isolated names and R2 state keys.
- `modules/platform/` owns R2 buckets and Queues and exports Worker release
  contracts.
- `state/state-backup-lock.json` is the reviewed desired Bucket Lock policy for
  immutable state backups. It is not applied automatically.
- `scripts/wrangler-pinned.sh` resolves one exact Wrangler release for future
  Worker validation and deployment.

## Account bootstrap boundary

Account creation, passkey/2FA enrollment, recovery-code storage, billing,
Workers Paid, and the two Pro zone subscriptions remain owner-performed steps.
After that manual prerequisite, the state bucket is the first cloud mutation:

1. Copy `state-bootstrap/terraform.tfvars.example` to the ignored
   `state-bootstrap/terraform.tfvars` and set the account ID and globally unique
   bucket name.
2. Create and review one complete saved OpenTofu plan using a scoped
   Cloudflare infrastructure-apply token. Apply only that exact reviewed plan
   after explicit authorization.
3. Store the bootstrap root's local state encrypted and offline. Never commit
   it or upload it as a plaintext workflow artifact.
4. Create bucket-scoped R2 S3 credentials for the state bucket. Supply them to
   OpenTofu only through `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`.
5. Copy each environment's `backend.hcl.example` to ignored `backend.hcl`,
   replace its placeholders, and initialize only after the state bucket exists.

The R2 backend enables native S3 lockfiles. R2 supports the conditional writes
that lockfiles require, while GitHub concurrency remains the outer single-writer
control. R2 does not provide S3 bucket versioning, so every approved apply must
first copy the current state object to a unique `backups/<environment>/...`
key. Apply the reviewed `state/state-backup-lock.json` policy separately so
those copies cannot be overwritten or deleted during their retention window.
Do not lock the active `state/` prefix.

Credentials never belong in `backend.hcl`; OpenTofu persists partial backend
configuration under `.terraform/`.

## DNS import boundary

`zones` accepts the future Cloudflare zone ID, exact zone name, verified DNS
inventory SHA-256, and assigned nameservers. The values are exported for later
import work, but there are deliberately no `cloudflare_zone`,
`cloudflare_dns_record`, route, or custom-domain resources here.

Before any DNS import, capture every A/AAAA/CNAME/MX/TXT/DKIM/DMARC record,
canonicalize the inventory, verify its SHA-256, and review an import-only plan.
Nameservers change while GCP remains the origin. Application cutover is a later
approval.

## Regional contract

- Portfolio Containers: `ME`, with `WEUR` fallback. Portfolio R2 buckets use
  the closest currently supported R2 location hint, `weur`; R2 reads remain
  global. Portfolio Firestore remains separately managed in `me-central2`.
- FinOps invoices and receipts use the dedicated private
  `FINOPS_RECEIPTS` bucket. It is never attached to a public read route;
  objects use immutable SHA-256 keys and are accessible only through the
  authenticated Portfolio edge-to-origin contract.
- Seen coordination and writes: `ENAM`/`WNAM`; R2 uses `enam` as its creation
  hint and is read globally. Seen Firestore remains in the existing US
  database.

The platform module applies Bucket Locks only where the key layout is already
safe: published content-addressed package blobs are indefinite, while evidence
and backup SHA-256 prefixes retain objects for 90 days. Quarantine and private
objects stay deletable for cleanup and account erasure. TUF metadata remains
unlocked until versioned metadata moves under a prefix that excludes mutable
`root.json` and `timestamp.json` pointers.

## Validation

Run the offline checks from the repository root:

```sh
bash infra/cloudflare/scripts/validate-contracts.sh
tofu fmt -check -recursive infra/cloudflare
```

The first command requires only Bash, `jq`, and `rg`. The second requires
OpenTofu 1.12.x and does not initialize a backend or contact Cloudflare. The
module also contains mock-provider OpenTofu tests for CI once provider plugins
are initialized in an approved networked environment.

Future plans and applies must retain the repository's protected-production
contract: one complete saved plan, a separate plan approval and apply approval,
and application of only the exact reviewed plan by the repository's sole human
operator.

## Dev FinOps WIF broker boundary

The optional `finops_wif_access_enabled` gate creates only in dev and only when
the owner Access boundary is complete. It provisions a dedicated 90-day
service token, a Service Auth (`non_identity`) policy, and an application for
the exact `/internal/gcp-wif/assertion` route with a one-hour session. It does
not grant that token access to `/admin/spending` or `/api/admin/finops/*`.

The client secret is a sensitive OpenTofu output because Cloudflare displays it
only at creation. Pipe it directly into the `GCP_WIF_ACCESS_CLIENT_SECRET`
Worker secret during the approved dev apply; never write it to a tfvars file,
render it in a plan, or publish it as a workflow artifact. The non-sensitive
`finops_access.wif_broker` output supplies the client ID and application
audience required by the Google WIF provider and Worker configuration.

## Dev Samurai remote Access boundary

The Samurai development hostname remains protected by the hostname-wide,
owner-only GitHub Access application. Five more-specific path applications are
the only remote exceptions, and Cloudflare evaluates those path applications
instead of inheriting the hostname-wide policy:

| Development path | Access action | Required application authentication |
| --- | --- | --- |
| `/api/remote/*` | Bypass | Samurai account, host, one-time pairing, or mobile credential required by the endpoint |
| `/ws/remote/*` | Bypass | Samurai host or mobile relay credential |
| `/.well-known/assetlinks.json` | Bypass | Static Android package/signing-certificate association only; no account data or mutation |
| `/api/v1/billing/paddle/webhook` | Bypass | Paddle server-to-server webhook; authenticity comes from the HMAC-SHA256 `Paddle-Signature` header verified by the API origin, not from an Access session |
| `/internal/remote/inference/*` | Service Auth | Dedicated Access service token **and** a scoped Samurai application bearer validated by the SaaS origin |

The remote API bypasses make non-browser clients routable; they do not make
the Samurai API public. The single static Android exception is required because
Android verifies App Links without a browser Access session. Anonymous, expired,
cross-account, or revoked API credentials must still be rejected by the
Worker/SaaS boundary. The dedicated inference
service token cannot access either remote-client path or any other internal
route, and an Access token alone is insufficient for inference. The Worker also
rejects this route unless the request origin exactly matches the canonical dev
custom domain, so an enabled `workers.dev` route cannot bypass Service Auth.

All four applications are derived only when the platform module is enabled
with `environment = "dev"` and the complete dev Access boundary. The production
environment does not pass any Access inputs and contract tests require all
four resources to be absent there. The sensitive inference client secret is
available only inside `dev_machine_access_client_secrets`; pipe its exact map
entry directly into the approved Samurai credential destination and never save
it in tfvars, state-rendered artifacts, or logs.

References:

- [Cloudflare provider](https://registry.terraform.io/providers/cloudflare/cloudflare/latest)
- [Cloudflare Access application paths](https://developers.cloudflare.com/cloudflare-one/access-controls/policies/app-paths/)
- [Cloudflare Access service-token policies](https://developers.cloudflare.com/cloudflare-one/access-controls/policies/common-policies/#authenticate-a-service-using-a-service-token)
- [Cloudflare R2 remote backend](https://developers.cloudflare.com/terraform/advanced-topics/remote-backend/)
- [Wrangler configuration](https://developers.cloudflare.com/workers/wrangler/configuration/)
- [R2 Bucket Locks](https://developers.cloudflare.com/r2/buckets/bucket-locks/)
- [OpenTofu S3 backend](https://opentofu.org/docs/language/settings/backends/s3/)
