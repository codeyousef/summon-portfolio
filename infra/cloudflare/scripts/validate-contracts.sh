#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cloudflare_root="${repo_root}/infra/cloudflare"
contract="${cloudflare_root}/modules/platform/contract.json"

fail() {
  printf 'Cloudflare contract validation failed: %s\n' "$1" >&2
  exit 1
}

command -v jq >/dev/null || fail "jq is required"
command -v rg >/dev/null || fail "rg is required"

jq -e '
  .schema_version == 1 and
  .toolchain.opentofu == "1.12.5" and
  .toolchain.cloudflare_provider == "5.23.0" and
  .toolchain.wrangler == "4.125.0" and
  .toolchain.compatibility_date == "2026-08-13" and
  .authoritative_database == "firestore" and
  (.r2_buckets | keys | sort) == [
    "finops_receipts",
    "portfolio_docs",
    "portfolio_logs",
    "portfolio_media",
    "seen_backup",
    "seen_evidence",
    "seen_metadata",
    "seen_private",
    "seen_public",
    "seen_quarantine"
  ] and
  (.queues | keys | sort) == [
    "finops_ingest",
    "finops_ingest_dlq",
    "portfolio_async",
    "portfolio_async_dlq",
    "portfolio_async_parking",
    "seen_operations",
    "seen_operations_dlq"
  ] and
  .workers.portfolio_edge.container_regions == ["ME", "WEUR"] and
  .workers.seen_edge.container_regions == ["ENAM", "WNAM"] and
  ([.workers.seen_edge.route_less_workers[].role] | sort) == [
    "releases",
    "security",
    "snapshot",
    "timestamp"
  ] and
  all(.workers.seen_edge.route_less_workers[]; .route_less == true and (.secret_binding | length) > 0) and
  (([.workers | to_entries[].value.r2_bucket_keys[]] - [.r2_buckets | keys[]]) | length) == 0 and
  (([.workers | to_entries[].value.queue_keys[]] - [.queues | keys[]]) | length) == 0 and
  (.ownership.opentofu | sort) == ["access_applications", "access_policies", "access_service_tokens", "queues", "r2_bucket_locks", "r2_buckets"] and
  .r2_buckets.seen_public.lock_rules[0].prefix == "v1/blobs/sha256/" and
  .r2_buckets.seen_public.lock_rules[0].condition.type == "Indefinite" and
  .r2_buckets.seen_evidence.lock_rules[0].prefix == "v1/evidence/sha256/" and
  .r2_buckets.seen_evidence.lock_rules[0].condition.max_age_seconds == 7776000 and
  .r2_buckets.seen_backup.lock_rules[0].prefix == "v1/backup/sha256/" and
  .r2_buckets.seen_backup.lock_rules[0].condition.max_age_seconds == 7776000 and
  (.ownership.unmanaged_until_reviewed_import | index("dns_records")) != null and
  (.ownership.unmanaged_until_reviewed_import | index("zones")) != null
' "${contract}" >/dev/null || fail "platform contract has drifted from the reviewed ownership and regional policy"

mapfile -t versions_files < <(rg --files "${cloudflare_root}" -g 'versions.tf')
[[ "${#versions_files[@]}" -eq 4 ]] || fail "expected four versions.tf files"
for versions_file in "${versions_files[@]}"; do
  rg -q 'version[[:space:]]*=[[:space:]]*"5\.23\.0"' "${versions_file}" ||
    fail "${versions_file} does not pin Cloudflare provider 5.23.0"
  rg -q 'required_version[[:space:]]*=[[:space:]]*">= 1\.12\.0, < 1\.13\.0"' "${versions_file}" ||
    fail "${versions_file} does not constrain OpenTofu to 1.12.x"
done

if rg -n 'resource[[:space:]]+"cloudflare_(dns_record|zone|worker|workers_script|workers_deployment|workers_custom_domain|workers_route)"' \
  "${cloudflare_root}" -g '*.tf'; then
  fail "DNS and Worker releases must remain outside OpenTofu until their separately reviewed phases"
fi

if rg -n '^[[:space:]]*(api_token|access_key|secret_key|token)[[:space:]]*=' \
  "${cloudflare_root}" -g '*.tf' -g '*.hcl' -g '*.example'; then
  fail "credentials must be supplied only through environment variables"
fi

if git -C "${repo_root}" ls-files 'infra/cloudflare/**' | rg -q '(^|/)(terraform\.tfvars|[^/]+\.tfplan|[^/]+\.tfstate(?:\..*)?|\.dev\.vars)$'; then
  fail "local inputs, plans, state, or development secrets are tracked under infra/cloudflare"
fi

for ignored_path in \
  "infra/cloudflare/environments/dev/backend.hcl" \
  "infra/cloudflare/environments/prod/terraform.tfvars" \
  "infra/cloudflare/state-bootstrap/.state/bootstrap.tfstate" \
  "infra/cloudflare/.wrangler/state.json" \
  "infra/cloudflare/review.tfplan"; do
  git -C "${repo_root}" check-ignore -q "${ignored_path}" ||
    fail "${ignored_path} is not covered by the repository ignore policy"
done

rg -q 'readonly WRANGLER_VERSION="4\.125\.0"' \
  "${cloudflare_root}/scripts/wrangler-pinned.sh" ||
  fail "Wrangler wrapper is not pinned to the contract version"

jq -e '
  (.rules | length) == 1 and
  .rules[0].enabled == true and
  .rules[0].prefix == "backups/" and
  .rules[0].condition.type == "Age" and
  .rules[0].condition.maxAgeSeconds == 7776000
' "${cloudflare_root}/state/state-backup-lock.json" >/dev/null ||
  fail "state backup lock must retain the immutable backups/ prefix for 90 days"

rg -q 'resource[[:space:]]+"cloudflare_r2_bucket_lock"[[:space:]]+"immutable_prefixes"' \
  "${cloudflare_root}/modules/platform/resources.tf" ||
  fail "reviewed Seen immutable prefixes must be managed by OpenTofu Bucket Locks"

rg -q 'resource[[:space:]]+"cloudflare_zero_trust_access_service_token"[[:space:]]+"finops_wif_broker"' \
  "${cloudflare_root}/modules/platform/resources.tf" ||
  fail "the dev WIF broker must use a dedicated OpenTofu-owned Access service token"

rg -q 'decision[[:space:]]*=[[:space:]]*"non_identity"' \
  "${cloudflare_root}/modules/platform/resources.tf" ||
  fail "the dev WIF broker must use a Service Auth policy"

printf 'Cloudflare infrastructure contracts are valid.\n'
