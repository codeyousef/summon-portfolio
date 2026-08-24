#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
root="${repo_root}/infra/gcp-finops"

fail() {
  printf 'GCP FinOps contract validation failed: %s\n' "$1" >&2
  exit 1
}

command -v rg >/dev/null || fail "rg is required"

rg -q 'var\.project_id[[:space:]]*==[[:space:]]*"felidai-dev"' "${root}/dev/variables.tf" ||
  fail "the root must remain pinned to felidai-dev"
rg -q 'workload_identity_pool_id[[:space:]]*=[[:space:]]*"cloudflare-dev"' "${root}/dev/main.tf" ||
  fail "the dev WIF pool ID has drifted"
rg -q 'workload_identity_pool_provider_id[[:space:]]*=[[:space:]]*"access-dev"' "${root}/dev/main.tf" ||
  fail "the dev WIF provider ID has drifted"
rg -q '"google.subject"[[:space:]]*=[[:space:]]*"assertion.common_name"' "${root}/dev/main.tf" ||
  fail "the WIF subject must map only from the Access service-token common_name"
rg -q 'assertion\.type == .app.' "${root}/dev/main.tf" ||
  fail "the WIF condition must require an Access application token"
rg -q 'assertion\.sub == ..' "${root}/dev/main.tf" ||
  fail "the WIF condition must require the empty service-token subject"

for role in roles/iam.workloadIdentityUser roles/bigquery.jobUser roles/bigquery.dataViewer; do
  rg -q "${role}" "${root}/dev/main.tf" || fail "missing required least-privilege role ${role}"
done

if rg -n 'resource[[:space:]]+"google_service_account_key"|roles/(owner|editor|viewer|billing\.)' \
  "${root}" -g '*.tf'; then
  fail "service-account keys, broad project roles, and billing mutation roles are forbidden"
fi

firestore_iam="${root}/dev/portfolio_firestore_iam.tf"
[[ "$(rg -n 'roles/datastore\.' "${root}" -g '*.tf' | wc -l)" -eq 1 ]] ||
  fail "exactly one reviewed Firestore role binding is permitted"
rg -q 'role[[:space:]]*=[[:space:]]*"roles/datastore\.user"' "${firestore_iam}" ||
  fail "the reviewed Dammam binding must retain roles/datastore.user"
rg -q "resource.name == 'projects/portfolio-476219/databases/portfolio-me-dev'" "${firestore_iam}" ||
  fail "the Firestore role must remain constrained to portfolio-me-dev"
rg -q 'member[[:space:]]*=[[:space:]]*"serviceAccount:portfolio-dev-runtime@portfolio-476219\.iam\.gserviceaccount\.com"' "${firestore_iam}" ||
  fail "the Firestore role must remain bound only to the Portfolio dev runtime"

rg -q 'maximum_bytes_billed[[:space:]]*=[[:space:]]*50000000' "${root}/dev/outputs.tf" ||
  fail "the Worker handoff must retain the 50 MB daily query cap"
rg -q 'billing_dataset_location[[:space:]]*==[[:space:]]*"US"' "${root}/dev/variables.tf" ||
  fail "the billing export must remain in the US multi-region"

if git -C "${repo_root}" ls-files 'infra/gcp-finops/**' | \
  rg -q '(^|/)(terraform\.tfvars|backend\.hcl|[^/]+\.tfplan|[^/]+\.tfstate(?:\..*)?)$|(^|/)\.terraform/'; then
  fail "local inputs, backend configuration, plans, state, or provider caches are tracked"
fi

for ignored_path in \
  "infra/gcp-finops/dev/backend.hcl" \
  "infra/gcp-finops/dev/terraform.tfvars" \
  "infra/gcp-finops/dev/review.tfplan" \
  "infra/gcp-finops/dev/.terraform/terraform.tfstate"; do
  git -C "${repo_root}" check-ignore -q "${ignored_path}" ||
    fail "${ignored_path} is not covered by the repository ignore policy"
done

printf 'GCP FinOps infrastructure contracts are valid.\n'
