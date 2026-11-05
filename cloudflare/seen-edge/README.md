# Seen Cloudflare edge runtime

This package is the Wrangler-owned runtime for Seen's hybrid Cloudflare/GCP deployment. Firestore remains authoritative. OpenTofu owns only the R2 bucket and Queue identities; Wrangler owns Worker code and bindings, Durable Object migrations, Workflows, the Queue consumer, and the scale-to-zero Container class.

Nothing in this directory creates, plans, deploys, or mutates a Cloudflare resource. Tracked templates deliberately contain no account IDs, zone IDs, routes, custom domains, secret values, or signing key material.

## Runtime boundary

- The main Worker streams only `v1/blobs/sha256/<digest>` and allowlisted TUF metadata from R2. Public blobs and versioned metadata are immutable; `root.json` and `timestamp.json` remain short-lived mutable pointers.
- Reservation, promotion, and signing-order Durable Objects use SQLite, the US jurisdiction, and deterministic `enam`/`wnam` first-placement hints.
- Queue messages are exact-schema and idempotent. Deterministically invalid messages are copied to the explicit DLQ; transient failures use bounded retries and the Queue DLQ policy.
- Promotion and maintenance Workflows start named one-shot Kotlin Container instances, poll durable status, and let idle instances scale to zero. Promotion and maintenance use separate R2 credentials and exact per-mode bucket environments.
- Four route-less signer Workers each require exactly one role-specific non-extractable Ed25519 `CryptoKey`. They validate the Kotlin canonical TUF request shape, request/audience/role/operation/digest/expiry, require a short-lived committed-state guard receipt, reserve monotonic signing order, and only then call WebCrypto.
- The timestamp signer builds the signed envelope and asks `SeenSigningOrderDO` to replace `v1/metadata/timestamp.json` with an R2 ETag compare-and-set. A changed pointer fails closed.

## Generate local Wrangler configs

Run `node scripts/validate-config.mjs` for a secret-free offline contract check. `scripts/generate-config.mjs` requires explicit dev/prod Firestore, R2 endpoint, public key, TUF key ID, and signer-guard service values. It creates five mode-`0600`, ignored `wrangler.generated.*.json` files with exclusive-create semantics. It never calls Cloudflare.

The four signing keys must be provisioned in a separately approved secret ceremony through the Workers Secrets API as `type: "secret_key"`, PKCS#8 Ed25519, `usages: ["sign"]`. Wrangler 4.123 declares their required names and preserves the bindings, but does not put private key bytes in config. Never use `wrangler secret put` with a text private key as a substitute.

## Required deployment gates not hidden by this package

Deployment remains blocked until all of the following are reviewed and implemented:

1. A route-less `SEEN_SIGNER_GUARD` adapter must expose the existing Kotlin `RegistryTufSignerStatePolicyGuard` as authorization-only receipts. The signer Workers return `503` if it is absent or rejects; shape checks and monotonic ordering are deliberately not presented as a replacement for committed-chain validation.
2. `registry-service` loads exactly one explicit Firestore service-account or external-account/WIF credential from the Container's in-memory secret environment, validates its credential type, and never writes it to disk. For signing jobs it selects a signer-endpoint-pinned Cloudflare bearer provider when `SEEN_SIGNER_CALL_TOKEN` is present; the Container outbound host map then replaces and forwards that credential through the appropriate route-less role-specific signer service binding. Dev deployment still requires the provisioned secret/resource bindings and a complete promotion/maintenance recovery exercise.
3. The platform contract must add `SeenRegistryJobContainer` to its DO binding/migration model and record the four caller service bindings plus signer metadata/ordering capabilities. The config validator permits the required Container DO binding but reports no contract mutation.
4. OpenTofu must apply reviewed R2 Bucket Lock/lifecycle rules. `v1/blobs/sha256/`, `v1/evidence/sha256/`, and `v1/backup/sha256/` are lock-safe. The flat `v1/metadata/` prefix cannot be bucket-locked without also freezing `root.json` and `timestamp.json`; versioned metadata needs a key-layout migration first.
5. Public catalog/Firestore API routes still belong to the Kotlin read-only service. This Worker intentionally implements immutable package/TUF reads only; it does not invent a second business-data authority.

Local verification:

```text
node scripts/validate-config.mjs
node_modules/.bin/tsc --noEmit
node_modules/.bin/vitest run
```
