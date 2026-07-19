# Seen registry infrastructure

This directory owns the isolated Google Cloud infrastructure for the Seen
package registry. The reusable module is instantiated by a concrete development
root and an inert-by-default production root whose launch surfaces are enabled
independently.

OpenTofu manages resource containers, identities, IAM, network policy,
Cloud Run definitions, schedules, and monitoring. It never manages secret
payload versions, offline private keys, offline signing workspaces, bootstrap
envelope bytes, or trust-ceremony artifacts.

## Safety boundaries

- Development and production have different projects, names, networks, buckets,
  keys, identities, state buckets, and signed repository IDs.
- When enabled for an environment, GitHub CI exchanges an OIDC token only from
  the exact registry workflow, branch, repository identity, event, and protected
  environment configured by that root. Its dedicated service account can write
  only to that environment's Artifact Registry repository and receives no
  runtime `actAs`, data, signing, public-key, or infrastructure-apply grant. No
  service-account key is created or stored.
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

### Production protected rollout

The production roots are inert with their default inputs. Adding
`deploy-seen-registry-infrastructure.yml` is a code-only change: it does not
create a GitHub environment, federated identity, state bucket, project, plan, or
cloud resource, and it does not authorize a workflow dispatch. The bootstrap
root deliberately starts with a local backend, and the workflow rejects that
root until the separately reviewed remote-state migration is committed. Do not
dispatch the workflow or run a cloud apply as part of this code-only change.

The isolated project is fixed to `seen-registry-prod-476219`. The production
root fixes the signed trust identity to this exact tuple:

- environment: `production`;
- repository ID: `seen-prod-registry-v1`;
- registry origin: `https://seen.yousef.codes/packages`.

Do not reuse a development state bucket, key, identity, object, envelope, or
signed metadata file. The production root does not expose the writer-capable
stack, source verification, scanning, promotion, release/security action
services, schedules, edge provisioning, or edge cutover. Its API-only gateway
output contains only `SEEN_REGISTRY_PUBLIC_HOST` and
`SEEN_REGISTRY_UPSTREAM_URL`; both action upstreams remain absent.

#### Saved-plan approval contract

Every phase requires one new, complete saved plan from the current state, an
offline review, an explicit approval, and application of that exact binary
plan. Never use `-target`, regenerate a plan after review, or apply a plan after
its commit, inputs, state, root, backend, or approval context changes. Discard
it and start the phase again instead.

Before the OIDC handoff, use direct human ADC and ignored mode-0600 inputs and
plan files. Do not impersonate the infrastructure apply account. A typical
human bootstrap phase is:

```sh
umask 077
tofu -chdir=infra/registry/environments/prod-bootstrap init \
  -backend-config=backend.hcl
tofu -chdir=infra/registry/environments/prod-bootstrap validate
tofu -chdir=infra/registry/environments/prod-bootstrap plan \
  -var-file=terraform.tfvars \
  -out=/secure/path/prod-bootstrap.tfplan
tofu -chdir=infra/registry/environments/prod-bootstrap show \
  /secure/path/prod-bootstrap.tfplan
tofu -chdir=infra/registry/environments/prod-bootstrap apply \
  /secure/path/prod-bootstrap.tfplan
```

The protected workflow is `workflow_dispatch`-only on `master` and accepts only
`prod` or `prod-bootstrap`. A plan-only dispatch cannot later be promoted to an
apply. To apply, start one run with `apply=true` and
`apply_confirmation=APPLY_SAVED_PLAN`; the apply job then consumes only the
same run's reviewed plan. Concurrency is serialized and a running production
operation cannot be cancelled by a newer dispatch.

#### Protected GitHub environments and encrypted review

Create all four protected environments before setting either review gate:

- `seen-registry-production-plan`;
- `seen-registry-production-apply`;
- `seen-registry-production-materials`;
- `seen-registry-production-jobs`.

Set `github_infrastructure_environments_reviewed=true` only after the plan and
apply environments pass these checks. Separately set
`github_operations_environments_reviewed=true` only after the materials and
jobs environments pass them. Keeping the gates separate prevents an older
infrastructure-only attestation from activating operations federation.

Each environment must:

- allow deployments only from `master`;
- require explicit approval from the repository's sole human operator;
- permit the workflow initiator to provide that approval;
- disable administrator bypass.

This is intentionally a solo-operator control, not independent or two-person
review. The operator must approve plan and apply as two distinct actions. Before
approving apply, they must verify the exact run, commit, artifact coordinates,
hashes, and decrypted saved plan described below. Do not record either
attestation until its two environments have been configured and tested to hold
an unapproved deployment for review, reject non-`master` deployment branches,
and disallow administrator bypass. Configure these environment-scoped values
exactly:

`seen-registry-production-plan` variables:

- `GCP_REGISTRY_PROD_INFRA_WORKLOAD_IDENTITY_PROVIDER`: the full
  `infrastructure_workload_identity_providers["plan"]` output, ending in
  `/workloadIdentityPools/seen-registry-prod-infra/providers/infra-plan`;
- `GCP_REGISTRY_PROD_INFRA_SERVICE_ACCOUNT`:
  `seen-registry-prod-plan@seen-registry-prod-476219.iam.gserviceaccount.com`;
- `SEEN_REGISTRY_PROD_PLAN_ARTIFACT_KEY_FINGERPRINT`: the full primary
  fingerprint of the reviewed plan-artifact key.

`seen-registry-production-plan` secrets:

- `SEEN_REGISTRY_PROD_PLAN_ARTIFACT_PUBLIC_KEY_B64`;
- `SEEN_REGISTRY_PROD_TFVARS_B64`;
- `SEEN_REGISTRY_PROD_BOOTSTRAP_TFVARS_B64`.

`seen-registry-production-apply` variables:

- `GCP_REGISTRY_PROD_INFRA_WORKLOAD_IDENTITY_PROVIDER`: the full
  `infrastructure_workload_identity_providers["apply"]` output, ending in
  `/workloadIdentityPools/seen-registry-prod-infra/providers/infra-apply`;
- `GCP_REGISTRY_PROD_INFRA_SERVICE_ACCOUNT`:
  `seen-registry-prod-iac@seen-registry-prod-476219.iam.gserviceaccount.com`;
- `SEEN_REGISTRY_PROD_PLAN_ARTIFACT_KEY_FINGERPRINT`: the same full primary
  fingerprint used by the plan environment.

`seen-registry-production-apply` secrets:

- `SEEN_REGISTRY_PROD_PLAN_ARTIFACT_PRIVATE_KEY_B64`;
- `SEEN_REGISTRY_PROD_PLAN_ARTIFACT_PRIVATE_KEY_PASSPHRASE`.

`seen-registry-production-materials` variables:

- `GCP_REGISTRY_PROD_OPERATIONS_WORKLOAD_IDENTITY_PROVIDER`: the full
  `operations_workload_identity_providers["materials"]` output, ending in
  `/workloadIdentityPools/seen-registry-prod-infra/providers/material-ops`;
- `GCP_REGISTRY_PROD_OPERATIONS_SERVICE_ACCOUNT`:
  `seen-registry-prod-materials@seen-registry-prod-476219.iam.gserviceaccount.com`.

`seen-registry-production-materials` secrets:

- `SEEN_REGISTRY_PROD_BOOTSTRAP_ROOT_ENVELOPE_PAYLOAD_B64`;
- `SEEN_REGISTRY_PROD_BOOTSTRAP_TARGETS_ENVELOPE_PAYLOAD_B64`;
- `SEEN_REGISTRY_PROD_TARGETS_RENEWAL_ENVELOPE_PAYLOAD_B64`;
- `SEEN_REGISTRY_PROD_TARGETS_RELEASES_ROTATION_ENVELOPE_PAYLOAD_B64`;
- `SEEN_REGISTRY_PROD_TARGETS_SECURITY_ROTATION_ENVELOPE_PAYLOAD_B64`;
- `SEEN_REGISTRY_PROD_ROOT_ROTATION_ENVELOPE_PAYLOAD_B64`.

`seen-registry-production-jobs` variables:

- `GCP_REGISTRY_PROD_OPERATIONS_WORKLOAD_IDENTITY_PROVIDER`: the full
  `operations_workload_identity_providers["jobs"]` output, ending in
  `/workloadIdentityPools/seen-registry-prod-infra/providers/job-ops`;
- `GCP_REGISTRY_PROD_OPERATIONS_SERVICE_ACCOUNT`:
  `seen-registry-prod-job-runner@seen-registry-prod-476219.iam.gserviceaccount.com`.

The jobs environment has no payload secrets.

Generate a dedicated passphrase-protected RSA OpenPGP encryption key on an
offline host. Export only its public key for the plan environment. Store the
base64-encoded private-key export and its passphrase only in the apply
environment, and retain a separate protected offline review/recovery copy. Never put
the private key, passphrase, decoded production variables, or decrypted plan
material in the repository, an issue, a workflow log, or an unencrypted
artifact.

The plan job suppresses plan diagnostics from public logs and encrypts exactly
`plan.tfplan`, `review.txt`, and `metadata.json`. The only uploaded file is
`plan-bundle.tar.gpg`, retained for one day. Before approving the apply
environment, the approving operator downloads that ciphertext to the offline
review host, verifies its published digest, decrypts it, verifies the inner
digests and metadata, and reviews `review.txt`. The apply job independently
checks the same-run artifact ID, ciphertext and inner hashes, workflow commit,
root, backend, repository identity, and OpenTofu version before applying.

The permanent plan-artifact key fingerprint is pinned as a reviewed constant in
the workflow. Each protected environment must contain that same fingerprint,
and the imported public or private key must match it. Rotating the key therefore
requires a reviewed code change as well as protected-environment updates, so an
environment administrator cannot substitute both a key and its expected
fingerprint.

#### One-time direct-human bootstrap

All bootstrap gates start false. Complete each numbered item with a separate
full saved plan where it changes managed state:

1. Create and protect all four GitHub environments, prepare the offline
   encryption key, and obtain the one-time Billing Account IAM Admin capability
   (or an equivalent exact `setIamPolicy` capability) on the selected billing
   account.
   The latter is an external prerequisite for installing the two exact-account
   `seenRegistryBillingRefresh` read bindings; neither infrastructure WIF
   identity receives billing-policy mutation permission. Remove a temporary
   external billing privilege after those bindings are verified. The
   direct-human bootstrap also requires separately approved, time-bounded
   authority to create the project,
   define organization custom roles, update organization IAM, enable the two
   control-project APIs, and update IAM on the exact control project. These are
   external bootstrap prerequisites, not standing grants created for an
   infrastructure WIF identity; remove any temporary assignments and verify
   their absence after the corresponding bootstrap phase.
2. Copy `environments/prod-bootstrap/backend.hcl.example` to the ignored
   backend file. The initial backend is the mode-0600 local path
   `.state/terraform.tfstate`. It contains infrastructure configuration
   metadata, but no secret payload versions or private signing material. Never
   place it in the development bucket.
3. Apply only `enable_control_project_apis=true` first. It enables only
   Organization Policy and Cloud Billing in `portfolio-476219`. Inspect the
   organization for a stored `compute.skipDefaultNetworkCreation` policy before
   planning the guardrail. If one exists, import that exact policy into
   `google_org_policy_policy.skip_default_network[0]` and review both the import
   and post-import plan as state mutations. If no stored policy exists, review
   creation of the enforced policy in the complete saved plan. Never assume a
   prior policy exists or try to import an absent policy.
4. Apply `enable_organization_guardrails=true` with the temporary organization
   Policy Admin lease. Wait for propagation, independently verify the effective
   no-default-network policy, and only then set
   `organization_guardrail_effective=true`. Remove the organization Policy Admin
   lease in another complete plan while retaining the policy.
5. With the reviewed GitHub-environment attestation true, apply
   `enable_production_project_bootstrap=true` using direct ADC as
   `yousef@felidai.com`. Keep Owner adoption/removal, the gateway exception, and
   the OIDC handoff false; enable only the temporary project Policy Admin lease
   and exact human state-migration access required for the reviewed phases. This
   creates the isolated project, eight bootstrap-owned APIs, two state buckets,
   the monitoring channel, the four protected plan, apply, materials, and jobs
   identities and providers, and their bounded custom roles.
6. Immediately audit direct project Owner and Editor bindings. Confirm the only
   automatically added bootstrap Owner is `user:yousef@felidai.com`. In its own
   plan set `project_creator_owner_member="user:yousef@felidai.com"` and
   `adopt_project_creator_owner=true`. Accept only import of that already
   existing binding with no IAM addition, then verify it is in state. OpenTofu
   must never create an Owner grant.
7. Migrate the bootstrap state immediately after its destination is verified.
   The two independent backends are:

   - bootstrap: bucket
     `seen-registry-prod-476219-bootstrap-tofu-state`, prefix
     `seen-registry/bootstrap/prod`;
   - production: bucket `seen-registry-prod-476219-prod-tofu-state`, prefix
     `seen-registry/prod`.

   Pull a private backup, then use a reviewed follow-up commit to change
   `environments/prod-bootstrap/versions.tf` from `backend "local"` to
   `backend "gcs"` and use
   `environments/prod-bootstrap/backend.gcs.hcl.example`. Run
   `tofu init -migrate-state -force-copy -backend-config=backend.hcl` as a
   separately approved state-only operation.
   Verify lineage, serial, the full resource list, and destination generation;
   require a zero-change remote plan before deleting the backup and old local
   state. The production root uses its separate bucket from its first plan. The
   protected workflow refuses bootstrap planning while the tracked backend is
   still local.
8. Wait for and inspect the effective
   `iam.managed.allowedPolicyMembers` and
   `iam.automaticIamGrantsForDefaultServiceAccounts` policies. The managed
   policy permanently includes the exact human operator as an allowed subject
   so the temporary state binding is policy-valid. That allowlist entry is not
   an IAM grant. Set `managed_member_policy_effective=true` and
   `automatic_default_service_account_grants_policy_effective=true` only after
   the effective policies match the reviewed configuration. Do not enable
   Compute before the automatic-default-service-account policy is effective.
9. Under the temporary project Policy Admin lease, apply
   `enable_portfolio_gateway_exception=true` only after the managed-member policy
   is effective. Wait for propagation and independently verify the effective
   legacy-domain exception before setting
   `portfolio_gateway_exception_effective=true`. The attested follow-up plan may
   then install the two exact OIDC-to-service-account trust bindings without
   being rejected by the inherited legacy restriction. Retain both protected
   policies, then remove the project Policy Admin lease. Both organization and
   project Policy Admin leases must now be false.
10. Complete the email verification and refresh until
    `production_notification_channel_verification_status` is exactly `VERIFIED`.
    The production root rejects a channel that is not the exact same-project,
    enabled, verified bootstrap channel.
11. Apply the direct-human production foundation from the dedicated production
    backend. Set `bootstrap_production_policies_effective=true` only after the
    inherited no-default-network policy, automatic-default-service-account
    policy, managed-member policy, and gateway exception are all verified
    effective. Set `enable_production_foundation=true`, pass the exact channel
    ID, and keep every runtime selector false: read-only API, root verifier,
    refresh jobs, and signer JWKS are disabled, and
    `production_ceremony_operations=[]`.

    Review that this empty foundation pre-provisions the exact
    `seen-registry-prod-iac` `roles/iam.serviceAccountUser` binding on only the 14
    approved API, verifier, refresh, signer, and ceremony service accounts. It
    must exclude the hard-disabled source, scanner, promoter, action, and
    scheduler identities and retain read access to only the production Artifact
    Registry repository. If production image publication is
    approved, create its protected image-publisher identity and exact repository
    writer binding in this direct-human phase as well. There is intentionally no
    steady-state Artifact Registry or service-account IAM setter, so all required
    bindings must exist before handoff. Then record
    `production_foundation_applied=true` and, when applicable,
    `production_image_publisher_foundation_applied=true` in the bootstrap root.
    The foundation creates KMS key and Secret Manager containers only. It does
    not create a KMS key version, add a secret payload version, or execute a
    Cloud Run job.
12. Remove the creator Owner in its own direct-human saved plan. Keep exact human
    state access to both roots, keep both Policy Admin leases false, and require
    the effective gateway exception and completed foundation attestations. Set
    `adopt_project_creator_owner=false`, keep the exact member long enough to
    identify the imported binding, and set
    `approve_project_creator_owner_removal=true`. Accept only deletion of that
    exact imported Owner. After apply, verify no direct Owner or Editor remains;
    then clear the member and removal input and set
    `project_creator_owner_removed=true`.

No phase grants a human Storage Object Admin or Service Account Token Creator
role. The temporary human backend access uses only the custom exact-state
reader/writer permissions on both named roots and remains solely so the human
can migrate state, apply the empty foundation, and remove the imported Owner.
Storage data reads and writes are audit logged.

#### First WIF handoff and steady state

Populate the protected environment values from the verified bootstrap outputs
only after the backend migration and human bootstrap are complete. The first
protected `prod-bootstrap` saved-plan apply must set
`project_executor_handoff_complete=true`, keep
`project_creator_owner_removed=true`, clear all Owner adoption/removal inputs,
keep both Policy Admin leases false, and set
`enable_temporary_human_state_migration_access=false`. That same apply replaces
both authoritative state-bucket policies and removes the human's actual state
access. Verify the binding is absent; the permanent managed-policy allowlist
entry still does not grant access.

The plan identity and apply identity then use GitHub OIDC-derived ADC directly;
they do not share a service account or depend on human impersonation. Keep the
bootstrap root at a no-op/read-only refresh after this cleanup. Changes to the
WIF pool/providers, their service-account trust, infrastructure custom roles,
organization or project policies, billing-account bindings, Artifact Registry
IAM, service-account IAM, or the state-policy capability boundary require a
separately approved privileged bootstrap path. They are not ordinary steady
production applies. In particular, later Artifact Registry or runtime `actAs`
drift cannot be repaired by broadening the protected apply identity.

The same boundary applies to post-handoff drift in required API enablement,
durable Artifact Registry, Firestore, network, bucket, KMS, and Secret Manager
foundation resources, project audit configurations, and billing budgets. An
ordinary plan may detect that drift, but its apply identity intentionally cannot
remediate it. Stop and use a separately approved privileged path; do not expand
the ordinary production identity to make the plan apply.

#### Protected production operations

The `operate-seen-registry-production.yml` workflow is the post-handoff path for
bounded material and job operations. It is manual `workflow_dispatch` only,
runs only from the exact `master` workflow commit, requires
`OPERATE_SEEN_REGISTRY_PRODUCTION`, and serializes all production operations
without cancelling an in-progress dispatch. Each dispatch selects exactly one
of these operation classes:

- create the next reviewed version on one of the four exact online signing
  keys;
- add the next reviewed payload version to one of the six exact envelope
  secrets;
- execute one of the nine allowlisted ceremony, verifier, or refresh jobs with
  its exact immutable image, runtime identity, arguments, one task, and zero
  retries.

Materials dispatches require the exact positive next version. Secret-version
dispatches additionally require the SHA-256 of the exact decoded Secret Manager
payload bytes. Job dispatches require the exact immutable Artifact Registry
image URI and reject material-version inputs. The materials identity has only
bounded KMS-version creation/inspection and Secret Manager version-addition
authority; it cannot sign or read secret payloads. The job identity can inspect
and run only the currently selected exact jobs. Neither operations identity has
infrastructure plan/apply, state, organization, billing, or control-project
authority, and neither infrastructure identity receives material or job
operations authority.

Every `*_PAYLOAD_B64` GitHub secret is base64 of the exact bytes to store in
Secret Manager. For the initial bootstrap root and targets envelopes, the bytes
stored in Secret Manager are themselves base64 text, so the GitHub secret
base64-encodes that text and therefore double-encodes the source envelope. For
targets renewal, targets role rotations, and root rotation, the bytes stored in
Secret Manager are raw envelope JSON, so the GitHub secret is one base64
encoding of that raw JSON. Compute `expected_payload_sha256` over the bytes
obtained after decoding the GitHub secret once, never over the GitHub secret
text or a differently encoded source file.

This path creates no schedule and grants no standing runtime job authority for
an unselected job. A reviewed infrastructure apply must first provision the
selected job and its resource-level authorization; after successful execution,
another complete saved-plan apply removes transient ceremony jobs and authority.
Creating the identities, configuring the protected environments, or merging the
workflow is not approval to create a key version, add payload bytes, or run a
job. Each external operation still requires its own protected-environment
review and exact dispatch inputs.

Manual operation must provide the same freshness outcome that an automated
production cadence would provide. Releases metadata has a seven-day maximum
lifetime, security and timestamp metadata have six-hour maximum lifetimes, and
snapshot metadata has a one-day maximum lifetime. Complete and verify the
release and security refreshes with alert headroom before any dependent role
expires. A successful one-time bootstrap or refresh is not durable production
readiness: if the manual cadence cannot be maintained, the registry must remain
fail closed until a separately reviewed scheduled path provides equivalent
freshness.

#### Runtime rollout order

Stop if any reviewed plan includes a writer/action service, schedule, edge
resource, broad signer authority, mutable image reference, development project
reference, or unexpected destroy/replace action. After the Owner-removal,
WIF-handoff, and protected-operations gates are verified, enable
production in this order:

1. If the image publisher was pre-provisioned, protect
   `seen-registry-production-image` with required reviewers and no bypass, then
   enable its repository-side publication gate. The master workflow may publish
   and attest an immutable candidate; it never deploys it.
2. Through the separately approved key/secret operations path, create the exact
   concrete online KMS key versions and independently record their four public
   keys. On a clean network-disabled host, generate fresh production root and
   targets keys and complete the ceremony in
   [signing operations](../../registry-service/docs/signing-operations.md). Add
   only the reviewed numeric envelope payload versions through that same bounded
   path; OpenTofu never owns their bytes.
3. Provision the offline bootstrap importer with only
   `production_ceremony_operations=["offline_bootstrap_importer"]`. After the
   saved-plan apply, have the separately protected exact job-operations path
   invoke and verify the one-task, zero-retry job. Then apply a complete
   reconciliation plan with the selector empty so its job, secret access, and
   authority are removed.
4. Repeat that provision, protected invocation, verification, and removal
   sequence for `online_bootstrap`. Review application and signer digests
   independently, pin concrete KMS versions and the immutable `1.root.json`
   SHA-256, and enable the signer JWKS route only for a plan that selects
   signers.
5. Before the initial online metadata expires, separately enable the
   releases/security refresh jobs if production will remain online. They are
   manual, one-task, zero-retry jobs with role-locked signers; this root never
   creates a scheduler. Invoke, exercise, and verify each only through the
   separately protected exact job-operations path before relying on it.
6. After the complete root, targets, delegated, snapshot, and timestamp chain
   verifies, separately provision the root verifier, invoke it through the exact
   protected job-operations path, and remove or retain it according to the
   reviewed plan. Only after successful verification enable the read-only API.
   Review the existing portfolio gateway service account, deploy only the
   emitted two-value gateway configuration, and verify catalog, package, blob,
   and TUF reads. Package publication, archive upload, yank/unyank, quarantine,
   and reinstatement must all remain unavailable.

Leave `production_ceremony_operations` empty in steady state. Any later renewal
or rotation selects exactly one ceremony operation, applies it, has the
separately protected operations path invoke and verify it, then applies another
complete saved plan that removes the transient job and authority.

Production Cloud Run service, signer, and job definitions intentionally do not
use provider deletion protection: their selectors must be reversible so all
refresh authority can be removed before a later isolated ceremony. They are
stateless and remain guarded by the complete saved-plan/apply workflow. The
Firestore database, buckets, KMS keys, and other durable trust/data resources
retain their separate deletion and retention controls.

### Schedule activation gate

This section applies only to a root that explicitly exposes schedules, such as
development. The current production root hard-disables writer workloads,
schedules, and edge resources; enabling any of them requires separately
reviewed code and a new privilege analysis before a production plan.

With `refresh_jobs_enabled=true`, enabling schedules creates five schedules:
source verification, scanning, promotion, releases refresh, and security
refresh. Setting `schedules_paused=false` activates all five; it is not only a
metadata-refresh switch.

First apply `schedules_enabled=true` with `schedules_paused=true`. Review every
schedule's name, UTC cadence, Cloud Run target, scheduler service account,
one-task/zero-retry target job, and IAM changes. Exercise an approved refresh
schedule while paused and verify that its scheduler-originated execution uses
the scheduler service account, completes successfully, and advances a fresh
public TUF chain. Unpausing requires a separate complete plan and explicit
approval naming all five schedules. Verify their enabled state and first
automatic executions before declaring the rollout complete.

Cloud Scheduler omits an all-default retry block from its API response. Keep
`retry_config` absent when the intended policy is the API default of zero
retries; declaring only `retry_count = 0` causes perpetual plan drift without
changing runtime behavior.

### Temporary plan-reader access

A complete plan may require project-level bucket metadata access. If the
reviewed operator lacks it, obtain explicit approval for the exact principal,
project, duration, role name, and permissions. The registry bucket-metadata
plan reader is limited to `storage.buckets.get`,
`storage.buckets.getIamPolicy`, and `storage.buckets.list`; it receives no
object-data access.

Record the temporary binding and role names. After the approved plan/apply
workflow—or after a failure—remove the binding, delete the custom role, remove
copied SDK credential/configuration directories, and verify that the principal
has no remaining binding and the role is deleted. Cleanup failure blocks
completion.

### Local OpenTofu artifacts

Treat backend configuration, non-example variable files, state, saved plans,
rendered plan JSON, and copied cloud configuration as sensitive local
artifacts. Use `umask 077`, save plans as `*.tfplan` in an ignored private
location, review and apply the exact saved plan, then delete the plan and any
rendered output when the operation completes or is abandoned. Never force-add
these files. Confirm expected paths with `git check-ignore -v` before
committing.

For transitional functional testing with `edge_cutover_enabled=false`, the module emits
the exact gateway values:

- `SEEN_REGISTRY_UPSTREAM_URL`
- `SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL`
- `SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL`

That bridge is transitional and does not complete the production rollout.
`edge_provisioned=true` creates a global
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

The environment-gated image-publisher workflow may build/test and publish an
immutable application image after registry changes land on `dev`. It must not
apply this stack, update Cloud Run, select ceremony operations, or change the
separately reviewed signer digest. Development cutover and infrastructure remain
separate reviewed operations. After the production OIDC handoff, production
infrastructure and signer-definition changes use only the protected complete
saved-plan workflow described above; job invocation still requires the separate
protected operations path.

Create that GitHub environment with required reviewers and disallow bypass
before enabling the WIF publisher. Without those repository-side protections,
keep `github_ci_enabled=false`; the cloud-side claim check does not create or
protect the GitHub environment.

The production root can emit a separate publisher only when
`github_ci_enabled=true`. Its provider accepts the same immutable repository and
owner IDs, but only `refs/heads/master`, the exact registry workflow on master,
a `push` event, and the protected `seen-registry-production-image` environment.
The production workflow job is additionally guarded by
`SEEN_REGISTRY_PROD_IMAGE_PUBLISH_ENABLED`. Publication yields an immutable,
attested candidate digest; deployment and signer use still require a separate
complete reviewed plan.

The gateway runtime account must be discovered from the existing service, not
guessed:

```sh
gcloud run services describe portfolio-dev \
  --project portfolio-476219 \
  --region us-central1 \
  --format='value(spec.template.spec.serviceAccountName)'
```

## Gates not hidden by this stack

- The registry image-publisher workflow is limited to validation and immutable
  image publication through its emitted OIDC provider and service account. It
  must not apply infrastructure, update Cloud Run, execute jobs, or change IAM.
- Routine application and signer image inputs remain separate:
  `container_image` and privileged `signer_container_image`. Image-publisher CI
  never applies either input. Before handoff, an explicitly approved direct
  human applies the exact saved plan; after handoff, only the protected
  infrastructure plan/apply identities may apply it. Signer and ceremony
  changes must be called out explicitly, and job execution remains outside the
  infrastructure workflow.
- The runtime image must contain `import-offline-bootstrap` and
  `bootstrap-online` before those ceremony operations can be selected.
- The runtime emits bounded `tuf_signing_rejected` and
  `tuf_metadata_expiry_breach` text events consumed by the exact log-metric
  filters. The foundation also enables Cloud KMS `DATA_READ` audit logs so the
  unexpected-`AsymmetricSign` metrics receive the signing events they monitor.
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
