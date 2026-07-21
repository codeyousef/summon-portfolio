#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

fake_tofu() {
  [[ $# -eq 4 ]] || return 65
  [[ "$1" == -chdir=* ]] || return 65
  [[ "$2" == show ]] || return 65
  [[ "$3" == -json ]] || return 65
  [[ "$4" == "${FAKE_PLAN_PATH:?}" ]] || return 65
  [[ -z "${FAKE_TOFU_STDERR:-}" ]] || printf '%s\n' "${FAKE_TOFU_STDERR}" >&2
  case "${FAKE_TOFU_RESULT:-success}" in
    success) printf '%s\n' "${FAKE_PLAN_JSON:?}" ;;
    failure) return 1 ;;
    *) return 64 ;;
  esac
}

fake_gcloud() {
  if [[ "${1:-}" == version ]]; then
    [[ $# -eq 2 && "$2" == --format=json ]] || return 65
    case "${FAKE_GCLOUD_VERSION_RESULT:-success}" in
      success) printf '{"Google Cloud SDK":"%s"}\n' "${FAKE_GCLOUD_VERSION:-576.0.0}" ;;
      failure)
        printf '%s\n' "${FAKE_PRIVATE_SENTINEL:-private-diagnostic}" >&2
        return 1
        ;;
      malformed-secret) printf '%s\n' "${FAKE_PRIVATE_SENTINEL:-private-diagnostic}" ;;
      *) return 64 ;;
    esac
    return
  fi

  [[ $# -eq 8 ]] || return 65
  [[ "$1" == alpha && "$2" == iam && "$3" == policies && "$4" == lint-condition ]] || return 65
  [[ "$5" == --expression=* ]] || return 65
  [[ "$6" == --title=seen_registry_iam_condition_* ]] || return 65
  [[ "$7" == --format=json && "$8" == --quiet ]] || return 65
  local expression="${5#--expression=}"
  local title="${6#--title=}"
  [[ -n "${expression}" && "${title}" =~ ^seen_registry_iam_condition_[1-9][0-9]*$ ]] || return 65
  printf '%s\n' "${expression}" >> "${FAKE_GCLOUD_LOG:?}"
  case "${FAKE_GCLOUD_RESULT:-empty-object}" in
    empty-array) printf '[]\n' ;;
    empty-object) printf '{}\n' ;;
    empty-results) printf '{"lintResults":[]}\n' ;;
    finding-secret) printf '{"lintResults":[{"severity":"ERROR","debug":"%s"}]}\n' "${FAKE_PRIVATE_SENTINEL:-private-diagnostic}" ;;
    malformed-secret) printf '%s\n' "${FAKE_PRIVATE_SENTINEL:-private-diagnostic}" ;;
    failure)
      printf '%s\n' "${FAKE_PRIVATE_SENTINEL:-private-diagnostic}" >&2
      return 1
      ;;
    *) return 64 ;;
  esac
}

case "${0##*/}" in
  tofu)
    if fake_tofu "$@"; then exit 0; else exit $?; fi
    ;;
  gcloud)
    if fake_gcloud "$@"; then exit 0; else exit $?; fi
    ;;
esac

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_SCRIPT="$(realpath "${BASH_SOURCE[0]}")"
readonly LINT_SCRIPT="${SCRIPT_DIR}/lint-seen-registry-iam-conditions.sh"
readonly ORIGINAL_PATH="${PATH}"
readonly PROJECT_ID="seen-registry-prod-476219"
readonly PROJECT_NUMBER="4321120"
readonly APPLY_MEMBER="serviceAccount:seen-registry-prod-iac@${PROJECT_ID}.iam.gserviceaccount.com"
readonly PLAN_MEMBER="serviceAccount:seen-registry-prod-plan@${PROJECT_ID}.iam.gserviceaccount.com"
readonly HUMAN_MEMBER="user:yousef@felidai.com"
readonly RECOVERY_EXPIRY="2026-07-21T12:34:56Z"
readonly MIGRATION_EXPIRY="2026-07-22T12:34:56Z"
readonly PRIVATE_SENTINEL="PRIVATE_PLAN_DIAGNOSTIC_MUST_NOT_ESCAPE"

declare -ar STEADY_ADDRESSES=(
  'google_project_iam_member.infrastructure_project_iam[0]'
  'google_project_iam_member.infrastructure_kms_policy_setter["seen-registry-prod-releases"]'
  'google_project_iam_member.infrastructure_kms_policy_setter["seen-registry-prod-security"]'
  'google_project_iam_member.infrastructure_kms_policy_setter["seen-registry-prod-snapshot"]'
  'google_project_iam_member.infrastructure_kms_policy_setter["seen-registry-prod-timestamp"]'
  'google_project_iam_member.infrastructure_run_policy_setter["job"]'
  'google_project_iam_member.infrastructure_run_policy_setter["service"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-github-app-id"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-github-app-private-key"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-gitlab-forge-token"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-publisher-token"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-root-envelope"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-root-rotation-envelope"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-security-token"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-targets-envelope"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-targets-releases-rotation-envelope"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-targets-renewal-envelope"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-targets-security-rotation-envelope"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-trust-and-safety-token"]'
  'google_project_iam_member.infrastructure_registry_storage_policy_setter["seen-registry-prod-476219-seen-registry-prod-backup"]'
  'google_project_iam_member.infrastructure_registry_storage_policy_setter["seen-registry-prod-476219-seen-registry-prod-blobs"]'
  'google_project_iam_member.infrastructure_registry_storage_policy_setter["seen-registry-prod-476219-seen-registry-prod-evidence"]'
  'google_project_iam_member.infrastructure_registry_storage_policy_setter["seen-registry-prod-476219-seen-registry-prod-metadata"]'
  'google_project_iam_member.infrastructure_registry_storage_policy_setter["seen-registry-prod-476219-seen-registry-prod-private"]'
  'google_project_iam_member.infrastructure_registry_storage_policy_setter["seen-registry-prod-476219-seen-registry-prod-quarantine"]'
  'google_project_iam_member.infrastructure_state_storage_policy_setter["bootstrap"]'
  'google_project_iam_member.infrastructure_state_storage_policy_setter["production"]'
)
declare -ar RECOVERY_READ_ADDRESSES=(
  'google_project_iam_member.temporary_human_state_bucket_policy_read_access["bootstrap"]'
  'google_project_iam_member.temporary_human_state_bucket_policy_read_access["production"]'
)
declare -ar RECOVERY_SETTER_ADDRESSES=(
  'google_project_iam_member.temporary_human_state_bucket_policy_access["bootstrap"]'
  'google_project_iam_member.temporary_human_state_bucket_policy_access["production"]'
)
declare -ar RECOVERY_ADDRESSES=("${RECOVERY_READ_ADDRESSES[@]}" "${RECOVERY_SETTER_ADDRESSES[@]}")
declare -ar CUSTOM_SETTER_ADDRESSES=(
  'google_project_iam_custom_role.project_iam_apply[0]'
  'google_project_iam_custom_role.resource_iam_setter["kms"]'
  'google_project_iam_custom_role.resource_iam_setter["run_job"]'
  'google_project_iam_custom_role.resource_iam_setter["run_service"]'
  'google_project_iam_custom_role.resource_iam_setter["secret"]'
  'google_project_iam_custom_role.resource_iam_setter["storage"]'
)
declare -ar RECOVERY_CHANGE_ADDRESSES=(
  'google_project_iam_custom_role.state_bucket_policy_reader[0]'
  "${RECOVERY_ADDRESSES[@]}"
)
declare -ar STEADY_EVIDENCE_ADDRESSES=(
  'terraform_data.recovery_reconciliation_record["user:yousef@felidai.com"]'
  'terraform_data.project_creator_owner_adoption_record["user:yousef@felidai.com"]'
  'terraform_data.recovery_cleanup_record["user:yousef@felidai.com"]'
  'terraform_data.project_creator_owner_removal_record["user:yousef@felidai.com"]'
)
declare -ar STATE_POLICY_ADDRESSES=(
  'google_storage_bucket_iam_policy.state["bootstrap"]'
  'google_storage_bucket_iam_policy.state["production"]'
)
declare -ar PHASE_CONTRACT_ADDRESSES=(
  'terraform_data.phase_evidence_contract'
  'terraform_data.bootstrap_phase_contract'
)

for required_command in bash chmod cp env jq ln mkdir mktemp realpath rm sed wc; do
  command -v "${required_command}" >/dev/null || fail "${required_command} is required"
done
[[ -f "${LINT_SCRIPT}" ]] || fail "the IAM-condition lint script was not found"

binding_key() {
  local key="${1#*\[\"}"
  printf '%s\n' "${key%\"\]}"
}

state_bucket_for_key() {
  case "$1" in
    bootstrap) printf '%s-bootstrap-tofu-state\n' "${PROJECT_ID}" ;;
    production) printf '%s-prod-tofu-state\n' "${PROJECT_ID}" ;;
    *) return 1 ;;
  esac
}

readonly PROJECT_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/datastore.user', 'roles/datastore.viewer'])"
readonly KMS_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['projects/${PROJECT_ID}/roles/seen_registry_prod_kms_signer', 'roles/cloudkms.publicKeyViewer'])"
readonly RUN_JOB_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/run.invoker', 'projects/${PROJECT_ID}/roles/seenRegistryJobViewer'])"
readonly RUN_SERVICE_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/run.invoker'])"
readonly SECRET_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/secretmanager.secretAccessor'])"
readonly REGISTRY_STORAGE_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['projects/${PROJECT_ID}/roles/seen_registry_prod_blob_creator', 'projects/${PROJECT_ID}/roles/seen_registry_prod_metadata_creator', 'projects/${PROJECT_ID}/roles/seen_registry_prod_pointer_replacer', 'projects/${PROJECT_ID}/roles/seen_registry_prod_quarantine_promoter', 'projects/${PROJECT_ID}/roles/seen_registry_prod_quarantine_writer', 'roles/storage.objectViewer'])"
readonly STATE_STORAGE_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['projects/${PROJECT_ID}/roles/seenRegistryStateLocker', 'projects/${PROJECT_ID}/roles/seenRegistryStateReader', 'projects/${PROJECT_ID}/roles/seenRegistryStateWriter'])"

expected_role_for_address() {
  case "$1" in
    'google_project_iam_member.infrastructure_project_iam[0]') printf 'projects/%s/roles/seenRegistryProjectIamApply\n' "${PROJECT_ID}" ;;
    google_project_iam_member.infrastructure_kms_policy_setter*) printf 'projects/%s/roles/seenRegistryKmsIamApply\n' "${PROJECT_ID}" ;;
    'google_project_iam_member.infrastructure_run_policy_setter["job"]') printf 'projects/%s/roles/seenRegistryRunJobIamApply\n' "${PROJECT_ID}" ;;
    'google_project_iam_member.infrastructure_run_policy_setter["service"]') printf 'projects/%s/roles/seenRegistryRunServiceIamApply\n' "${PROJECT_ID}" ;;
    google_project_iam_member.infrastructure_secret_policy_setter*) printf 'projects/%s/roles/seenRegistrySecretIamApply\n' "${PROJECT_ID}" ;;
    google_project_iam_member.infrastructure_registry_storage_policy_setter* | \
      google_project_iam_member.infrastructure_state_storage_policy_setter* | \
      google_project_iam_member.temporary_human_state_bucket_policy_access*)
      printf 'projects/%s/roles/seenRegistryStorageIamApply\n' "${PROJECT_ID}"
      ;;
    google_project_iam_member.temporary_human_state_bucket_policy_read_access*) printf 'projects/%s/roles/seenRegistryStateBucketPolicyReader\n' "${PROJECT_ID}" ;;
    *) return 1 ;;
  esac
}

expected_expression_for_address() {
  local address="$1"
  local key bucket
  case "${address}" in
    'google_project_iam_member.infrastructure_project_iam[0]') printf '%s\n' "${PROJECT_MODIFIED_ROLES}" ;;
    google_project_iam_member.infrastructure_kms_policy_setter*)
      key="$(binding_key "${address}")"
      printf "resource.type == 'cloudkms.googleapis.com/CryptoKey' && resource.name == 'projects/%s/locations/us-central1/keyRings/seen-registry-prod/cryptoKeys/%s' && %s\n" "${PROJECT_ID}" "${key}" "${KMS_MODIFIED_ROLES}"
      ;;
    'google_project_iam_member.infrastructure_run_policy_setter["job"]') printf '%s\n' "${RUN_JOB_MODIFIED_ROLES}" ;;
    'google_project_iam_member.infrastructure_run_policy_setter["service"]') printf '%s\n' "${RUN_SERVICE_MODIFIED_ROLES}" ;;
    google_project_iam_member.infrastructure_secret_policy_setter*)
      key="$(binding_key "${address}")"
      printf "resource.type == 'secretmanager.googleapis.com/Secret' && resource.name == 'projects/%s/secrets/%s' && %s\n" "${PROJECT_NUMBER}" "${key}" "${SECRET_MODIFIED_ROLES}"
      ;;
    google_project_iam_member.infrastructure_registry_storage_policy_setter*)
      key="$(binding_key "${address}")"
      printf "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s\n" "${key}" "${REGISTRY_STORAGE_MODIFIED_ROLES}"
      ;;
    google_project_iam_member.infrastructure_state_storage_policy_setter*)
      key="$(binding_key "${address}")"; bucket="$(state_bucket_for_key "${key}")"
      printf "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s\n" "${bucket}" "${STATE_STORAGE_MODIFIED_ROLES}"
      ;;
    google_project_iam_member.temporary_human_state_bucket_policy_read_access*)
      key="$(binding_key "${address}")"; bucket="$(state_bucket_for_key "${key}")"
      printf "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && request.time < timestamp('%s')\n" "${bucket}" "${RECOVERY_EXPIRY}"
      ;;
    google_project_iam_member.temporary_human_state_bucket_policy_access*)
      key="$(binding_key "${address}")"; bucket="$(state_bucket_for_key "${key}")"
      printf "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s && request.time < timestamp('%s')\n" "${bucket}" "${STATE_STORAGE_MODIFIED_ROLES}" "${RECOVERY_EXPIRY}"
      ;;
    *) return 1 ;;
  esac
}

expected_custom_contract() {
  case "$1" in
    'google_project_iam_custom_role.project_iam_apply[0]') printf '%s\t%s\n' seenRegistryProjectIamApply '["resourcemanager.projects.setIamPolicy"]' ;;
    'google_project_iam_custom_role.resource_iam_setter["kms"]') printf '%s\t%s\n' seenRegistryKmsIamApply '["cloudkms.cryptoKeys.setIamPolicy"]' ;;
    'google_project_iam_custom_role.resource_iam_setter["run_job"]') printf '%s\t%s\n' seenRegistryRunJobIamApply '["run.jobs.setIamPolicy"]' ;;
    'google_project_iam_custom_role.resource_iam_setter["run_service"]') printf '%s\t%s\n' seenRegistryRunServiceIamApply '["run.services.setIamPolicy"]' ;;
    'google_project_iam_custom_role.resource_iam_setter["secret"]') printf '%s\t%s\n' seenRegistrySecretIamApply '["secretmanager.secrets.setIamPolicy"]' ;;
    'google_project_iam_custom_role.resource_iam_setter["storage"]') printf '%s\t%s\n' seenRegistryStorageIamApply '["storage.buckets.setIamPolicy"]' ;;
    'google_project_iam_custom_role.state_reader[0]') printf '%s\t%s\n' seenRegistryStateReader '["storage.buckets.get","storage.objects.get","storage.objects.list"]' ;;
    'google_project_iam_custom_role.state_bucket_policy_reader[0]') printf '%s\t%s\n' seenRegistryStateBucketPolicyReader '["storage.buckets.get","storage.buckets.getIamPolicy"]' ;;
    *) return 1 ;;
  esac
}

expected_evidence_sentinel() {
  case "$1" in
    'terraform_data.recovery_reconciliation_record["user:yousef@felidai.com"]') printf '%s\n' recovery-reconciliation-applied ;;
    'terraform_data.project_creator_owner_adoption_record["user:yousef@felidai.com"]') printf '%s\n' project-creator-owner-adopted ;;
    'terraform_data.recovery_cleanup_record["user:yousef@felidai.com"]') printf '%s\n' recovery-project-bindings-cleaned ;;
    'terraform_data.project_creator_owner_removal_record["user:yousef@felidai.com"]') printf '%s\n' project-creator-owner-removed ;;
    *) return 1 ;;
  esac
}

expected_phase_contract_sentinel() {
  case "$1" in
    terraform_data.phase_evidence_contract) printf '%s\n' seen-registry-production-bootstrap-phase-evidence-contract ;;
    terraform_data.bootstrap_phase_contract) printf '%s\n' seen-registry-production-bootstrap-phase-contract ;;
    *) return 1 ;;
  esac
}

build_binding_resource() {
  local address="$1"
  local member="${APPLY_MEMBER}"
  [[ "${address}" == google_project_iam_member.temporary_human_state_bucket_policy_* ]] && member="${HUMAN_MEMBER}"
  jq -cn \
    --arg address "${address}" \
    --arg expression "$(expected_expression_for_address "${address}")" \
    --arg member "${member}" \
    --arg project "${PROJECT_ID}" \
    --arg role "$(expected_role_for_address "${address}")" \
    '{address: $address, mode: "managed", type: "google_project_iam_member", values: {condition: [{expression: $expression}], member: $member, project: $project, role: $role}}'
}

build_custom_resource() {
  local address="$1"
  local contract role_id permissions
  contract="$(expected_custom_contract "${address}")"
  role_id="${contract%%$'\t'*}"
  permissions="${contract#*$'\t'}"
  jq -cn \
    --arg address "${address}" \
    --arg project "${PROJECT_ID}" \
    --arg role_id "${role_id}" \
    --argjson permissions "${permissions}" \
    '{address: $address, mode: "managed", type: "google_project_iam_custom_role", values: {project: $project, role_id: $role_id, permissions: $permissions}}'
}

build_evidence_resource() {
  local address="$1"
  local sentinel
  sentinel="$(expected_evidence_sentinel "${address}")"
  jq -cn \
    --arg address "${address}" \
    --arg sentinel "${sentinel}" \
    '{address: $address, mode: "managed", type: "terraform_data", values: {input: $sentinel, output: $sentinel}}'
}

build_phase_contract_resource() {
  local address="$1"
  local sentinel
  sentinel="$(expected_phase_contract_sentinel "${address}")"
  jq -cn \
    --arg address "${address}" \
    --arg sentinel "${sentinel}" \
    '{address: $address, mode: "managed", type: "terraform_data", values: {input: $sentinel, output: $sentinel}}'
}

build_state_policy_resource() {
  local address="$1"
  local include_human="$2"
  local key bucket prefix state_resource lock_resource policy_data
  key="$(binding_key "${address}")"
  bucket="$(state_bucket_for_key "${key}")"
  case "${key}" in
    bootstrap) prefix='seen-registry/bootstrap/prod' ;;
    production) prefix='seen-registry/prod' ;;
    *) return 1 ;;
  esac
  state_resource="projects/_/buckets/${bucket}/objects/${prefix}/default.tfstate"
  lock_resource="projects/_/buckets/${bucket}/objects/${prefix}/default.tflock"
  policy_data="$(
    jq -cn \
      --arg apply "${APPLY_MEMBER}" \
      --arg plan "${PLAN_MEMBER}" \
      --arg human "${HUMAN_MEMBER}" \
      --arg reader "projects/${PROJECT_ID}/roles/seenRegistryStateReader" \
      --arg locker "projects/${PROJECT_ID}/roles/seenRegistryStateLocker" \
      --arg writer "projects/${PROJECT_ID}/roles/seenRegistryStateWriter" \
      --arg lock_title "exact_${key}_plan_lock" \
      --arg lock_expression "resource.name == '${lock_resource}'" \
      --arg writer_title "exact_${key}_state_mutations" \
      --arg writer_expression "resource.name == '${state_resource}' || resource.name == '${lock_resource}'" \
      --arg human_reader_title "temporary_${key}_human_state_read" \
      --arg human_reader_expression "request.time < timestamp('${MIGRATION_EXPIRY}')" \
      --arg human_writer_title "temporary_${key}_human_state_mutations" \
      --arg human_writer_expression "(resource.name == '${state_resource}' || resource.name == '${lock_resource}') && request.time < timestamp('${MIGRATION_EXPIRY}')" \
      --argjson include_human "${include_human}" \
      '{
        bindings: (
          [
            {role: $reader, members: [$apply, $plan]},
            {role: $locker, members: [$plan], condition: {title: $lock_title, expression: $lock_expression}},
            {role: $writer, members: [$apply], condition: {title: $writer_title, expression: $writer_expression}}
          ] +
          (if $include_human then
            [
              {role: $reader, members: [$human], condition: {title: $human_reader_title, expression: $human_reader_expression}},
              {role: $writer, members: [$human], condition: {title: $human_writer_title, expression: $human_writer_expression}}
            ]
          else
            []
          end)
        )
      }'
  )"
  jq -cn \
    --arg address "${address}" \
    --arg bucket "${bucket}" \
    --arg policy_data "${policy_data}" \
    '{address: $address, mode: "managed", type: "google_storage_bucket_iam_policy", values: {bucket: $bucket, policy_data: $policy_data}}'
}

build_plan_json() {
  local mode="$1"
  local -a binding_addresses=()
  local -a custom_addresses=()
  local -a evidence_addresses=()
  local -a policy_addresses=()
  local -a contract_addresses=()
  case "${mode}" in
    recovery)
      binding_addresses=("${RECOVERY_ADDRESSES[@]}")
      custom_addresses=('google_project_iam_custom_role.state_bucket_policy_reader[0]')
      ;;
    steady)
      binding_addresses=("${STEADY_ADDRESSES[@]}")
      custom_addresses=("${CUSTOM_SETTER_ADDRESSES[@]}" 'google_project_iam_custom_role.state_reader[0]' 'google_project_iam_custom_role.state_bucket_policy_reader[0]')
      evidence_addresses=("${STEADY_EVIDENCE_ADDRESSES[@]}")
      policy_addresses=("${STATE_POLICY_ADDRESSES[@]}")
      contract_addresses=("${PHASE_CONTRACT_ADDRESSES[@]}")
      ;;
    complete)
      binding_addresses=("${STEADY_ADDRESSES[@]}" "${RECOVERY_ADDRESSES[@]}")
      custom_addresses=("${CUSTOM_SETTER_ADDRESSES[@]}" 'google_project_iam_custom_role.state_reader[0]' 'google_project_iam_custom_role.state_bucket_policy_reader[0]')
      evidence_addresses=("${STEADY_EVIDENCE_ADDRESSES[@]}")
      policy_addresses=("${STATE_POLICY_ADDRESSES[@]}")
      contract_addresses=("${PHASE_CONTRACT_ADDRESSES[@]}")
      ;;
    *) return 64 ;;
  esac
  local root='[]' child='[]' prior_root='[]' prior_child='[]' resource prior_resource address index=0
  for address in "${binding_addresses[@]}"; do
    resource="$(build_binding_resource "${address}")"
    if (( index % 2 == 0 )); then root="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${root}")"; else child="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${child}")"; fi
    index=$((index + 1))
  done
  for address in "${custom_addresses[@]}"; do
    resource="$(build_custom_resource "${address}")"
    if (( index % 2 == 0 )); then root="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${root}")"; else child="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${child}")"; fi
    index=$((index + 1))
  done
  for address in "${evidence_addresses[@]}"; do
    resource="$(build_evidence_resource "${address}")"
    if (( index % 2 == 0 )); then
      root="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${root}")"
      prior_root="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${prior_root}")"
    else
      child="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${child}")"
      prior_child="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${prior_child}")"
    fi
    index=$((index + 1))
  done
  for address in "${contract_addresses[@]}"; do
    resource="$(build_phase_contract_resource "${address}")"
    if (( index % 2 == 0 )); then
      root="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${root}")"
      prior_root="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${prior_root}")"
    else
      child="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${child}")"
      prior_child="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${prior_child}")"
    fi
    index=$((index + 1))
  done
  for address in "${policy_addresses[@]}"; do
    resource="$(build_state_policy_resource "${address}" false)"
    prior_resource="$(build_state_policy_resource "${address}" true)"
    if (( index % 2 == 0 )); then
      root="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${root}")"
      prior_root="$(jq -c --argjson r "${prior_resource}" '. + [$r]' <<< "${prior_root}")"
    else
      child="$(jq -c --argjson r "${resource}" '. + [$r]' <<< "${child}")"
      prior_child="$(jq -c --argjson r "${prior_resource}" '. + [$r]' <<< "${prior_child}")"
    fi
    index=$((index + 1))
  done
  local resources changes recovery_changes
  resources="$(jq -cn --argjson root "${root}" --argjson child "${child}" '$root + $child')"
  recovery_changes="$(printf '%s\n' "${RECOVERY_CHANGE_ADDRESSES[@]}" | jq -R . | jq -cs '.')"
  changes="$(
    jq -cn --argjson resources "${resources}" --argjson recovery "${recovery_changes}" --arg mode "${mode}" '
      $resources
      | map(
          . as $resource
          | {
              address: $resource.address,
              mode: "managed",
              type: $resource.type,
              change: {
                actions: [
                  if $mode == "recovery" and ($recovery | index($resource.address)) then
                    "create"
                  else
                    "no-op"
                  end
                ]
              }
            }
        )
    '
  )"
  jq -cn \
    --argjson root "${root}" \
    --argjson child "${child}" \
    --argjson prior_root "${prior_root}" \
    --argjson prior_child "${prior_child}" \
    --argjson changes "${changes}" \
    '{
      planned_values: {root_module: {resources: $root, child_modules: [{resources: $child}]}},
      prior_state: {values: {root_module: {resources: $prior_root, child_modules: [{resources: $prior_child}]}}},
      resource_changes: $changes
    }'
}

set_resource_string() {
  local plan="$1" address="$2" field="$3" value="$4"
  jq -c --arg address "${address}" --arg field "${field}" --arg value "${value}" '
    walk(if type == "object" and .address? == $address and (.values? | type) == "object" then .values[$field] = $value else . end)
  ' <<< "${plan}"
}

set_resource_json() {
  local plan="$1" address="$2" field="$3" value="$4"
  jq -c --arg address "${address}" --arg field "${field}" --argjson value "${value}" '
    walk(if type == "object" and .address? == $address and (.values? | type) == "object" then .values[$field] = $value else . end)
  ' <<< "${plan}"
}

set_expression() {
  local plan="$1" address="$2" expression="$3"
  jq -c --arg address "${address}" --arg expression "${expression}" '
    walk(if type == "object" and .address? == $address and (.values.condition? | type) == "array" then .values.condition[0].expression = $expression else . end)
  ' <<< "${plan}"
}

set_change_actions() {
  local plan="$1" address="$2" actions="$3"
  jq -c --arg address "${address}" --argjson actions "${actions}" '(.resource_changes[] | select(.address == $address) | .change.actions) = $actions' <<< "${plan}"
}

add_managed_resource() {
  local plan="$1" resource="$2" actions="${3:-[\"no-op\"]}"
  jq -c --argjson resource "${resource}" --argjson actions "${actions}" '
    .planned_values.root_module.resources += [$resource]
    | .resource_changes += [{address: $resource.address, mode: "managed", type: $resource.type, change: {actions: $actions}}]
  ' <<< "${plan}"
}

remove_planned_resource() {
  local plan="$1" address="$2"
  jq -c --arg address "${address}" '
    .planned_values.root_module |= walk(
      if type == "object" and (.resources? | type) == "array" then
        .resources |= map(select(.address != $address))
      else
        .
      end
    )
  ' <<< "${plan}"
}

remove_prior_resource() {
  local plan="$1" address="$2"
  jq -c --arg address "${address}" '
    .prior_state.values.root_module |= walk(
      if type == "object" and (.resources? | type) == "array" then
        .resources |= map(select(.address != $address))
      else
        .
      end
    )
  ' <<< "${plan}"
}

set_planned_resource_string() {
  local plan="$1" address="$2" field="$3" value="$4"
  jq -c --arg address "${address}" --arg field "${field}" --arg value "${value}" '
    .planned_values.root_module |= walk(
      if type == "object" and .address? == $address and (.values? | type) == "object" then
        .values[$field] = $value
      else
        .
      end
    )
  ' <<< "${plan}"
}

set_prior_resource_string() {
  local plan="$1" address="$2" field="$3" value="$4"
  jq -c --arg address "${address}" --arg field "${field}" --arg value "${value}" '
    .prior_state.values.root_module |= walk(
      if type == "object" and .address? == $address and (.values? | type) == "object" then
        .values[$field] = $value
      else
        .
      end
    )
  ' <<< "${plan}"
}

add_prior_resource() {
  local plan="$1" resource="$2"
  jq -c --argjson resource "${resource}" '.prior_state.values.root_module.resources += [$resource]' <<< "${plan}"
}

remove_all_phase_evidence() {
  local plan="$1"
  local evidence_json
  evidence_json="$(printf '%s\n' "${STEADY_EVIDENCE_ADDRESSES[@]}" | jq -R . | jq -cs '.')"
  jq -c --argjson evidence "${evidence_json}" '
    .planned_values.root_module |= walk(
      if type == "object" and (.resources? | type) == "array" then
        .resources |= map(select(.address as $address | ($evidence | index($address)) == null))
      else
        .
      end
    )
    | .prior_state.values.root_module |= walk(
        if type == "object" and (.resources? | type) == "array" then
          .resources |= map(select(.address as $address | ($evidence | index($address)) == null))
        else
          .
        end
      )
    | .resource_changes |= map(select(.address as $address | ($evidence | index($address)) == null))
  ' <<< "${plan}"
}

make_legacy_steady_conditions() {
  local plan="$1"
  local address legacy_expression
  for address in "${STEADY_ADDRESSES[@]}"; do
    legacy_expression="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).size() > 0 && ($(expected_expression_for_address "${address}"))"
    plan="$(set_expression "${plan}" "${address}" "${legacy_expression}")"
  done
  printf '%s\n' "${plan}"
}

build_owner_removal_plan() {
  local plan="$1"
  local owner_address='google_project_iam_member.project_creator_owner["user:yousef@felidai.com"]'
  local removal_record='terraform_data.project_creator_owner_removal_record["user:yousef@felidai.com"]'
  local address policy_data owner_resource
  for address in "${STATE_POLICY_ADDRESSES[@]}"; do
    policy_data="$(build_state_policy_resource "${address}" true | jq -r '.values.policy_data')"
    plan="$(set_planned_resource_string "${plan}" "${address}" policy_data "${policy_data}")"
  done
  plan="$(remove_prior_resource "${plan}" "${removal_record}")"
  plan="$(set_planned_resource_string "${plan}" "${removal_record}" output '')"
  plan="$(set_change_actions "${plan}" "${removal_record}" '["create"]')"
  owner_resource="$(jq -cn --arg address "${owner_address}" --arg project "${PROJECT_ID}" --arg member "${HUMAN_MEMBER}" '{address: $address, mode: "managed", type: "google_project_iam_member", values: {project: $project, role: "roles/owner", member: $member}}')"
  plan="$(add_prior_resource "${plan}" "${owner_resource}")"
  jq -c \
    --arg address "${owner_address}" \
    --arg project "${PROJECT_ID}" \
    --arg member "${HUMAN_MEMBER}" \
    '.resource_changes += [{
      address: $address,
      mode: "managed",
      type: "google_project_iam_member",
      change: {
        actions: ["delete"],
        before: {project: $project, role: "roles/owner", member: $member},
        after: null
      }
    }]' <<< "${plan}"
}

readonly RECOVERY_PLAN_JSON="$(build_plan_json recovery)"
readonly STEADY_PLAN_JSON="$(build_plan_json steady)"
readonly COMPLETE_PLAN_JSON="$(build_plan_json complete)"
readonly CLEAN_COMPLETE_PLAN_JSON="${STEADY_PLAN_JSON}"
readonly EARLY_COMPLETE_PLAN_JSON="$(remove_all_phase_evidence "${CLEAN_COMPLETE_PLAN_JSON}")"
COMPLETE_RECOVERY_PLAN_JSON="${COMPLETE_PLAN_JSON}"
for address in "${RECOVERY_CHANGE_ADDRESSES[@]}"; do
  COMPLETE_RECOVERY_PLAN_JSON="$(set_change_actions "${COMPLETE_RECOVERY_PLAN_JSON}" "${address}" '["create"]')"
done
readonly COMPLETE_RECOVERY_PLAN_JSON
readonly LEGACY_RECOVERY_PLAN_JSON="$(make_legacy_steady_conditions "${COMPLETE_RECOVERY_PLAN_JSON}")"
readonly LEGACY_STEADY_PLAN_JSON="$(make_legacy_steady_conditions "${STEADY_PLAN_JSON}")"
readonly OWNER_REMOVAL_PLAN_JSON="$(build_owner_removal_plan "${CLEAN_COMPLETE_PLAN_JSON}")"
COMPLETE_MIGRATION_PLAN_JSON="${COMPLETE_PLAN_JSON}"
for address in "${STATE_POLICY_ADDRESSES[@]}"; do
  migration_policy_data="$(build_state_policy_resource "${address}" true | jq -r '.values.policy_data')"
  COMPLETE_MIGRATION_PLAN_JSON="$(set_planned_resource_string "${COMPLETE_MIGRATION_PLAN_JSON}" "${address}" policy_data "${migration_policy_data}")"
done
readonly COMPLETE_MIGRATION_PLAN_JSON
COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON="${COMPLETE_PLAN_JSON}"
COMPLETE_STATE_POLICY_REMOVAL_PLAN_JSON="${COMPLETE_PLAN_JSON}"
for address in "${STATE_POLICY_ADDRESSES[@]}"; do
  COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON="$(remove_planned_resource "${COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON}" "${address}")"
  COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON="$(remove_prior_resource "${COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON}" "${address}")"
  COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON="$(jq -c --arg address "${address}" '.resource_changes |= map(select(.address != $address))' <<< "${COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON}")"
  COMPLETE_STATE_POLICY_REMOVAL_PLAN_JSON="$(remove_planned_resource "${COMPLETE_STATE_POLICY_REMOVAL_PLAN_JSON}" "${address}")"
  COMPLETE_STATE_POLICY_REMOVAL_PLAN_JSON="$(set_change_actions "${COMPLETE_STATE_POLICY_REMOVAL_PLAN_JSON}" "${address}" '["delete"]')"
done
readonly COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON COMPLETE_STATE_POLICY_REMOVAL_PLAN_JSON

TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/seen-registry-iam-lint-test.XXXXXX")"
cleanup_test_root() { rm -rf -- "${TEST_ROOT}"; }
trap cleanup_test_root EXIT
readonly FAKE_BIN="${TEST_ROOT}/bin"
readonly FAKE_ROOT="${TEST_ROOT}/root"
readonly FAKE_PLAN_PATH="${TEST_ROOT}/saved.tfplan"
readonly FAKE_GCLOUD_LOG="${TEST_ROOT}/gcloud.log"
mkdir -p "${FAKE_BIN}" "${FAKE_ROOT}"
: > "${FAKE_PLAN_PATH}"
: > "${FAKE_GCLOUD_LOG}"
cp "${TEST_SCRIPT}" "${FAKE_BIN}/tofu"
cp "${TEST_SCRIPT}" "${FAKE_BIN}/gcloud"
chmod 0700 "${FAKE_BIN}/tofu" "${FAKE_BIN}/gcloud"

export FAKE_GCLOUD_LOG FAKE_PLAN_JSON FAKE_PLAN_PATH
export FAKE_GCLOUD_RESULT=empty-object
export FAKE_GCLOUD_VERSION=576.0.0
export FAKE_GCLOUD_VERSION_RESULT=success
export FAKE_PRIVATE_SENTINEL="${PRIVATE_SENTINEL}"
export FAKE_TOFU_RESULT=success
export FAKE_TOFU_STDERR=
export PATH="${FAKE_BIN}:${ORIGINAL_PATH}"

assert_lint_success() {
  local expected_output="$1" expected_lint_count="$2" mode="$3" output
  : > "${FAKE_GCLOUD_LOG}"
  output="$(bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" "${mode}" 2>&1)" || fail "an accepted ${mode} lint case failed: ${output}"
  [[ "${output}" == "${expected_output}" ]] || fail "an accepted ${mode} lint case returned the wrong output"
  [[ "${output}" != *"${PRIVATE_SENTINEL}"* ]] || fail "an accepted lint case exposed a private diagnostic"
  [[ "$(wc -l < "${FAKE_GCLOUD_LOG}")" -eq "${expected_lint_count}" ]] || fail "an accepted ${mode} lint case invoked the official linter the wrong number of times"
}

assert_rejected() {
  local expected_message="$1"
  shift
  local output
  if output="$("$@" 2>&1)"; then fail "a rejected lint case unexpectedly succeeded"; fi
  [[ "${output}" == *"${expected_message}"* ]] || fail "a rejected lint case returned the wrong message: ${output}"
  [[ "${output}" != *"${PRIVATE_SENTINEL}"* ]] || fail "a rejected lint case exposed a private diagnostic"
  [[ "${output}" != *"${RECOVERY_EXPIRY}"* ]] || fail "a rejected lint case exposed a recovery expiry"
}

FAKE_PLAN_JSON="${RECOVERY_PLAN_JSON}"; export FAKE_PLAN_JSON
for accepted_shape in empty-array empty-object empty-results; do
  export FAKE_GCLOUD_RESULT="${accepted_shape}"
  assert_lint_success 'Official IAM lint accepted 4 unique condition(s) across 4 validated recovery-mode binding(s).' 4 recovery
done
export FAKE_GCLOUD_RESULT=empty-object

FAKE_PLAN_JSON="${STEADY_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 27 unique condition(s) across 27 validated steady-mode binding(s).' 27 steady
FAKE_PLAN_JSON="${COMPLETE_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 31 unique condition(s) across 31 validated complete-mode binding(s).' 31 complete
FAKE_PLAN_JSON="${COMPLETE_MIGRATION_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 31 unique condition(s) across 31 validated complete-mode binding(s).' 31 complete
FAKE_PLAN_JSON="${COMPLETE_WITHOUT_STATE_POLICIES_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 31 unique condition(s) across 31 validated complete-mode binding(s).' 31 complete
FAKE_PLAN_JSON="${CLEAN_COMPLETE_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 27 unique condition(s) across 27 validated complete-mode binding(s).' 27 complete
FAKE_PLAN_JSON="${EARLY_COMPLETE_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 27 unique condition(s) across 27 validated complete-mode binding(s).' 27 complete
FAKE_PLAN_JSON="${LEGACY_RECOVERY_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 4 unique condition(s) across 4 validated recovery-mode binding(s).' 4 recovery

# Recovery ignores untargeted legacy/no-op steady expressions; ordinary full-plan modes remain exact.
FAKE_PLAN_JSON="${LEGACY_RECOVERY_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_rejected 'steady setter condition does not match its exact authorization contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
FAKE_PLAN_JSON="${LEGACY_STEADY_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_rejected 'steady setter condition does not match its exact authorization contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

# Complete and steady modes require full-plan coverage of both contracts and all steady bindings.
FAKE_PLAN_JSON="$(jq -c --arg address "${PHASE_CONTRACT_ADDRESSES[0]}" '.resource_changes |= map(select(.address != $address))' <<< "${COMPLETE_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'require full managed resource-change coverage' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
FAKE_PLAN_JSON="$(jq -c --arg address "${PHASE_CONTRACT_ADDRESSES[1]}" '.resource_changes |= map(select(.address != $address))' <<< "${STEADY_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'require full managed resource-change coverage' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(jq -c --arg address "${STEADY_ADDRESSES[0]}" '.resource_changes |= map(select(.address != $address))' <<< "${COMPLETE_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'require full managed resource-change coverage' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete

# Complete reconciliation validates both full authoritative state policies, with
# either the base service-account bindings or one exact shared-expiry human
# migration pair on each bucket.
FAKE_PLAN_JSON="${COMPLETE_STATE_POLICY_REMOVAL_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_rejected 'forbids removing or omitting previously managed authoritative state policies' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
complete_policy_probe="${STATE_POLICY_ADDRESSES[0]}"
complete_base_policy_data="$(build_state_policy_resource "${complete_policy_probe}" false | jq -r '.values.policy_data')"
complete_extra_role_policy_data="$(jq -r --arg member 'serviceAccount:unreviewed@seen-registry-prod-476219.iam.gserviceaccount.com' '(.bindings += [{role: "roles/storage.admin", members: [$member]}]) | tojson' <<< "${complete_base_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${COMPLETE_PLAN_JSON}" "${complete_policy_probe}" policy_data "${complete_extra_role_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'complete authoritative state policy contains an extra or missing binding' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
complete_extra_principal_policy_data="$(jq -r --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateReader" --arg member 'serviceAccount:unreviewed@seen-registry-prod-476219.iam.gserviceaccount.com' '(.bindings[] | select(.role == $role) | .members) += [$member] | tojson' <<< "${complete_base_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${COMPLETE_PLAN_JSON}" "${complete_policy_probe}" policy_data "${complete_extra_principal_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'contains an unreviewed principal or role' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
complete_broadened_writer_policy_data="$(jq -r --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateWriter" '(.bindings[] | select(.role == $role) | .condition.expression) = "true" | tojson' <<< "${complete_base_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${COMPLETE_PLAN_JSON}" "${complete_policy_probe}" policy_data "${complete_broadened_writer_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'state-writer condition does not match the exact state objects' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
FAKE_PLAN_JSON="$(set_planned_resource_string "${COMPLETE_PLAN_JSON}" "${STATE_POLICY_ADDRESSES[0]}" policy_data "$(build_state_policy_resource "${STATE_POLICY_ADDRESSES[0]}" true | jq -r '.values.policy_data')")"; export FAKE_PLAN_JSON
assert_rejected 'requires both authoritative state policies to use one exact migration-access shape' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
different_migration_expiry='2026-07-22T12:35:56Z'
different_expiry_policy_data="$(build_state_policy_resource "${STATE_POLICY_ADDRESSES[1]}" true | jq -r '.values.policy_data' | sed "s/${MIGRATION_EXPIRY}/${different_migration_expiry}/g")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${COMPLETE_MIGRATION_PLAN_JSON}" "${STATE_POLICY_ADDRESSES[1]}" policy_data "${different_expiry_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'human state-policy bindings do not share one expiry' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete

# Owner removal is isolated from state-policy repair and retains exact time-bounded human migration access.
FAKE_PLAN_JSON="${OWNER_REMOVAL_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 27 unique condition(s) across 27 validated complete-mode binding(s).' 27 complete
owner_address='google_project_iam_member.project_creator_owner["user:yousef@felidai.com"]'
removal_record_address='terraform_data.project_creator_owner_removal_record["user:yousef@felidai.com"]'
FAKE_PLAN_JSON="$(set_change_actions "${OWNER_REMOVAL_PLAN_JSON}" "${owner_address}" '["no-op"]')"; export FAKE_PLAN_JSON
assert_rejected 'requires the exact Owner-removal change pair' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
FAKE_PLAN_JSON="$(set_change_actions "${OWNER_REMOVAL_PLAN_JSON}" "${removal_record_address}" '["no-op"]')"; export FAKE_PLAN_JSON
assert_rejected 'requires the exact Owner-removal change pair' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
FAKE_PLAN_JSON="$(jq -c --arg address "${owner_address}" '(.resource_changes[] | select(.address == $address) | .change.before.role) = "roles/viewer"' <<< "${OWNER_REMOVAL_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'prior grant does not match the reviewed Owner' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
FAKE_PLAN_JSON="$(set_change_actions "${OWNER_REMOVAL_PLAN_JSON}" "${STATE_POLICY_ADDRESSES[0]}" '["update"]')"; export FAKE_PLAN_JSON
assert_rejected 'must isolate the Owner delete and removal-record create' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
owner_policy_data="$(build_state_policy_resource "${STATE_POLICY_ADDRESSES[0]}" true | jq -r '.values.policy_data')"
owner_policy_without_writer="$(jq -r --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateWriter" --arg human "${HUMAN_MEMBER}" '(.bindings |= map(select(.role != $role or .members != [$human]))) | tojson' <<< "${owner_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${OWNER_REMOVAL_PLAN_JSON}" "${STATE_POLICY_ADDRESSES[0]}" policy_data "${owner_policy_without_writer}")"; export FAKE_PLAN_JSON
assert_rejected 'complete authoritative state policy contains an extra or missing binding' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
owner_policy_with_extra_role="$(jq -r --arg member 'serviceAccount:unreviewed@seen-registry-prod-476219.iam.gserviceaccount.com' '(.bindings += [{role: "roles/storage.admin", members: [$member]}]) | tojson' <<< "${owner_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${OWNER_REMOVAL_PLAN_JSON}" "${STATE_POLICY_ADDRESSES[0]}" policy_data "${owner_policy_with_extra_role}")"; export FAKE_PLAN_JSON
assert_rejected 'complete authoritative state policy contains an extra or missing binding' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
owner_policy_with_extra_principal="$(jq -r --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateReader" --arg apply "${APPLY_MEMBER}" --arg plan "${PLAN_MEMBER}" --arg member 'serviceAccount:unreviewed@seen-registry-prod-476219.iam.gserviceaccount.com' '(.bindings[] | select(.role == $role and ((.members | sort) == ([$apply, $plan] | sort))) | .members) += [$member] | tojson' <<< "${owner_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${OWNER_REMOVAL_PLAN_JSON}" "${STATE_POLICY_ADDRESSES[0]}" policy_data "${owner_policy_with_extra_principal}")"; export FAKE_PLAN_JSON
assert_rejected 'contains an unreviewed principal or role' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
owner_policy_without_locker="$(jq -r --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateLocker" '(.bindings |= map(select(.role != $role))) | tojson' <<< "${owner_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${OWNER_REMOVAL_PLAN_JSON}" "${STATE_POLICY_ADDRESSES[0]}" policy_data "${owner_policy_without_locker}")"; export FAKE_PLAN_JSON
assert_rejected 'complete authoritative state policy contains an extra or missing binding' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
owner_policy_with_broadened_writer="$(jq -r --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateWriter" --arg apply "${APPLY_MEMBER}" '(.bindings[] | select(.role == $role and .members == [$apply]) | .condition.expression) = "true" | tojson' <<< "${owner_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${OWNER_REMOVAL_PLAN_JSON}" "${STATE_POLICY_ADDRESSES[0]}" policy_data "${owner_policy_with_broadened_writer}")"; export FAKE_PLAN_JSON
assert_rejected 'state-writer condition does not match the exact state objects' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
owner_extra_resource="$(jq -cn '{address: "terraform_data.owner_removal_race", mode: "managed", type: "terraform_data", values: {input: "unreviewed"}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${OWNER_REMOVAL_PLAN_JSON}" "${owner_extra_resource}" '["create"]')"; export FAKE_PLAN_JSON
assert_rejected 'must isolate the Owner delete and removal-record create' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete

# Complete mode accepts either zero or exactly four recovery bindings.
FAKE_PLAN_JSON="$(remove_planned_resource "${COMPLETE_PLAN_JSON}" "${RECOVERY_ADDRESSES[0]}")"
FAKE_PLAN_JSON="$(jq -c --arg address "${RECOVERY_ADDRESSES[0]}" '.resource_changes |= map(select(.address != $address))' <<< "${FAKE_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'does not contain the exact recovery binding set' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete

# Every mode validates any planned phase evidence, while origin-phase creates may have unknown output.
origin_evidence="$(build_evidence_resource "${STEADY_EVIDENCE_ADDRESSES[0]}")"
origin_evidence="$(jq -c '.values.output = null' <<< "${origin_evidence}")"
FAKE_PLAN_JSON="$(add_managed_resource "${EARLY_COMPLETE_PLAN_JSON}" "${origin_evidence}" '["create"]')"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 27 unique condition(s) across 27 validated complete-mode binding(s).' 27 complete

for invalid_evidence_address in "${STEADY_EVIDENCE_ADDRESSES[2]}" "${STEADY_EVIDENCE_ADDRESSES[3]}"; do
  invalid_evidence="$(build_evidence_resource "${invalid_evidence_address}")"
  invalid_evidence="$(jq -c '.values.input = "phase-evidence-unproven" | .values.output = null' <<< "${invalid_evidence}")"
  FAKE_PLAN_JSON="$(add_managed_resource "${EARLY_COMPLETE_PLAN_JSON}" "${invalid_evidence}" '["create"]')"; export FAKE_PLAN_JSON
  assert_rejected 'planned phase-evidence record has an invalid input sentinel' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete
done

extra_planned_evidence="$(build_evidence_resource "${STEADY_EVIDENCE_ADDRESSES[0]}")"
extra_planned_evidence="$(jq -c --arg address 'terraform_data.recovery_reconciliation_record["user:other@example.com"]' '.address = $address | .values.output = null' <<< "${extra_planned_evidence}")"
FAKE_PLAN_JSON="$(add_managed_resource "${EARLY_COMPLETE_PLAN_JSON}" "${extra_planned_evidence}" '["create"]')"; export FAKE_PLAN_JSON
assert_rejected 'saved plan contains an unknown phase-evidence address' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete

# Steady mode trusts four immutable, already-applied records and an IAM-clean prior state.
evidence_probe="${STEADY_EVIDENCE_ADDRESSES[0]}"
FAKE_PLAN_JSON="$(jq -c 'del(.prior_state)' <<< "${STEADY_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'require concrete prior state for full-plan coverage' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(remove_prior_resource "${STEADY_PLAN_JSON}" "${evidence_probe}")"; export FAKE_PLAN_JSON
assert_rejected 'requires the exact immutable evidence records in prior state' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(remove_planned_resource "${STEADY_PLAN_JSON}" "${evidence_probe}")"; export FAKE_PLAN_JSON
assert_rejected 'requires the exact immutable evidence records in planned values' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(set_prior_resource_string "${STEADY_PLAN_JSON}" "${evidence_probe}" input recovery-reconciliation-unproven)"; export FAKE_PLAN_JSON
assert_rejected 'prior state evidence record has an invalid sentinel' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(set_planned_resource_string "${STEADY_PLAN_JSON}" "${evidence_probe}" output recovery-reconciliation-unproven)"; export FAKE_PLAN_JSON
assert_rejected 'planned values evidence record has an invalid sentinel' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(remove_prior_resource "${STEADY_PLAN_JSON}" "${evidence_probe}")"
FAKE_PLAN_JSON="$(set_change_actions "${FAKE_PLAN_JSON}" "${evidence_probe}" '["create"]')"; export FAKE_PLAN_JSON
assert_rejected 'steady mode forbids immutable evidence record changes' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(set_change_actions "${STEADY_PLAN_JSON}" "${STEADY_EVIDENCE_ADDRESSES[1]}" '["update"]')"; export FAKE_PLAN_JSON
assert_rejected 'steady mode forbids immutable evidence record changes' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

extra_evidence="$(build_evidence_resource 'terraform_data.recovery_reconciliation_record["user:yousef@felidai.com"]')"
extra_evidence="$(jq -c --arg address 'terraform_data.recovery_reconciliation_record["user:other@example.com"]' '.address = $address' <<< "${extra_evidence}")"
FAKE_PLAN_JSON="$(add_prior_resource "${STEADY_PLAN_JSON}" "${extra_evidence}")"; export FAKE_PLAN_JSON
FAKE_PLAN_JSON="$(jq -c --arg address "$(jq -r '.address' <<< "${extra_evidence}")" '.resource_changes += [{address: $address, mode: "managed", type: "terraform_data", change: {actions: ["no-op"]}}]' <<< "${FAKE_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'requires the exact immutable evidence records in prior state' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

declare -ar PRIOR_HUMAN_IAM_ADDRESSES=(
  'google_project_iam_member.project_creator_owner["user:yousef@felidai.com"]'
  "${RECOVERY_ADDRESSES[@]}"
)
for prior_human_address in "${PRIOR_HUMAN_IAM_ADDRESSES[@]}"; do
  prior_human_resource="$(jq -cn --arg address "${prior_human_address}" '{address: $address, mode: "managed", type: "google_project_iam_member", values: {project: "seen-registry-prod-476219", role: "roles/owner", member: "user:yousef@felidai.com"}}')"
  FAKE_PLAN_JSON="$(add_prior_resource "${STEADY_PLAN_JSON}" "${prior_human_resource}")"
  FAKE_PLAN_JSON="$(jq -c --arg address "${prior_human_address}" '.resource_changes += [{address: $address, mode: "managed", type: "google_project_iam_member", change: {actions: ["no-op"]}}]' <<< "${FAKE_PLAN_JSON}")"; export FAKE_PLAN_JSON
  assert_rejected 'requires Owner and project-level recovery IAM resources absent from prior state' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
done

# Planned steady IAM rejects every user/group grant while prior migration-policy grants remain allowed.
for planned_human_principal in 'user:other@example.com' 'group:operators@example.com'; do
  planned_human_resource="$(jq -cn --arg member "${planned_human_principal}" '{address: "google_storage_bucket_iam_member.direct_human", mode: "managed", type: "google_storage_bucket_iam_member", values: {bucket: "fixture", role: "roles/storage.objectViewer", member: $member}}')"
  FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${planned_human_resource}" '["create"]')"; export FAKE_PLAN_JSON
  assert_rejected 'steady mode forbids human principals in planned IAM grants' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
done

unknown_member_resource="$(jq -cn '{address: "google_storage_bucket_iam_member.unknown_principal", mode: "managed", type: "google_storage_bucket_iam_member", values: {bucket: "fixture", role: "roles/storage.objectViewer"}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${unknown_member_resource}" '["create"]')"; export FAKE_PLAN_JSON
assert_rejected 'requires every planned IAM member or binding role and principal to be concrete' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
unknown_members_resource="$(jq -cn '{address: "google_storage_bucket_iam_binding.unknown_principals", mode: "managed", type: "google_storage_bucket_iam_binding", values: {bucket: "fixture", role: "roles/storage.objectViewer"}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${unknown_members_resource}" '["create"]')"; export FAKE_PLAN_JSON
assert_rejected 'requires every planned IAM member or binding role and principal to be concrete' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
unknown_role_resource="$(jq -cn --arg member "${APPLY_MEMBER}" '{address: "google_storage_bucket_iam_member.unknown_role", mode: "managed", type: "google_storage_bucket_iam_member", values: {bucket: "fixture", member: $member}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${unknown_role_resource}" '["create"]')"; export FAKE_PLAN_JSON
assert_rejected 'requires every planned IAM member or binding role and principal to be concrete' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

policy_probe="${STATE_POLICY_ADDRESSES[0]}"
human_policy_data="$(build_state_policy_resource "${policy_probe}" true | jq -r '.values.policy_data')"
FAKE_PLAN_JSON="$(set_planned_resource_string "${STEADY_PLAN_JSON}" "${policy_probe}" policy_data "${human_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'steady mode forbids human principals in planned authoritative IAM policies' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(jq -c --arg address "${policy_probe}" '.planned_values.root_module |= walk(if type == "object" and .address? == $address then .values.policy_data = null else . end)' <<< "${STEADY_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'requires concrete parseable planned authoritative IAM policy data' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

# Both planned authoritative policies must have exactly the reviewed service-account bindings and object conditions.
clean_policy_data="$(build_state_policy_resource "${policy_probe}" false | jq -r '.values.policy_data')"
empty_policy_data='{"bindings":[]}'
FAKE_PLAN_JSON="$(set_planned_resource_string "${STEADY_PLAN_JSON}" "${policy_probe}" policy_data "${empty_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'contains an extra or missing binding' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
extra_service_policy_data="$(jq -r --arg member 'serviceAccount:unreviewed@seen-registry-prod-476219.iam.gserviceaccount.com' '(.bindings[0].members += [$member]) | tojson' <<< "${clean_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${STEADY_PLAN_JSON}" "${policy_probe}" policy_data "${extra_service_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'contains an unreviewed principal' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
extra_role_policy_data="$(jq -r --arg member "${APPLY_MEMBER}" '(.bindings += [{role: "roles/storage.objectViewer", members: [$member]}]) | tojson' <<< "${clean_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${STEADY_PLAN_JSON}" "${policy_probe}" policy_data "${extra_role_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'contains an extra or missing binding' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
missing_writer_policy_data="$(jq -r --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateWriter" '(.bindings |= map(select(.role != $role))) | tojson' <<< "${clean_policy_data}")"
FAKE_PLAN_JSON="$(set_planned_resource_string "${STEADY_PLAN_JSON}" "${policy_probe}" policy_data "${missing_writer_policy_data}")"; export FAKE_PLAN_JSON
assert_rejected 'contains an extra or missing binding' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

# Recovery is a mechanically exact five-resource managed change set.
FAKE_PLAN_JSON="$(set_change_actions "${RECOVERY_PLAN_JSON}" "${RECOVERY_CHANGE_ADDRESSES[0]}" '["no-op"]')"; export FAKE_PLAN_JSON
assert_rejected 'recovery mode requires exactly the five reviewed managed changes' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
for invalid_recovery_actions in '["update"]' '["delete"]' '["delete","create"]'; do
  FAKE_PLAN_JSON="$(set_change_actions "${RECOVERY_PLAN_JSON}" "${RECOVERY_CHANGE_ADDRESSES[0]}" "${invalid_recovery_actions}")"; export FAKE_PLAN_JSON
  assert_rejected 'requires each reviewed resource to be an exact create' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
done
extra_change_resource="$(jq -cn '{address: "terraform_data.unrelated", mode: "managed", type: "terraform_data", values: {input: "fixture"}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${RECOVERY_PLAN_JSON}" "${extra_change_resource}" '["create"]')"; export FAKE_PLAN_JSON
assert_rejected 'recovery mode requires exactly the five reviewed managed changes' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
FAKE_PLAN_JSON="$(jq -c '.resource_changes += [{address: "data.google_project.fixture", mode: "data", type: "google_project", change: {actions: ["read"]}}]' <<< "${RECOVERY_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_lint_success 'Official IAM lint accepted 4 unique condition(s) across 4 validated recovery-mode binding(s).' 4 recovery

# The fifth recovery resource is the exact bucket-policy reader role.
FAKE_PLAN_JSON="$(set_resource_json "${RECOVERY_PLAN_JSON}" 'google_project_iam_custom_role.state_bucket_policy_reader[0]' permissions '["storage.buckets.getIamPolicy"]')"; export FAKE_PLAN_JSON
assert_rejected 'reviewed custom IAM role does not match its exact permission contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
FAKE_PLAN_JSON="$(set_resource_string "${RECOVERY_PLAN_JSON}" 'google_project_iam_custom_role.state_bucket_policy_reader[0]' role_id seenRegistryStateReader)"; export FAKE_PLAN_JSON
assert_rejected 'reviewed custom IAM role does not match its exact permission contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
FAKE_PLAN_JSON="$(set_resource_string "${RECOVERY_PLAN_JSON}" 'google_project_iam_custom_role.state_bucket_policy_reader[0]' project wrong-project)"; export FAKE_PLAN_JSON
assert_rejected 'reviewed custom IAM role does not match its exact permission contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
FAKE_PLAN_JSON="$(jq -c --arg address 'google_project_iam_custom_role.state_bucket_policy_reader[0]' 'walk(if type == "object" and .address? == $address and (.values? | type) == "object" then .type = "terraform_data" else . end)' <<< "${RECOVERY_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'reviewed custom IAM role address uses the wrong resource type' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery

# Recovery binding scope, role, member, expiry, and hasOnly semantics are exact.
probe="${RECOVERY_READ_ADDRESSES[0]}"
FAKE_PLAN_JSON="$(set_expression "${RECOVERY_PLAN_JSON}" "${probe}" "$(expected_expression_for_address "${probe}") || true")"; export FAKE_PLAN_JSON
assert_rejected 'recovery binding condition does not match its exact authorization contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
FAKE_PLAN_JSON="$(set_expression "${RECOVERY_PLAN_JSON}" "${probe}" "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${PROJECT_ID}-bootstrap-tofu-state' && request.time < timestamp('not-a-time')")"; export FAKE_PLAN_JSON
assert_rejected 'recovery binding expiry is not a canonical RFC3339 timestamp' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
probe="${RECOVERY_READ_ADDRESSES[1]}"
other_expiry='2026-07-21T12:35:56Z'
FAKE_PLAN_JSON="$(set_expression "${RECOVERY_PLAN_JSON}" "${probe}" "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${PROJECT_ID}-prod-tofu-state' && request.time < timestamp('${other_expiry}')")"; export FAKE_PLAN_JSON
assert_rejected 'reviewed IAM binding condition does not match its exact authorization contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
probe="${RECOVERY_SETTER_ADDRESSES[0]}"
FAKE_PLAN_JSON="$(set_expression "${RECOVERY_PLAN_JSON}" "${probe}" "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${PROJECT_ID}-bootstrap-tofu-state' && (true || ${STATE_STORAGE_MODIFIED_ROLES}) && request.time < timestamp('${RECOVERY_EXPIRY}')")"; export FAKE_PLAN_JSON
assert_rejected 'reviewed IAM binding condition does not match its exact authorization contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
for field_value in 'project wrong-project' 'member user:wrong@example.com' 'role roles/owner'; do
  field="${field_value%% *}"; value="${field_value#* }"
  FAKE_PLAN_JSON="$(set_resource_string "${RECOVERY_PLAN_JSON}" "${probe}" "${field}" "${value}")"; export FAKE_PLAN_JSON
  assert_rejected 'reviewed IAM binding uses the wrong' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
done
FAKE_PLAN_JSON="$(set_resource_json "${RECOVERY_PLAN_JSON}" "${probe}" condition '[]')"; export FAKE_PLAN_JSON
assert_rejected 'reviewed IAM binding must have one concrete condition' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery

# Every steady expression is reconstructed, including one shared numeric project number.
declare -ar EXPRESSION_REPRESENTATIVES=(
  'google_project_iam_member.infrastructure_project_iam[0]'
  'google_project_iam_member.infrastructure_kms_policy_setter["seen-registry-prod-releases"]'
  'google_project_iam_member.infrastructure_run_policy_setter["job"]'
  'google_project_iam_member.infrastructure_run_policy_setter["service"]'
  'google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-github-app-id"]'
  'google_project_iam_member.infrastructure_registry_storage_policy_setter["seen-registry-prod-476219-seen-registry-prod-backup"]'
  'google_project_iam_member.infrastructure_state_storage_policy_setter["bootstrap"]'
)
for probe in "${EXPRESSION_REPRESENTATIVES[@]}"; do
  FAKE_PLAN_JSON="$(set_expression "${STEADY_PLAN_JSON}" "${probe}" "$(expected_expression_for_address "${probe}") || true")"; export FAKE_PLAN_JSON
  assert_rejected 'condition does not match its exact authorization contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
done
secret_probe='google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-github-app-id"]'
FAKE_PLAN_JSON="$(set_expression "${STEADY_PLAN_JSON}" "${secret_probe}" "${PROJECT_MODIFIED_ROLES}")"; export FAKE_PLAN_JSON
assert_rejected 'steady setter condition does not match its exact authorization contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
second_secret='google_project_iam_member.infrastructure_secret_policy_setter["seen-registry-prod-github-app-private-key"]'
FAKE_PLAN_JSON="$(set_expression "${STEADY_PLAN_JSON}" "${second_secret}" "$(expected_expression_for_address "${second_secret}" | sed "s/projects\/${PROJECT_NUMBER}/projects\/9999999/")")"; export FAKE_PLAN_JSON
assert_rejected 'secret setters do not use one production project number' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
FAKE_PLAN_JSON="$(set_expression "${STEADY_PLAN_JSON}" "${secret_probe}" "$(expected_expression_for_address "${secret_probe}" | sed "s/projects\/${PROJECT_NUMBER}/projects\/0${PROJECT_NUMBER}/")")"; export FAKE_PLAN_JSON
assert_rejected 'secret setter does not use a concrete numeric production project number' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

# Missing/duplicate/unreviewed binding addresses and setter-role bypasses fail closed.
FAKE_PLAN_JSON="$(jq -c --arg address "${STEADY_ADDRESSES[0]}" 'walk(if type == "object" and (.resources? | type) == "array" then .resources |= map(select(.address != $address)) else . end) | .resource_changes |= map(select(.address != $address))' <<< "${STEADY_PLAN_JSON}")"; export FAKE_PLAN_JSON
assert_rejected 'does not contain the exact required IAM binding set' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
unknown_binding="$(jq -cn --arg role 'projects/9999999/roles/seenRegistryStorageIamApply' '{address: "google_storage_bucket_iam_binding.bypass", mode: "managed", type: "google_storage_bucket_iam_binding", values: {bucket: "fixture", role: $role, members: ["serviceAccount:attacker@example.iam.gserviceaccount.com"], condition: []}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${unknown_binding}")"; export FAKE_PLAN_JSON
assert_rejected 'grants a setter role outside its reviewed binding address' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
unknown_modified="$(jq -cn --arg expression "${PROJECT_MODIFIED_ROLES}" '{address: "google_project_iam_member.bypass", mode: "managed", type: "google_project_iam_member", values: {project: "fixture", role: "roles/viewer", member: "serviceAccount:attacker@example.iam.gserviceaccount.com", condition: [{expression: $expression}]}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${unknown_modified}")"; export FAKE_PLAN_JSON
assert_rejected 'unreviewed modified-grants condition' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
unknown_family="$(jq -cn --arg role "projects/${PROJECT_ID}/roles/seenRegistryStorageIamApply" '{address: "google_project_iam_member.temporary_human_state_bucket_policy_access[\"other\"]", mode: "managed", type: "google_project_iam_member", values: {project: "seen-registry-prod-476219", role: $role, member: "user:yousef@felidai.com", condition: []}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${COMPLETE_PLAN_JSON}" "${unknown_family}")"; export FAKE_PLAN_JSON
assert_rejected 'unreviewed IAM binding address' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" complete

# Setter roles cannot hide inside authoritative policy_data.
policy_data="$(jq -cn --arg role 'projects/9999999/roles/seenRegistryStorageIamApply' '{bindings: [{role: $role, members: ["serviceAccount:attacker@example.iam.gserviceaccount.com"]}]}' )"
policy_resource="$(jq -cn --arg policy_data "${policy_data}" '{address: "google_storage_bucket_iam_policy.bypass", mode: "managed", type: "google_storage_bucket_iam_policy", values: {bucket: "fixture", policy_data: $policy_data}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${policy_resource}")"; export FAKE_PLAN_JSON
assert_rejected 'authoritative IAM policy contains a setter role' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

# Known custom setters have exact one-permission contracts; new setter roles are rejected.
custom_probe='google_project_iam_custom_role.resource_iam_setter["storage"]'
FAKE_PLAN_JSON="$(set_resource_json "${STEADY_PLAN_JSON}" "${custom_probe}" permissions '["storage.buckets.setIamPolicy","storage.objects.delete"]')"; export FAKE_PLAN_JSON
assert_rejected 'reviewed custom IAM role does not match its exact permission contract' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
unknown_custom="$(jq -cn '{address: "google_project_iam_custom_role.bypass[0]", mode: "managed", type: "google_project_iam_custom_role", values: {project: "seen-registry-prod-476219", role_id: "bypass", permissions: ["storage.buckets.setIamPolicy"]}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${unknown_custom}")"; export FAKE_PLAN_JSON
assert_rejected 'unreviewed custom IAM authority role' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
unknown_known_id="$(jq -cn '{address: "google_project_iam_custom_role.renamed[0]", mode: "managed", type: "google_project_iam_custom_role", values: {project: "seen-registry-prod-476219", role_id: "seenRegistryStorageIamApply", permissions: ["storage.buckets.get"]}}')"
FAKE_PLAN_JSON="$(add_managed_resource "${STEADY_PLAN_JSON}" "${unknown_known_id}")"; export FAKE_PLAN_JSON
assert_rejected 'unreviewed custom IAM authority role' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady

# Protected steady mode cannot mutate Owner or either temporary binding family.
for forbidden_address in \
  'google_project_iam_member.project_creator_owner["user:yousef@felidai.com"]' \
  'google_project_iam_member.temporary_human_state_bucket_policy_read_access["bootstrap"]' \
  'google_project_iam_member.temporary_human_state_bucket_policy_access["production"]'; do
  forbidden_resource="$(jq -cn --arg address "${forbidden_address}" --arg project "${PROJECT_ID}" --arg member "${HUMAN_MEMBER}" '{address: $address, mode: "managed", type: "google_project_iam_member", values: {project: $project, role: "roles/owner", member: $member}}')"
  FAKE_PLAN_JSON="$(add_prior_resource "${STEADY_PLAN_JSON}" "${forbidden_resource}")"
  FAKE_PLAN_JSON="$(jq -c --arg address "${forbidden_address}" --arg project "${PROJECT_ID}" --arg member "${HUMAN_MEMBER}" '.resource_changes += [{address: $address, mode: "managed", type: "google_project_iam_member", change: {actions: ["delete"], before: {project: $project, role: "roles/owner", member: $member}, after: null}}]' <<< "${FAKE_PLAN_JSON}")"; export FAKE_PLAN_JSON
  assert_rejected 'steady mode forbids Owner or temporary recovery binding changes' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" steady
done

# Official-linter/version failures and plan diagnostics remain private.
FAKE_PLAN_JSON="${RECOVERY_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_rejected 'official IAM linter reported a finding' env FAKE_GCLOUD_RESULT=finding-secret bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
assert_rejected 'official IAM linter reported a finding' env FAKE_GCLOUD_RESULT=malformed-secret bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
assert_rejected 'official IAM linter could not evaluate a condition' env FAKE_GCLOUD_RESULT=failure bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
assert_rejected 'Google Cloud CLI 576.0.0 is required' env FAKE_GCLOUD_VERSION=575.0.0 bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
assert_rejected 'pinned Google Cloud CLI version could not be verified' env FAKE_GCLOUD_VERSION_RESULT=failure bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
assert_rejected 'pinned Google Cloud CLI version could not be verified' env FAKE_GCLOUD_VERSION_RESULT=malformed-secret bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
FAKE_PLAN_JSON="${PRIVATE_SENTINEL}"; export FAKE_PLAN_JSON
assert_rejected 'saved plan could not be inspected safely' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
FAKE_PLAN_JSON="${RECOVERY_PLAN_JSON}"; export FAKE_PLAN_JSON
assert_rejected 'saved plan could not be inspected safely' env FAKE_TOFU_RESULT=failure FAKE_TOFU_STDERR="${PRIVATE_SENTINEL}" bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" recovery
export FAKE_TOFU_STDERR="${PRIVATE_SENTINEL}"
assert_lint_success 'Official IAM lint accepted 4 unique condition(s) across 4 validated recovery-mode binding(s).' 4 recovery
export FAKE_TOFU_STDERR=

ln -s "${FAKE_PLAN_PATH}" "${TEST_ROOT}/linked.tfplan"
assert_rejected 'saved plan must be a real file' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${TEST_ROOT}/linked.tfplan" recovery
ln -s "${FAKE_ROOT}" "${TEST_ROOT}/linked-root"
assert_rejected 'OpenTofu root must be a real directory' bash "${LINT_SCRIPT}" "${TEST_ROOT}/linked-root" "${FAKE_PLAN_PATH}" recovery
assert_rejected 'saved-plan path must be absolute' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" saved.tfplan recovery
assert_rejected 'MODE must be recovery, complete, or steady' bash "${LINT_SCRIPT}" "${FAKE_ROOT}" "${FAKE_PLAN_PATH}" unsupported

printf 'Seen registry IAM-condition lint tests passed.\n'
