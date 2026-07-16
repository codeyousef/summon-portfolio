# Seen development registry service

An isolated Kotlin/JVM service for the staged Seen package registry. It uses
Aether for HTTP, Summon for the public catalog, Firestore for records, GCS for
quarantine/public objects, and Cloud KMS or a local Ed25519 key for TUF signing.

The `opaque-dev` writer mode is deliberately temporary and internal-only. It is
rejected outside `development`; `REGISTRY_WRITERS_ENABLED` defaults to `false`.
Public account writers are not implemented. Promotion defaults to disabled and
cannot be enabled with GCP storage until scanner evidence is integrated.

Run tests with the repository wrapper:

```bash
GRADLE_OPTS=-Xmx4g ../gradlew -p . test --max-workers=2
```

Required deployment configuration is documented by `RegistryConfig` and
validated at startup. The service request body limit is 25 MiB plus one byte so
oversized archive uploads are rejected before materialization.
Release upload reservations last 25 hours so the contract's full 24-hour
byte-exact idempotency replay window always returns usable upload instructions.

## Development owner bootstrap

The GitHub deployment identity intentionally cannot create service accounts or
rewrite project IAM. An authenticated project owner performs the idempotent
one-time bootstrap instead:

```bash
GCP_PROJECT_ID=portfolio-476219 \
  ./registry-service/scripts/provision-development-owner.sh
```

The script creates only the development runtime identity, named Firestore
database, three private buckets, isolated image repository, and four online
Ed25519 KMS keys. It grants bucket, database, signing, image-push, and
service-account-use permissions at the narrowest supported resource scope.
It does not create offline keys, TUF envelopes, publisher tokens, or Secret
Manager values. The deployment workflow verifies these pre-provisioned
resources and fails closed instead of escalating its own permissions.

## Signing boundary

`describe-signing-policy` prints the machine-checkable deployment policy without
loading runtime configuration. Root is offline 2-of-3, targets is offline
2-of-2, and releases/security/snapshot/timestamp use four distinct online KMS
keys.

The offline host runs:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar prepare-offline-bootstrap ./ceremony-output
```

It reads root/targets public keys and PKCS#8 signers from
`REGISTRY_OFFLINE_ROOT_*` and `REGISTRY_OFFLINE_TARGETS_*`, plus only the four
`REGISTRY_KMS_<ROLE>_PUBLIC_KEY_HEX` values. It writes canonical `1.root.json`,
`root.json`, and `1.targets.json`; it never contacts GCP.

Deployment imports those pre-signed bytes through
`REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64` and
`REGISTRY_BOOTSTRAP_TARGETS_ENVELOPE_BASE64`. Import re-verifies the signatures,
thresholds, role topology, key IDs, KMS public-key bindings, environment,
repository, origin, canonical encoding, and expiry ceilings before persisting.
Offline private keys are rejected in GCP configuration.

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
than 30 days away. It refuses rollback, replay, tampering, and overwrite, then
publishes a complete releases/security/snapshot/timestamp transaction pointing
at the new immutable targets version. Never provision a targets private key in
the online runtime.

For GCP, run the importer as a one-task Cloud Run job built from the deployed
registry image, with the same runtime service account, registry/KMS environment,
and database/bucket settings as the service. Mount the transferred envelope as
a read-only Secret Manager file, pass that file path to the command, set
`--max-retries=0`, and delete the job plus the short-lived envelope secret after
success. Do not give the job any offline signing material. A completed import is
not replayable; an exact envelope already stored by an interrupted import may be
retried only while the live snapshot still references the preceding targets
version.
