# Seen registry service

An isolated Kotlin/JVM service image for the staged Seen package registry. The
development environment can deploy separate public API, review worker,
operation-specific coordinator, ephemeral importer, and private role-locked
signer workloads. Production initially deploys only the credential-free
read-only public API plus separately gated trust-maintenance jobs. The service
uses Aether for HTTP, Summon for the public catalog, Firestore for records, and
GCS for quarantine/public objects and signed metadata. Only a role-locked
signer workload can open one online KMS key version; local Ed25519 keys are
development-test only.

The `opaque-dev` writer mode is deliberately temporary and internal-only. It is
rejected outside `development`; `REGISTRY_WRITERS_ENABLED` defaults to `false`.
Public account writers are not implemented. The serving process has no
promotion or signing path: dedicated source-verification, isolated-scanner,
and operation-specific coordinator jobs advance only the exact evidence-bound
release state.

Run tests with the repository wrapper:

```bash
GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=768m -Dorg.gradle.workers.max=2' \
  ../gradlew -p . test --no-daemon --max-workers=2
```

Required deployment configuration is documented by `RegistryConfig` and
validated at startup. The service request body limit is 25 MiB plus one byte so
oversized archive uploads are rejected before materialization.
Release upload reservations last 25 hours so the contract's full 24-hour
byte-exact idempotency replay window always returns usable upload instructions.

## Production read-only mode

`serve-read-only-public-api` is accepted only with
`REGISTRY_ENVIRONMENT=production`. The managed production root supplies the
isolated repository ID `seen-prod-registry-v1` and origin
`https://seen.yousef.codes/packages`. The mode rejects publisher,
trust-and-safety, security-action, quarantine, signer URL, private-key, and KMS
key-version configuration at startup.

This mode exposes only health, catalog, package/release/source-proof, download,
blob, and signed-metadata reads. The repository layer filters out private and
security-quarantined releases; only public available or yanked releases are
readable. Package creation, release reservation, archive upload/completion,
yank/unyank, report/appeal review, quarantine, and reinstatement routes are not
installed. The runtime receives read-only Firestore, public-object, and
metadata-object authority and has no signer invocation or metadata write path.

Production infrastructure is inert by default and retains no writer, action,
or schedule selector. Follow the phased saved-plan, trust-ceremony, gateway,
and verification gates in [registry infrastructure](../infra/registry/README.md#production-read-only-rollout).

## Development owner bootstrap

The GitHub deployment identity intentionally cannot create service accounts or
rewrite project IAM. An authenticated project owner performs the idempotent
one-time bootstrap instead:

```bash
GCP_PROJECT_ID=portfolio-476219 \
  ./registry-service/scripts/provision-development-owner.sh
```

The bootstrap/deployment pair must create distinct identities for the public
API, source verifier, scanner, release coordinator, security coordinator,
ephemeral targets/root importers, and four private role-locked signer services.
Each signer service account can use exactly one online Ed25519 KMS key and read
the public metadata chain. Releases, security, and snapshot signers are
read-only; only the timestamp signer can generation-match replace
`timestamp.json`. Only the ephemeral root importer can generation-match
replace `root.json`.

Coordinators receive signer URLs, pinned public keys, and exact signer-invoker
grants for their operation, never key versions or direct KMS permissions. The
API, source verifier, and scanner receive no signer URL, signer-invoker grant,
or KMS permission. Ordinary CI can publish an immutable image, read public key
material, and produce a reviewed plan; it cannot act as a signer or ceremony
identity. Trust-plane deployment requires a separate protected operator
approval.

It does not create offline keys, TUF envelopes, the publisher token, or GitHub
App/GitLab credential values. Add the read-only GitHub App ID and PEM key out of
band before deployment. Leave `REGISTRY_ENABLE_REVIEW_SCHEDULES` unset in
OpenTofu-managed environments: the legacy owner helper must not create or
update schedules. Provision and activate the v2 schedules only through the
staged gate documented in [registry infrastructure](../infra/registry/README.md#schedule-activation-gate).
The deployment workflow verifies every pre-provisioned dependency and fails
closed instead of escalating its own permissions.

## Signing boundary

The complete public ceremony, custody, rotation, recovery, audit, and drill
procedure is in [Signing operations](docs/signing-operations.md).

`describe-signing-policy` prints the machine-checkable deployment policy for
`REGISTRY_ENVIRONMENT` without loading any secret or private-key configuration.
Root is offline 2-of-3, targets is
offline 2-of-2, and releases/security/snapshot/timestamp each use a distinct
private signer service, service account, and online key.

Coordinators authenticate with a Google-signed OIDC ID token for the receiving
service audience. Each signer independently verifies the Google issuer,
audience, verified caller email, and operation mapping before validating the
pinned sequential root chain and committed/staged metadata. The allowed
operations are `release`, `security`, `bootstrap`, `targets-renewal`,
`targets-rotation:releases`, and `targets-rotation:security`; each maps only to
the roles listed in the public signing runbook.

The offline host runs:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar prepare-offline-bootstrap ./ceremony-output
```

It reads root/targets public keys and PKCS#8 signers from
`REGISTRY_OFFLINE_ROOT_*` and `REGISTRY_OFFLINE_TARGETS_*`, plus only the four
`REGISTRY_KMS_<ROLE>_PUBLIC_KEY_HEX` values. It writes canonical `1.root.json`,
`root.json`, and `1.targets.json`; it never contacts GCP.

An ephemeral bootstrap import imports those pre-signed bytes through
`REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64` and
`REGISTRY_BOOTSTRAP_TARGETS_ENVELOPE_BASE64`. Import re-verifies the signatures,
thresholds, role topology, key IDs, online public-key bindings, environment,
repository, origin, canonical encoding, and expiry ceilings before persisting.
Only the root-import identity can create `root.json`. The bootstrap coordinator
then creates immutable delegated and snapshot metadata through the four
operation-mapped signer services; the timestamp signer alone conditionally
creates `timestamp.json`. Offline private keys are rejected in GCP
configuration.

## Renewing offline targets

Renew top-level targets before its 30-day expiry. Copy the currently trusted
`root.json` and the versioned `N.targets.json` referenced by the live
timestamp/snapshot transaction to the offline host, then run:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  prepare-offline-targets-renewal ./root.json ./N.targets.json ./ceremony-output
```

This command uses the two `REGISTRY_OFFLINE_TARGETS_SIGNING_KEYS_PKCS8_BASE64`
signers and only the four configured online **public** keys. It writes canonical
`N+1.targets.json`, preserving the existing environment, repository, empty
top-level target set, delegation policy, and releases/security keys byte for
byte; only `version` and `expires` change.

Transfer that one envelope to the deployment environment and run the one-shot
import:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  import-offline-targets-renewal ./N+1.targets.json
```

Import re-verifies the live root and timestamp/snapshot chain, both offline
signatures, exact sequential version, unchanged policy, and an expiry no more
than 30 days away. It refuses rollback, replay, tampering, and overwrite. The
ephemeral targets importer creates the immutable targets candidate, invokes
only the snapshot and timestamp signers for the `targets-renewal` operation,
and creates the immutable snapshot. The timestamp signer reloads and validates
the full candidate chain and alone generation-match replaces `timestamp.json`.
Never provision a targets private key in the online runtime.

For GCP, run the importer as a one-task Cloud Run job built from the deployed
registry image under its dedicated ephemeral importer identity. Give it only
read access to committed metadata, create-if-absent access to the required
immutable objects, and invocation access to the snapshot and timestamp signer
services. It must not receive a key version, direct KMS permission, mutable
timestamp permission, API/review credentials, or offline signing material.
Mount the transferred envelope read-only, set `--max-retries=0`, and delete the
job plus transfer input after success. A completed import is not replayable; an
exact envelope already stored by an interrupted import may be retried only
while the live snapshot still references the preceding targets version.
