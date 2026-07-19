# Seen registry signing operations

This public runbook defines the operational boundary for the Seen registry's
TUF-compatible signing system. It contains no private key material, secret
values, custodian identities, recovery locations, or incident contacts. Those
details belong in the private security record linked from the active incident
or ceremony ticket.

The fixed signing policy is machine-readable without loading runtime secrets:

```bash
REGISTRY_ENVIRONMENT=production \
  java -jar seen-registry-service.jar describe-signing-policy
```

The policy is root 2-of-3 and top-level targets 2-of-2 offline, with
distinct one-key online roles for releases, security, snapshot, and timestamp.
Production must use separate keys, identities, storage, and trust roots; never
promote development key material.

## Non-negotiable boundary

| Role | Threshold | Signing authority | Metadata writes |
| --- | ---: | --- | --- |
| root | 2 of 3 | offline only | an ephemeral root importer alone creates the immutable next root and generation-match replaces `root.json` |
| targets | 2 of 2 | offline only | an ephemeral targets importer creates only the immutable next targets object |
| releases | 1 of 1 | one private role-locked signer service and service account | coordinator creates immutable delegated metadata; signer is read-only |
| security | 1 of 1 | one private role-locked signer service and service account | coordinator creates immutable delegated metadata; signer is read-only |
| snapshot | 1 of 1 | one private role-locked signer service and service account | coordinator creates immutable snapshot metadata; signer is read-only |
| timestamp | 1 of 1 | one private role-locked signer service and service account | timestamp signer alone generation-match replaces `timestamp.json` |

Each online signer service has exactly one role, one service account, and one
key. It has read-only access to public TUF metadata so it can authorize a
candidate from trusted state. Releases, security, and snapshot signers cannot
write metadata. The timestamp signer has only the narrow conditional write
needed for `timestamp.json`; it cannot create delegated or snapshot objects.

Coordinators never receive key versions or direct signing permission. They may
read the committed chain, create role-appropriate immutable candidates, and
invoke only the signer services required by their operation. The public API,
source verifier, and scanner receive no signer URL, signer invocation grant,
key version, or signing permission. They verify signed metadata and fail
closed. No online service or job receives an offline private key.

The bootstrap, targets, and root importers are ephemeral. They receive only the
signed envelope needed for that operation and are deleted after use. Only the
root importer can replace `root.json`; no online signer or ordinary
coordinator can do so.

A project administrator remains a control-plane trustee because an
administrator can rewrite IAM. The infrastructure policy gate must evaluate
key, key-ring, project, folder, and organization allows and denies;
service-account impersonation; groups and custom roles; each deployed
revision's identity, environment names, secrets, volumes, signer-invocation
bindings, metadata permissions, and enabled key versions. Unknown or
incomplete ancestor visibility fails the gate. The gate runs before traffic is
shifted, never reads secret payloads or requests signatures, and stores only
safe decision evidence.

## Authenticated signing and transaction commit

Every coordinator requests a Google-signed OIDC ID token for the receiving
signer service's configured audience. Cloud Run IAM must allow that caller to
invoke the private service, and the signer independently verifies the token's
Google signature, issuer, audience, verified email, and exact
operation-to-caller mapping. A valid token for one operation or service is not
authority for another.

The allowed operation mappings are:

| Operation | Signer roles |
| --- | --- |
| `release` | releases, snapshot, timestamp |
| `security` | security, snapshot, timestamp |
| `bootstrap` | releases, security, snapshot, timestamp |
| `targets-renewal` | snapshot, timestamp |
| `targets-rotation:releases` | releases, snapshot, timestamp |
| `targets-rotation:security` | security, snapshot, timestamp |

Before any signature, every signer verifies the pinned digest of
`1.root.json`, the complete sequential root chain through `root.json`, the
current committed timestamp/snapshot/targets/delegated chain, environment,
repository, canonical encoding, expiry, monotonic version, and the invariants
for the declared operation.

An online transaction commits in this order:

1. The coordinator asks the affected delegated signer to authorize and sign
   canonical metadata, locally verifies the returned signature, then creates
   the immutable versioned delegated object.
2. The snapshot signer reads and verifies the pinned root and committed chain,
   the staged target/delegated objects, their hashes and signatures, the
   monotonic transaction version, and preservation of the opposite delegated
   role. After signing, the coordinator creates the immutable versioned
   snapshot object.
3. The timestamp signer reloads and validates the full candidate chain,
   authorizes and signs the timestamp, reloads the state again, and alone
   generation-match replaces `timestamp.json`. A generation conflict fails the
   transaction without overwrite.
4. The coordinator reads back `timestamp.json` and verifies the complete
   committed chain. It never writes the mutable timestamp pointer.

Staged immutable objects are not published state until the timestamp signer
commits their exact snapshot. They may be safely reused only when byte
identical; a different object at an existing version is forbidden.

## Custody and recovery copies

Each offline signer is generated on a clean, network-disabled ceremony host
from the operating system CSPRNG. Never generate two signers from the same seed
or copy one signer to satisfy two threshold positions.

For every root and targets signer:

1. Assign one primary encrypted removable device to one custodian. A custodian
   may not hold enough keys to satisfy either threshold alone.
2. Create one independently encrypted recovery copy on different media. Store
   it in a documented geographic region separate from the primary and from the
   other quorum members.
3. Keep device-unlock and recovery credentials outside the device, registry
   repository, CI, password variables, GCP, and the public ceremony record.
   Recovery access requires two-person approval recorded privately.
4. Record only the public key ID, media inventory ID, custodian role, region,
   creation date, and last recovery test in the private custody inventory.
5. Test one recovery copy per quarter on an offline host, rotating which copy
   is tested. Verify its derived public key ID, then securely erase the test
   host's working copy.

Loss, unexplained access, failed inventory reconciliation, or a broken tamper
seal is a suspected compromise, not merely a missing backup.

## Ceremony controls

Every ceremony needs a private ticket, an operator, a second operator, and an
observer. The ticket records the purpose, approved role/version transition,
expected thresholds, participants, and rollback/fail-closed decision. Never
paste private material into the ticket, terminal transcript, chat, or logs.

Before signing:

1. Boot the known-clean ceremony image with networking physically disabled.
   Confirm the clock from two independent references before disconnecting.
2. Verify the registry JAR digest against the reviewed build digest through two
   independent channels. Record the JAR digest and source revision.
3. Scan transfer media, mount inputs read-only, and copy only public online-key
   inputs plus already-public trusted metadata onto the host.
4. Independently verify environment, repository ID, registry origin, current
   versions, expiry, key IDs, and requested new public keys.
5. Load only the minimum quorum for the role being signed. Stop if an
   unexpected key, version, delegation, target, threshold, or expiry appears.

After signing, each operator independently verifies canonical bytes,
signatures, thresholds, version increments, environment/repository/origin, and
expiry. Transfer only signed metadata. Record its filename, length, SHA-256,
version, expiry, and signing key IDs. Power down and securely erase working
copies before returning primary media to custody.

## Production launch boundary

The initial production launch serves only credential-free public reads through
the authenticated portfolio gateway. It has no package writer, archive upload,
source-verification, scanning, promotion, release/security action, scheduler,
or edge-cutover surface. The only valid production trust identity is:

- environment: `production`;
- repository ID: `seen-prod-registry-v1`;
- registry origin: `https://seen.yousef.codes/packages`.

Generate a fresh three-key root set and fresh two-key targets set for this
identity on the network-disabled ceremony host. Production must also use four
online keys created in its isolated project. A development root, targets file,
envelope, online key version, object, state file, or root digest is never a
production input, even for a temporary smoke test.

Infrastructure starts inert. Each foundation, image-publication, ceremony,
refresh, verifier, and read-only API phase needs a complete saved OpenTofu plan,
independent review, explicit approval, and application of that exact saved
plan. During bootstrap, select only one ephemeral ceremony operation at a time.
After executing and verifying it, apply another complete saved plan with the
selector empty to remove the job and all of its temporary secret, metadata, and
signer-invocation authority. The exact rollout order and plan controls are in
[Seen registry infrastructure](../../infra/registry/README.md#production-read-only-rollout).

The production metadata-refresh jobs are a later, separately gated surface.
They invoke only their role-locked signers, run one task with zero retries, and
have no scheduler. They do not add a package writer or action endpoint.

## Initial root and targets ceremony

First apply only the isolated environment foundation. Leave all application,
refresh, ceremony, and schedule selectors false. From the reviewed concrete KMS
versions, retrieve and independently verify the four online **public** keys for
releases, security, snapshot, and timestamp. Record their project, key, version,
public-key bytes, and key IDs as public ceremony inputs; an image build or CI
artifact is not the source of trust for those values.

On the offline host, set `REGISTRY_ENVIRONMENT` and
`REGISTRY_REPOSITORY_ID` to the exact approved identity, configure three fresh
root public keys and signers, two fresh targets public keys and signers, and the
four reviewed online public keys. Record and independently verify the exact
registry origin that the online importer will enforce. Run:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  prepare-offline-bootstrap ./ceremony-output
```

The output must contain canonical `1.root.json`, `root.json`, and
`1.targets.json`. Verify root is signed by at least 2 of its 3 keys, targets by
both targets keys, and that the role topology and online public keys match the
reviewed KMS evidence. Configure only the signed envelopes as numeric-versioned
one-shot import inputs; never select `latest`.

In the first saved-plan phase, the ephemeral offline bootstrap importer verifies
the exact environment, repository, origin, thresholds, key bindings, and
canonical bytes, creates the immutable version-one root and targets objects,
and conditionally creates `root.json`. Remove that job and authority after
read-back verification. In a new saved-plan phase, the online bootstrap
coordinator invokes the four operation-mapped signer services, creates the
immutable delegated and snapshot objects, and waits for the timestamp signer to
commit `timestamp.json`. Neither identity receives an online private key
version; only the root importer can write `root.json`, and only the timestamp
signer can write `timestamp.json`.

After read-back verification, confirm the transfer inputs and ephemeral jobs
are deleted, all temporary access is gone, and the pre-traffic policy gate
passes. Never use `latest` for ceremony inputs.

## Expiry and routine renewal

Metadata lifetimes are bounded to root 365 days, targets 30 days,
releases 7 days, snapshot 1 day, and security/timestamp 6 hours. Operation-
specific coordinators refresh online roles before expiry through their private
signer services. Alerts should page at 90/60/30
days for root, 14/7/3 days for targets, and before the online refresh window is
exhausted. Alerts are not authority to extend an expiry or reduce a threshold.

For normal top-level targets renewal, obtain the currently trusted `root.json`
and the exact versioned `N.targets.json` referenced by the live
timestamp/snapshot chain. On the offline host, verify both hashes and run:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  prepare-offline-targets-renewal \
  ./root.json ./N.targets.json ./ceremony-output
```

Both targets signers are required. The candidate may change only version and
expiry. Import `N+1.targets.json` with a one-shot job using:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  import-offline-targets-renewal ./N+1.targets.json
```

The ephemeral targets importer receives the signed envelope and exact
snapshot/timestamp signer endpoints, never a key version or private key. It
creates the immutable targets and snapshot candidates; the timestamp signer
alone commits `timestamp.json`. Set one task, zero retries, and delete the job
and transfer input after success. Confirm the committed snapshot references the
exact candidate and clients accept the complete chain. Reruns must either be
byte-identical and resume a safe interrupted transaction or fail without
overwrite.

### Development recovery after both delegated roles expire

Routine refresh remains strict: a releases coordinator will not retain stale
security metadata, and a security coordinator will not retain stale releases
metadata. If and only if **both** delegated roles are already expired while the
offline root and top-level targets remain valid, development has an explicit
two-transaction recovery path. Run the role-separated releases job once with:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  recover-expired-releases-once
```

This command holds releases, snapshot, and timestamp authority only. It
retains the signed security envelope byte-for-byte and commits fresh releases,
snapshot, and timestamp metadata. The committed intermediate transaction is
still expired: readiness and clients must continue to reject it. Immediately
complete recovery through the ordinary security job:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  refresh-security-once
```

The symmetric `recover-expired-security-once` followed by
`refresh-releases-once` is supported, but never run both recovery commands.
After the first recovery transaction, only one delegated role remains expired,
so another recovery command must fail. The commands reject fresh or partially
expired state, expired root or targets, altered retained metadata, repository
state mismatches, and any runtime that receives the opposite delegated signer.
The coordinator uses the existing role-specific signing operation, and every
delegated, snapshot, and timestamp signer independently reloads the committed
chain: if the opposite retained role is expired, the signer authorizes the
transaction only when both delegated roles are already expired. Thus a faulty
coordinator cannot use routine publication to retain a single stale opposite
role. The timestamp signer repeats this check immediately before its
generation-matched commit.
They do not add an unsigned mode, extend an expiry, combine releases and
security authority, or make the intermediate transaction client-acceptable.
Verify the final complete chain before restoring service readiness.

## Key rotation and threshold change

All changes are monotonic. Never overwrite a versioned root or targets file.
Retain old public metadata indefinitely so clients can update sequentially.

### Releases or security delegated key

Create a replacement key for the affected role's private signer service. Only
that role's signer service account may use it. Use the current trusted root and
targets plus the replacement online public key in the offline command:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  prepare-offline-targets-rotation \
  ./root.json ./N.targets.json ./ceremony-output
```

The candidate must be `N+1`, preserve the top-level target set and unaffected
delegations byte-for-byte, and replace only approved delegated key material.
The command reads the replacement releases/security public keys from
`REGISTRY_KMS_RELEASES_PUBLIC_KEY_HEX` and
`REGISTRY_KMS_SECURITY_PUBLIC_KEY_HEX`. Its snapshot/timestamp public-key
inputs must still match the trusted root; a targets rotation cannot change a
root-authorized online role.
Import it through the one-shot path:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  import-offline-targets-rotation releases ./N+1.targets.json
```

Use `security` instead of `releases` only for a security-key incident. One
transition changes exactly one delegated role. The releases importer can
invoke only releases, snapshot, and timestamp signers and must retain the
committed security envelope; the security importer can invoke only security,
snapshot, and timestamp signers and must retain the committed releases
envelope. Freeze ordinary publication while importing.

The role-specific importer receives signer endpoints and pinned public keys,
not key versions. It creates the immutable targets, affected delegated, and
snapshot candidates. Each signer validates the pinned root, committed chain,
staged objects, caller email, and rotation operation; the timestamp signer
alone commits the pointer. Rerun the infrastructure policy checks and verify
the committed transaction with a client before disabling the old signer key.
Do not add a temporary signing principal or broaden a key or invocation binding
during rotation.

### Root key

Prepare `N+1.root.json` on the offline host. The current root quorum is provided
through `REGISTRY_OFFLINE_ROOT_SIGNING_KEYS_PKCS8_BASE64`; the three replacement
public keys and replacement signing quorum use
`REGISTRY_OFFLINE_NEXT_ROOT_PUBLIC_KEYS_HEX` and
`REGISTRY_OFFLINE_NEXT_ROOT_SIGNING_KEYS_PKCS8_BASE64`:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  prepare-offline-root-rotation \
  ./N.root.json ./ceremony-output
```

The new root must be signed by the old root threshold and by the new root
threshold. The fixed-policy helper requires both policies at 2-of-3 and rejects
changes to targets, snapshot, timestamp, environment, repository, origin, or
expiry policy. All three replacement root keys must be fresh, mutually
distinct, and distinct from every currently trusted role key. Do not remove an
old signer until the dual-threshold envelope exists and has been independently
verified. This helper deliberately performs a full three-key root-set rotation;
it is not a single-key replacement command.

Import only the next root version:

```bash
java -jar seen-registry-service-0.1.0-dev-all.jar \
  import-offline-root-rotation ./N+1.root.json
```

Run this only as the ephemeral root importer. It verifies the old and new
thresholds, creates the immutable version, and is the sole identity allowed to
generation-match replace `root.json`. It has no online signer invocation or
timestamp write permission. Public metadata reads expose the new root only
after that pointer advances. If the pointer condition loses, retry only the
byte-identical candidate; a different candidate at the same version is
forbidden. Every deploy and every online signer verifies the pinned
`1.root.json` digest and complete sequential chain through the current pointer.

### Targets authority, snapshot/timestamp key, or threshold policy

These changes are also authorized by root metadata, but the fixed-policy root
rotation helper intentionally cannot perform them. Freeze the affected role and
prepare a separately reviewed, dual-threshold next-root transition: the old
root threshold authorizes it and the candidate satisfies its own new root
threshold. A threshold change must follow the same rule; never lower a
threshold to work around missing custody. A targets-authority transition is
followed by new top-level targets signed under the authority named by the new
root.

Snapshot/timestamp compromise requires that root-authorized public-key
transition, deployment of the replacement role-locked signer, and a fresh
complete online transaction. Exercise the dedicated transition tooling and IAM changes
in the isolated infrastructure-as-code environment drill before relying on
them for production. If the reviewed transition tooling is unavailable during
an incident, remain fail-closed; do not hand-edit or online-sign root metadata.

## Revocation and emergency compromise

Declare an incident immediately when compromise is suspected. Freeze affected
publication, preserve Cloud Audit Logs, and record the last known-good root,
targets, timestamp, object generations, and client observations. Never delete
evidence or silently republish the same version.

- **One offline root key:** if the adversary cannot satisfy the old threshold,
  perform the helper's full three-key-set rotation with the uncompromised old
  quorum and dual-sign the next root. If
  the old root threshold may be compromised, the repository trust is lost;
  publish an incident notice and require out-of-band client rebootstrap.
- **One targets key:** because the targets policy is 2-of-2, freeze delegated changes,
  rotate the targets authority through a dual-threshold root update, then
  publish newly authorized targets. Do not lower the threshold as a shortcut.
- **Releases or security:** disable the affected signer key and invocation
  path, then perform the offline delegated-key rotation. Security compromise
  also freezes quarantine/reinstatement publication.
- **Snapshot or timestamp:** disable the affected signer key and invocation
  path, keep serving the last verified chain only while it remains valid,
  perform the root-authorized rotation, and publish a fresh complete
  transaction.

If a safe transition cannot finish before metadata expires, the registry and
clients remain fail-closed. There is no unsigned mode, expiry bypass, emergency
single signer, or online root fallback.

## Offline loss, grace, and client recovery

Unavailable offline custodians do not stop already-valid online publication,
but they do stop root/targets renewal and rotation. The operational grace
period is therefore the remaining signed lifetime, not extra time after
expiry. Escalate at the alert thresholds, recover a tested backup under
two-person control, and complete the normal ceremony. After root or targets
expiry, readiness and clients must reject the chain until a correctly
authorized sequential update is available.

Clients update root one version at a time: a client trusting root `N` fetches
`N+1.root.json`, verifies it with the threshold from root `N`, then verifies it
against the threshold declared in `N+1`. It repeats without skipping versions
before accepting timestamp, snapshot, targets, or delegated metadata. Keep all
versioned roots publicly available and test from the oldest supported root.

Out-of-band rebootstrap is allowed only when the client has no trusted root,
the trusted root is irrecoverably expired without an authorized successor, or
the old root threshold may be compromised. Distribute the replacement root
and its SHA-256 through two independent authenticated channels. The user must
explicitly approve the trust reset; do not make it an automatic network
fallback.

## Logs and evidence

Retain Cloud Audit Logs for signer-service KMS operations, KMS/IAM
administration, ephemeral import execution, signer invocation, and
metadata-bucket reads and writes. Alert on an unverified or unexpected caller
email/operation mapping, any signer invocation by the API/source/scanner,
releases/security/snapshot attempts to mutate metadata, any non-timestamp
attempt to replace `timestamp.json`, any non-root-import attempt to replace
`root.json`, offline-material environment names, a persistent import job, key
disable/destroy actions, and IAM changes to signing resources.

Each ceremony or drill evidence record contains:

- ticket and reviewed source revision;
- purpose, environment, start/end UTC, operator/observer roles;
- old/new metadata versions, expiry, filenames, byte lengths, SHA-256, public
  key IDs, and the private key-inventory evidence reference;
- threshold and invariant verification results;
- IAM policy-check result and workflow run URL;
- client verification from the previous trusted root and fresh-install root;
- rollback/fail-closed observations and cleanup confirmation.

Never retain environment dumps, shell tracing, private key bytes, unlock
credentials, Secret Manager payloads, access tokens, or decrypted working
directories as evidence.

## Development drill

Run the complete drill quarterly, before a production trust-root ceremony, and
after changing signing or infrastructure code:

1. Open a private drill ticket and capture the pre-drill live metadata hashes,
   versions, expiry, signer revisions and key states, and passing pre-traffic
   IAM policy checks.
2. Exercise targets renewal with a future sequential version and confirm exact
   client acceptance plus replay/rollback rejection.
3. Rotate a designated development releases/security key, import new targets,
   publish one complete transaction through the operation-mapped signers, and
   prove the old key and caller mapping can no longer sign.
4. Rotate the complete development root key set with old/new dual thresholds.
   Verify a client pinned to the previous root updates sequentially and a
   skipped, singly signed, expired, or tampered root fails.
5. In an isolated infrastructure-as-code environment, simulate
   snapshot/timestamp compromise: freeze, revoke access, complete the
   root-authorized replacement, and recover with no unsigned or partially
   published interval. Prove the snapshot signer cannot write metadata and a
   stale timestamp generation cannot commit.
6. Simulate loss of one primary offline device and recover its encrypted,
   geographically separated backup under two-person approval. Compare the
   derived public key ID and securely erase the working copy.
7. Rerun the infrastructure IAM policy checks, verify the bootstrap/import job,
   transfer versions, and temporary access are gone, and attach only the safe
   evidence fields listed above.

Any unexpected access, unknown IAM decision, version gap, partial transaction,
signature failure, or missing cleanup makes the drill a failure and blocks
production readiness.
