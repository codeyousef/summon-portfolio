# AWS platform

This tree defines the AWS home for Portfolio and its subdomains in a dedicated
AWS account. Every root is inert by default: nothing is created until
`resources_enabled = true` (or `bootstrap_enabled = true`) in an ignored
tfvars file, and every plan and apply is a separately approved phase.

The organization restricts regional services to `eu-north-1`. CloudFront
certificates are the only resources created in `us-east-1`.

## Layout

| Root | State key | Applied by | Owns |
| --- | --- | --- | --- |
| `state-bootstrap/` | local `.state/` (ignored, keep encrypted offline) | operator | S3 state bucket, account budget |
| `environments/shared/` | `state/shared/` | operator only | `yousef.codes` Route 53 zone and non-application records, ECR repositories, GitHub OIDC provider and CI roles |
| `environments/dev/` | `state/dev/` | operator or `portfolio-ci-dev-apply` | dev platform |
| `environments/prod/` | `state/prod/` | `portfolio-ci-prod-apply` after protected review | prod platform |

CI identities cannot modify themselves, the OIDC provider, or shared
foundation resources, and no infrastructure identity can read or write
application data, read secret values, or use signing keys.

## Request path

```
viewer ─▶ CloudFront ─┬─▶ /cdn/media/*, /cdn/docs/*  ─▶ S3 (prod only, OAC)
                      └─▶ everything else ─▶ ALB (CloudFront origin-facing prefix list only)
                                              ├─ /internal/*            → 404
                                              ├─ /seen*, /playground*,  → Seen playground (isolated subnets, no internet)
                                              │  /api/seen/run*           Host rewritten to seen.<suffix>
                                              ├─ /summon|/materia|      → SSR, Host rewritten, prefix stripped
                                              │  /sigil|/aether*
                                              └─ served hosts           → SSR
```

CloudFront forwards the viewer `Host`, so the Kotlin host map works unchanged
and the ALB certificate covers the same names as the CloudFront certificate.
The ALB rewrites reproduce `cloudflare/portfolio-edge/src/routing.ts`.

In dev, every ALB rule except `/__hooks/github` (signature-verified by the
application) first authenticates against an owner-only Cognito user pool with
mandatory MFA. CloudFront does not read S3 directly in dev; the application
serves `/cdn/*` behind the gate.

## Runtime services

| Service | Image | Network | Task permissions |
| --- | --- | --- | --- |
| `app-ssr` | `portfolio-ssr` (`Dockerfile.portfolio`) | public subnets, inbound only from the ALB, HTTPS egress | app tables, media/docs/receipts objects, jobs queue |
| `app-playground` | `seen-playground` (`Dockerfile`) | isolated subnets, ECR/Logs endpoints only | none |

Services use digest-pinned images. OpenTofu registers the initial task
definition; later rollouts register new revisions through the protected deploy
identity, so services ignore `task_definition` drift. Routine deploy roles may
pass only `portfolio-<env>-app-*` roles and push only routine repositories;
the `seen-registry-signer` repository and `portfolio-<env>-signer-*` roles
belong to the separate signer deploy identity.

FinOps ingestion runs from EventBridge Scheduler messages on the jobs queue.
Schedules are created `DISABLED` and are enabled only under a separate
approval after their target, caller identity, retry policy, and one
scheduler-originated execution have been verified.

Secrets Manager entries are created without values. Set each value out of
band before starting a service that injects it.

## Environment phases

Each step is its own reviewed plan and apply:

1. `resources_enabled = true`: network, ALB (no listener yet), target groups,
   Cognito gate, tables, buckets, jobs queue, disabled schedules, empty
   secrets, and the ACM certificate requests. The
   `certificate_validation_records` output lists the validation CNAMEs.
2. Until the zone is delegated, mirror those CNAMEs at Cloudflare as DNS-only
   records. They do not change live traffic.
3. `certificates_validated = true` once every certificate is `ISSUED`:
   listeners and rewrite rules, CloudFront, and Route 53 host records (served
   only after delegation).
4. Set secret values, then pinned `images`, to start services.
5. Back up and copy data, verify parity, then delegate the zone at the
   registrar.

## Commands

All commands run against the dedicated account's `portfolio` CLI profile.

```bash
tofu -chdir=infra/aws/environments/dev init -backend-config=backend.hcl
tofu -chdir=infra/aws/environments/dev plan -out=dev.tfplan
# review the complete saved plan, then apply exactly that plan
tofu -chdir=infra/aws/environments/dev apply dev.tfplan
```

Saved plans, rendered plans, backend files, and tfvars are ignored and must
never be committed, uploaded as plaintext artifacts, or printed to public
logs.

Local contract tests need no AWS access:

```bash
tofu -chdir=infra/aws/modules/platform init -backend=false && tofu -chdir=infra/aws/modules/platform test
tofu -chdir=infra/aws/modules/shared init -backend=false && tofu -chdir=infra/aws/modules/shared test
```

## Seen registry

`modules/registry` runs beside the platform in each environment and follows
[`registry-service/docs/aws-operations.md`](../../registry-service/docs/aws-operations.md).
Each step is a separately approved phase controlled by the `registry` object
in the environment tfvars:

1. `enabled` creates the seven DynamoDB tables, six S3 buckets (bucket
   policies deny unconditional writes to archives, versioned metadata, and
   the `root.json`/`timestamp.json` pointers), runtime IAM roles, the internal
   HTTPS ALB, and empty secrets.
2. `kms_keys_enabled` creates one single-region `ECC_NIST_EDWARDS25519` key per
   online role. The key policy lets the account administer keys but grants
   `kms:Sign` only to that role's signer task role, restricted to
   `ED25519_SHA_512` and `RAW` messages.
3. `image`, `online_public_keys_hex`, and `trusted_root_v1_sha256` (from the
   reviewed ceremony) start the APIs, coordinators, jobs, and signers.
   `signer_image` comes from the separately reviewed `seen-registry-signer`
   repository.
4. Schedules are created `DISABLED`; refresh schedules are additionally gated
   by `refresh_schedules_enabled`.

Production runs only the read-only API, maintenance jobs, and signers. Each
signer accepts an operation only from the coordinator roles that perform it
(`signer_caller_bindings` output). Registry and signer endpoints resolve to
private addresses under `*.registry.<host suffix>`; the SSR gateway receives
their upstream URLs automatically once the API runs.
