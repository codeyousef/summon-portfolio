# Development import runbook

The development root intentionally names the existing `portfolio-476219`
resources. Import them before the first apply; do not allow OpenTofu to replace
or duplicate them.

Copy the example backend and variable files to ignored local files, authenticate
with a reviewed infrastructure identity, and initialize:

```sh
cp infra/registry/environments/dev/backend.hcl.example \
  infra/registry/environments/dev/backend.hcl
cp infra/registry/environments/dev/terraform.tfvars.example \
  infra/registry/environments/dev/terraform.tfvars
tofu -chdir=infra/registry/environments/dev init -backend-config=backend.hcl
```

Do not put secret payloads or offline ceremony material in either file. Numeric
secret version references and public hashes/keys are safe inputs.

## Existing foundation

```sh
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_artifact_registry_repository.registry[0]' \
  'projects/portfolio-476219/locations/us-central1/repositories/seen-registry'

tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_firestore_database.registry[0]' \
  'projects/portfolio-476219/databases/seen-registry-dev'

tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_storage_bucket.registry["quarantine"]' \
  'portfolio-476219-seen-registry-dev-quarantine'
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_storage_bucket.registry["public"]' \
  'portfolio-476219-seen-registry-dev-blobs'
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_storage_bucket.registry["metadata"]' \
  'portfolio-476219-seen-registry-dev-metadata'

tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_kms_key_ring.registry[0]' \
  'projects/portfolio-476219/locations/us-central1/keyRings/seen-registry-dev'
```

Import all four online keys:

```sh
for role in releases security snapshot timestamp; do
  tofu -chdir=infra/registry/environments/dev import \
    "module.registry.google_kms_crypto_key.online[\"${role}\"]" \
    "projects/portfolio-476219/locations/us-central1/keyRings/seen-registry-dev/cryptoKeys/seen-registry-dev-${role}"
done
```

The `private`, `evidence`, and `backup` buckets and all networking resources are
new. Do not import them.

## Identities

Do not import the legacy runtime, promoter, security, source, or scanner
accounts. The root deliberately creates fresh v2 accounts so ordinary CI
`actAs` cannot inherit a legacy direct-KMS or broad bucket grant. Revoke the old
accounts only after the new path passes its drills.

The scheduler is also a fresh `seen-reg-dev-scheduler-v2` account. Do not
import the legacy `seen-registry-dev-scheduler` identity.

## Candidate Cloud Run resources

Do not import or update any live Cloud Run service or job. The root creates
distinct v2 candidates:

- `seen-registry-dev-api-v2`
- `seen-registry-dev-release-actions-v2`
- `seen-registry-dev-security-actions-v2`
- `seen-registry-dev-source-verify-v2`
- `seen-registry-dev-scan-v2`
- `seen-registry-dev-promote-v2`
- `seen-registry-dev-root-verify-v2`

This preserves `seen-registry-dev` and every legacy job as rollback/audit
evidence through gateway, edge, and E2E testing. The old combined bootstrap job
cannot be imported into either split-authority job. Disable and delete legacy
resources only through a separately reviewed cleanup after cutover. The
existing workflow does not create schedules; if a live schedule is discovered,
pause it before any apply.

## Existing secret containers

Import containers only. OpenTofu must never import or create a
`google_secret_manager_secret_version`.

The development root intentionally matches the live immutable replication
mode: the publisher token and bootstrap root/targets envelopes use a
user-managed `us-central1` replica; the other existing and new containers use
automatic replication. Stop if a plan proposes replacement of any imported
secret.

```sh
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_secret_manager_secret.registry["publisher_token"]' \
  'projects/portfolio-476219/secrets/seen-registry-dev-publisher-token'
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_secret_manager_secret.registry["trust_and_safety_token"]' \
  'projects/portfolio-476219/secrets/seen-registry-dev-trust-and-safety-token'
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_secret_manager_secret.registry["security_token"]' \
  'projects/portfolio-476219/secrets/seen-registry-dev-security-token'
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_secret_manager_secret.registry["github_app_id"]' \
  'projects/portfolio-476219/secrets/seen-registry-dev-github-app-id'
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_secret_manager_secret.registry["github_app_private_key"]' \
  'projects/portfolio-476219/secrets/seen-registry-dev-github-app-private-key'
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_secret_manager_secret.registry["bootstrap_root_envelope"]' \
  'projects/portfolio-476219/secrets/seen-registry-dev-root-envelope'
tofu -chdir=infra/registry/environments/dev import \
  'module.registry.google_secret_manager_secret.registry["bootstrap_targets_envelope"]' \
  'projects/portfolio-476219/secrets/seen-registry-dev-targets-envelope'
```

Import the GitLab token container only if it exists. Let the first apply create
missing empty containers and the new ceremony containers.

## Required review before apply

Run a refresh-only plan and a normal plan:

```sh
tofu -chdir=infra/registry/environments/dev plan -refresh-only
tofu -chdir=infra/registry/environments/dev plan
```

Stop if the plan replaces an imported resource, changes a key algorithm, deletes
an object/bucket/key/secret, unpauses schedules, or grants an ordinary CI
identity access to a signer/importer account. The first workload plan should
update imported resources in place to fresh v2 identities.

Inventory legacy IAM separately. In particular, remove any old
`roles/cloudkms.signerVerifier`, project-wide KMS signer, bucket object-admin,
root/timestamp overwrite, unauthenticated Cloud Run invoker, or broad
service-account-user grants after their scoped replacements are present.
Re-run the plan and IAM audit before enabling workloads.

The live project also carries Google-created legacy bucket basic-role bindings.
After scoped replacements exist, remove `projectEditor`/`projectOwner`
`roles/storage.legacyBucketOwner` and `projectViewer`
`roles/storage.legacyBucketReader` from all three imported registry buckets.
Do not remove them from unrelated portfolio buckets.

Before giving the CI identity any continued role, revoke its project-wide
`roles/iam.serviceAccountUser`, `roles/iam.serviceAccountTokenCreator`,
`roles/run.admin`, `roles/storage.admin`, and `roles/storage.objectAdmin`
bindings. Do not remove project Editor from the default compute service account
while unrelated legacy services still run as that identity. Removing the
registry buckets' project basic-role convenience bindings prevents that Editor
grant from conferring registry object authority; migrate those unrelated
services to dedicated runtime identities before removing Editor separately.
Capture before/after IAM policies and verify effective access. Delete the
workflow's static `GCP_SA_KEY` only after the emitted Workload Identity provider
and image-publisher service account authenticate successfully. The module
grants that OIDC identity only repository-scoped Artifact Registry writer; it
receives no runtime `actAs`, public-key, signer, data, or apply authority. An
optional separately controlled `reviewer_service_account` receives only KMS
public-key viewer on the four exact online keys.

Before setting `github_ci_enabled=true`, create and protect the exact GitHub
environment `seen-registry-development-image` with required reviewers and no
bypass. The provider also verifies immutable repository/owner IDs, the `dev`
ref, exact registry workflow, `push` event, and that environment claim. Keep the
old key available only for rollback until one OIDC image push succeeds, then
delete the repository secret and revoke/delete the old service-account key.
