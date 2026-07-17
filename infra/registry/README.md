# Seen registry infrastructure

This directory owns the isolated Google Cloud infrastructure for the Seen
package registry. The reusable module is instantiated by a concrete development
root and a deliberately inert production root.

OpenTofu manages resource containers, identities, IAM, network policy,
Cloud Run definitions, schedules, and monitoring. It never manages secret
payload versions, offline private keys, offline signing workspaces, bootstrap
envelope bytes, or trust-ceremony artifacts.

## Safety boundaries

- Development and production have different projects, names, networks, buckets,
  keys, identities, state buckets, and signed repository IDs.
- GitHub CI exchanges an OIDC token only from the exact registry workflow on
  the exact development branch. Its dedicated service account can write only
  to this Artifact Registry repository and receives no runtime `actAs`, data,
  signing, public-key, or infrastructure-apply grant. No service-account key is
  created or stored.
- Each signer service can call only `cloudkms.cryptoKeyVersions.useToSign` on
  its exact role key. Only a review/deployment identity can retrieve public
  keys for pinning.
- The offline bootstrap importer can create exactly `1.root.json`,
  `1.targets.json`, and the initial `root.json` pointer. It cannot invoke a
  signer or use Firestore.
- The online bootstrap coordinator can create exactly the three version-one
  online metadata objects, use the Firestore publication lease, and invoke the
  four role signers. It cannot create or replace `root.json`, read offline
  envelope secrets, or access quarantine/public buckets.
- Only the timestamp signer can generation-CAS `timestamp.json`; only the
  short-lived root importer can generation-CAS `root.json` after bootstrap.
- Public access prevention stays enforced on every bucket. Any future CDN
  origin may expose only the immutable `v1/blobs/sha256/` namespace; metadata
  and mutation routes remain behind the authenticated portfolio gateway.
- Scanner, API, signers, and root verifier use restricted Google API egress and
  have all other VPC egress denied. The source verifier uses NAT for forge
  access. Signer-calling coordinators currently retain general NAT egress, but
  all of their traffic is VPC-routed and the signer origins accept only
  same-project internal ingress. Coordinators have no KMS or pointer authority.

Application-origin ingress defaults to `INGRESS_TRAFFIC_ALL` for the first
cutover because the existing portfolio gateway is not attached to this VPC.
This does not grant public invocation: Cloud Run IAM permits only the portfolio
gateway account. The four privileged signer origins always use
`INGRESS_TRAFFIC_INTERNAL_ONLY`; their enumerated callers send all traffic
through this stack's Direct VPC network and must also pass Cloud Run IAM and the
signer's independent audience, principal, operation, and role checks.

## Workflow

Use OpenTofu 1.12.x. Run commands from the repository root:

```sh
tofu fmt -check -recursive infra/registry
tofu -chdir=infra/registry/environments/dev init -backend-config=backend.hcl
tofu -chdir=infra/registry/environments/dev validate
tofu -chdir=infra/registry/environments/dev plan -out=dev.tfplan
tofu -chdir=infra/registry/environments/dev show dev.tfplan
```

Keep `workloads_enabled=false`, schedules disabled, and schedules paused during
foundation import. Follow [IMPORTS.md](IMPORTS.md), inspect a zero-surprise
plan, then enable workloads with separately reviewed immutable application and
signer image digests plus reviewed numeric KMS/Secret Manager versions. Select
exactly the required `ceremony_operations` entry for one reviewed operation and
remove it immediately afterward; unselected ceremony identities have no data,
secret, pointer, or signer-invocation IAM.

For transitional functional testing with `edge_cutover_enabled=false`, the module emits
the exact gateway values:

- `SEEN_REGISTRY_UPSTREAM_URL`
- `SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL`
- `SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL`

That bridge is not FEL-637 completion. `edge_provisioned=true` creates a global
IPv4 address, managed certificate, HTTPS/redirect
forwarding rules, serverless NEGs, backends, and a URL map:

- release `yank`/`unyank` suffixes route to the release-action service;
- `security-quarantine`/`security-reinstate` route to the security-action
  service;
- all remaining `/packages/api/v1` paths route to the public registry API;
- every docs, playground, and non-registry path falls back to `portfolio-dev`.

The API rule matches only the exact `/packages/api/v1` path and its
slash-delimited descendants, so lookalikes such as `/packages/api/v1evil`
remain on the portfolio fallback. Registry backends use a MODERN minimum-TLS
1.2 policy. SQLi, XSS, and per-IP rate-limit rules start in Cloud Armor preview
mode; archive `PUT` bodies are excluded from those WAF signatures. An enforced
edge rule rejects archive uploads whose `Content-Length` is absent, malformed,
zero, or above 26,214,400 bytes, while the application's streaming 25 MiB check
remains authoritative for the complete body. Inspect preview logs and tune
before a separate enforcement review. CDN remains disabled.

Only after `edge_cutover_enabled=true` do candidate application services disable
their default `run.app` URLs and Cloud Run invoker IAM and accept traffic only
from internal/load-balancer ingress. Signers remain VPC-internal and separately
IAM-authenticated.
The stack deliberately creates no DNS record and explicitly disables CDN on
every backend. Apply the emitted Certificate Manager DNS-authorization CNAME
first, without moving the host. Wait for the certificate to become active,
review the emitted global address, lower the existing host TTL, then perform a
separately approved A-record cutover. The portfolio fallback keeps docs and
playground available while registry paths move directly. After route and E2E
checks pass, remove the portfolio gateway environment and its transitional
invoker binding. A future CDN may be added only for immutable
`/packages/api/v1/blobs/sha256/` responses, never metadata or mutation routes.

Keep rollback available throughout the phased edge handoff:

1. Before host DNS moves, set `edge_cutover_enabled=false` to restore candidate
   gateway invocation and default URLs; the live legacy service remains
   untouched.
2. After the A-record cutover but before legacy authority is revoked, restore
   the prior `seen.dev.yousef.codes` CNAME to `ghs.googlehosted.com.` and retain
   the portfolio domain mapping. Wait at least the previous DNS TTL.
3. Preserve the global address, certificate, URL map, candidate services, old
   service, and old gateway wiring through the soak window. Do not destroy the
   edge as a rollback mechanism.
4. Revoke legacy signing/storage authority and remove gateway wiring only after
   publish, install, TUF, mutation, direct-origin, and rollback drills pass.

The development root emits `workload_identity_provider` and
`image_publisher_service_account` for `google-github-actions/auth`. The provider
accepts only:

- repository `codeyousef/summon-portfolio`;
- immutable repository ID `1091564909` and owner ID `10247142`;
- ref `refs/heads/dev`;
- workflow `codeyousef/summon-portfolio/.github/workflows/deploy-seen-registry.yml@refs/heads/dev`;
- event `push`;
- protected environment `seen-registry-development-image`.

The environment-gated workflow may build/test and publish an immutable
application image after registry changes land on `dev`. It must not apply this
stack, update Cloud Run, select ceremony operations, or change the separately
reviewed signer digest. The current cutover, infrastructure changes, and signer
changes remain manual reviewed owner applies.

Create that GitHub environment with required reviewers and disallow bypass
before enabling the WIF publisher. Without those repository-side protections,
keep `github_ci_enabled=false`; the cloud-side claim check does not create or
protect the GitHub environment.

The gateway runtime account must be discovered from the existing service, not
guessed:

```sh
gcloud run services describe portfolio-dev \
  --project portfolio-476219 \
  --region us-central1 \
  --format='value(spec.template.spec.serviceAccountName)'
```

Production can only build an inert foundation. The module rejects production
workloads, schedules, and ceremonies, and the prod root rejects the current
development project.

## Gates not hidden by this stack

- The current deployment workflow must be reduced to test/image publication
  using the emitted OIDC provider and service account. Remove the static
  `GCP_SA_KEY`; imperative IAM, Cloud Run, and job mutations would drift this
  state.
- Routine application and signer image inputs remain separate:
  `container_image` and privileged `signer_container_image`. CI never applies
  either plan. A separately reviewed manual principal inspects and applies the
  complete state, with signer/ceremony changes called out explicitly.
- The runtime image must contain `import-offline-bootstrap` and
  `bootstrap-online` before those ceremony operations can be selected.
- The application must emit `tuf_signing_rejected` and
  `tuf_metadata_expiry_breach` structured events before those log-based alerts
  are meaningful. Uptime, job-error, and unexpected-KMS-use alerts do not
  depend on those application events.
- Existing broad bucket, KMS, service-account, and Cloud Run IAM grants must be
  inventoried and revoked during import. Additive IAM member resources cannot
  erase an unmanaged legacy grant.
- Source and signer-calling coordinators retain initial HTTPS NAT access even
  though coordinator-to-signer traffic is VPC-internal. Restrict coordinator
  egress to its exact external dependencies after the functional drill; only
  the source verifier requires general forge access.
- A budget is created only when both `billing_account_id` and
  `monthly_budget_usd` are supplied.

See [MOVED.md](MOVED.md) before changing any resource address.
