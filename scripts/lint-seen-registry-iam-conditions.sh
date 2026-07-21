#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'Seen registry IAM-condition lint failed: %s\n' "$1" >&2
  exit 1
}

if (( $# != 3 )); then
  fail "usage: $0 ROOT SAVED_PLAN MODE"
fi

readonly ROOT="$1"
readonly SAVED_PLAN="$2"
readonly MODE="$3"
readonly EXPECTED_GCLOUD_VERSION="576.0.0"
readonly PROJECT_ID="seen-registry-prod-476219"
readonly APPLY_MEMBER="serviceAccount:seen-registry-prod-iac@${PROJECT_ID}.iam.gserviceaccount.com"
readonly PLAN_MEMBER="serviceAccount:seen-registry-prod-plan@${PROJECT_ID}.iam.gserviceaccount.com"
readonly HUMAN_MEMBER="user:yousef@felidai.com"
readonly RECOVERY_EXPIRY_SUFFIX="')"
readonly SETTER_ROLE_PATTERN='^projects/[^/]+/roles/seenRegistry(ProjectIamApply|KmsIamApply|RunJobIamApply|RunServiceIamApply|SecretIamApply|StorageIamApply)$'

[[ -d "${ROOT}" && ! -L "${ROOT}" ]] || fail "the OpenTofu root must be a real directory"
[[ "${SAVED_PLAN}" == /* ]] || fail "the saved-plan path must be absolute"
[[ -f "${SAVED_PLAN}" && ! -L "${SAVED_PLAN}" ]] || fail "the saved plan must be a real file"

case "${MODE}" in
  recovery | complete | steady) ;;
  *) fail "MODE must be recovery, complete, or steady" ;;
esac

for required_command in gcloud jq mktemp tofu; do
  command -v "${required_command}" >/dev/null || fail "${required_command} is required"
done

gcloud_version_json=
if ! gcloud_version_json="$(gcloud version --format=json 2>/dev/null)"; then
  fail "the pinned Google Cloud CLI version could not be verified"
fi
gcloud_version=
if ! gcloud_version="$({
  jq -er '
    if type == "object" and (."Google Cloud SDK" | type) == "string" then
      ."Google Cloud SDK"
    else
      error("invalid Google Cloud CLI version response")
    end
  ' <<< "${gcloud_version_json}"
} 2>/dev/null)"; then
  fail "the pinned Google Cloud CLI version could not be verified"
fi
[[ "${gcloud_version}" == "${EXPECTED_GCLOUD_VERSION}" ]] || fail "Google Cloud CLI ${EXPECTED_GCLOUD_VERSION} is required"

declare -ar steady_addresses=(
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
declare -ar recovery_read_addresses=(
  'google_project_iam_member.temporary_human_state_bucket_policy_read_access["bootstrap"]'
  'google_project_iam_member.temporary_human_state_bucket_policy_read_access["production"]'
)
declare -ar recovery_setter_addresses=(
  'google_project_iam_member.temporary_human_state_bucket_policy_access["bootstrap"]'
  'google_project_iam_member.temporary_human_state_bucket_policy_access["production"]'
)
declare -ar recovery_addresses=("${recovery_read_addresses[@]}" "${recovery_setter_addresses[@]}")
declare -ar setter_binding_addresses=("${steady_addresses[@]}" "${recovery_setter_addresses[@]}")
declare -ar known_binding_addresses=("${steady_addresses[@]}" "${recovery_addresses[@]}")
declare -ar recovery_change_addresses=(
  'google_project_iam_custom_role.state_bucket_policy_reader[0]'
  "${recovery_addresses[@]}"
)
declare -ar setter_custom_role_addresses=(
  'google_project_iam_custom_role.project_iam_apply[0]'
  'google_project_iam_custom_role.resource_iam_setter["kms"]'
  'google_project_iam_custom_role.resource_iam_setter["run_job"]'
  'google_project_iam_custom_role.resource_iam_setter["run_service"]'
  'google_project_iam_custom_role.resource_iam_setter["secret"]'
  'google_project_iam_custom_role.resource_iam_setter["storage"]'
)
declare -ar known_custom_role_addresses=(
  "${setter_custom_role_addresses[@]}"
  'google_project_iam_custom_role.state_reader[0]'
  'google_project_iam_custom_role.state_bucket_policy_reader[0]'
)
declare -ar setter_roles=(
  "projects/${PROJECT_ID}/roles/seenRegistryProjectIamApply"
  "projects/${PROJECT_ID}/roles/seenRegistryKmsIamApply"
  "projects/${PROJECT_ID}/roles/seenRegistryRunJobIamApply"
  "projects/${PROJECT_ID}/roles/seenRegistryRunServiceIamApply"
  "projects/${PROJECT_ID}/roles/seenRegistrySecretIamApply"
  "projects/${PROJECT_ID}/roles/seenRegistryStorageIamApply"
)
declare -ar steady_evidence_addresses=(
  'terraform_data.recovery_reconciliation_record["user:yousef@felidai.com"]'
  'terraform_data.project_creator_owner_adoption_record["user:yousef@felidai.com"]'
  'terraform_data.recovery_cleanup_record["user:yousef@felidai.com"]'
  'terraform_data.project_creator_owner_removal_record["user:yousef@felidai.com"]'
)
declare -ar phase_contract_addresses=(
  'terraform_data.phase_evidence_contract'
  'terraform_data.bootstrap_phase_contract'
)

json_array() {
  printf '%s\n' "$@" | jq -R . | jq -cs 'sort'
}

phase_contract_sentinel_for_address() {
  case "$1" in
    terraform_data.phase_evidence_contract)
      printf '%s\n' 'seen-registry-production-bootstrap-phase-evidence-contract'
      ;;
    terraform_data.bootstrap_phase_contract)
      printf '%s\n' 'seen-registry-production-bootstrap-phase-contract'
      ;;
    *) return 1 ;;
  esac
}

known_bindings_json="$(json_array "${known_binding_addresses[@]}")" || fail "the IAM binding contract could not be constructed"
setter_bindings_json="$(json_array "${setter_binding_addresses[@]}")" || fail "the setter binding contract could not be constructed"
setter_roles_json="$(json_array "${setter_roles[@]}")" || fail "the setter-role contract could not be constructed"
steady_evidence_json="$(json_array "${steady_evidence_addresses[@]}")" || fail "the steady-state evidence contract could not be constructed"

plan_json=
if ! plan_json="$({
  tofu -chdir="${ROOT}" show -json "${SAVED_PLAN}" |
    jq -ce '
      if type == "object" and (.planned_values.root_module | type) == "object" and (.resource_changes | type) == "array" then
        .
      else
        error("invalid saved plan shape")
      end
    '
} 2>/dev/null)"; then
  fail "the saved plan could not be inspected safely"
fi

all_resources=
if ! all_resources="$(
  jq -ce '[
    .planned_values.root_module
    | recurse(.child_modules[]?)
    | .resources[]?
    | select((.address | type) == "string" and (.type | type) == "string" and (.values | type) == "object")
    | {address, mode: (.mode // "managed"), type, values}
  ]' 2>/dev/null <<< "${plan_json}"
)"; then
  fail "the saved-plan resource contract could not be evaluated"
fi

managed_resources=
if ! managed_resources="$(jq -ce '[.[] | select(.mode == "managed")]' 2>/dev/null <<< "${all_resources}")"; then
  fail "the saved-plan resource contract could not be evaluated"
fi

managed_non_noop_changes=
if ! managed_non_noop_changes="$(
  jq -ce '[
    .resource_changes[]
    | select(.mode == "managed")
    | select((.address | type) == "string")
    | select((.change.actions | type) == "array" and (.change.actions | length) > 0)
    | select(any(.change.actions[]; . != "no-op"))
    | .address
  ] | sort' 2>/dev/null <<< "${plan_json}"
)"; then
  fail "the saved-plan change contract could not be evaluated"
fi

if [[ "${MODE}" != "recovery" ]]; then
  if ! jq -e '(.prior_state.values.root_module | type) == "object"' >/dev/null 2>&1 <<< "${plan_json}"; then
    fail "complete and steady modes require concrete prior state for full-plan coverage"
  fi
  expected_managed_coverage=
  if ! expected_managed_coverage="$(
    jq -ce '(
      [
        .planned_values.root_module
        | recurse(.child_modules[]?)
        | .resources[]?
        | select((.mode // "managed") == "managed")
        | .address
        | select(type == "string")
      ] +
      [
        .prior_state.values.root_module
        | recurse(.child_modules[]?)
        | .resources[]?
        | select((.mode // "managed") == "managed")
        | .address
        | select(type == "string")
      ]
    ) | sort | unique' 2>/dev/null <<< "${plan_json}"
  )"; then
    fail "the full-plan managed resource coverage contract could not be evaluated"
  fi
  actual_managed_coverage=
  if ! actual_managed_coverage="$(
    jq -ce '[
      .resource_changes[]
      | select(.mode == "managed")
      | select((.address | type) == "string")
      | select((.change.actions | type) == "array" and (.change.actions | length) > 0)
      | .address
    ] | sort' 2>/dev/null <<< "${plan_json}"
  )"; then
    fail "the full-plan managed resource coverage contract could not be evaluated"
  fi
  unique_managed_coverage="$(jq -ce 'unique' 2>/dev/null <<< "${actual_managed_coverage}")" || fail "the full-plan managed resource coverage contract could not be evaluated"
  [[ "${actual_managed_coverage}" == "${unique_managed_coverage}" ]] || fail "complete and steady modes forbid duplicate managed resource-change entries"
  [[ "${actual_managed_coverage}" == "${expected_managed_coverage}" ]] || fail "complete and steady modes require full managed resource-change coverage"

  for address in "${phase_contract_addresses[@]}"; do
    contract_count="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)] | length' 2>/dev/null <<< "${managed_resources}")" || fail "the phase-contract planned-value coverage could not be evaluated"
    (( contract_count == 1 )) || fail "complete and steady modes require both phase-contract resources in planned values"
    contract_type="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].type' 2>/dev/null <<< "${managed_resources}")" || fail "a phase-contract resource is not concrete"
    contract_input="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].values.input | select(type == "string")' 2>/dev/null <<< "${managed_resources}")" || fail "a phase-contract resource is not concrete"
    contract_sentinel="$(phase_contract_sentinel_for_address "${address}")" || fail "a phase-contract resource has no sentinel contract"
    [[ "${contract_type}" == "terraform_data" && "${contract_input}" == "${contract_sentinel}" ]] || fail "a phase-contract resource does not match its exact planned-value contract"
  done
fi

if [[ "${MODE}" == "recovery" ]]; then
  recovery_changes_json="$(json_array "${recovery_change_addresses[@]}")" || fail "the recovery change contract could not be constructed"
  [[ "${managed_non_noop_changes}" == "${recovery_changes_json}" ]] || fail "recovery mode requires exactly the five reviewed managed changes"
  for address in "${recovery_change_addresses[@]}"; do
    recovery_change="$(jq -ce --arg address "${address}" '[.resource_changes[] | select(.mode == "managed" and .address == $address)] | if length == 1 then .[0] else error("invalid recovery change") end' 2>/dev/null <<< "${plan_json}")" || fail "a reviewed recovery change is missing or duplicated"
    recovery_actions="$(jq -ce '.change.actions' 2>/dev/null <<< "${recovery_change}")" || fail "a reviewed recovery change is not concrete"
    [[ "${recovery_actions}" == '["create"]' ]] || fail "recovery mode requires each reviewed resource to be an exact create"
  done
fi

if [[ "${MODE}" == "steady" ]]; then
  forbidden_steady_changes=
  if ! forbidden_steady_changes="$(
    jq -er '[
      .[]
      | select(
          startswith("google_project_iam_member.project_creator_owner") or
          startswith("google_project_iam_member.temporary_human_state_bucket_policy_read_access") or
          startswith("google_project_iam_member.temporary_human_state_bucket_policy_access")
        )
    ] | length' 2>/dev/null <<< "${managed_non_noop_changes}"
  )"; then
    fail "the steady-state change contract could not be evaluated"
  fi
  (( forbidden_steady_changes == 0 )) || fail "steady mode forbids Owner or temporary recovery binding changes"

  forbidden_evidence_changes=
  if ! forbidden_evidence_changes="$(
    jq -er '[
      .[]
      | select(
          startswith("terraform_data.recovery_reconciliation_record") or
          startswith("terraform_data.project_creator_owner_adoption_record") or
          startswith("terraform_data.recovery_cleanup_record") or
          startswith("terraform_data.project_creator_owner_removal_record")
        )
    ] | length' 2>/dev/null <<< "${managed_non_noop_changes}"
  )"; then
    fail "the steady-state evidence change contract could not be evaluated"
  fi
  (( forbidden_evidence_changes == 0 )) || fail "steady mode forbids immutable evidence record changes"
fi

resource_count() {
  jq -er --arg address "$1" '[.[] | select(.address == $address)] | length' 2>/dev/null <<< "${managed_resources}"
}

resource_value() {
  local address="$1"
  local field="$2"
  jq -er --arg address "${address}" --arg field "${field}" '
    [.[] | select(.address == $address)] as $matches
    | if ($matches | length) == 1 and ($matches[0].values[$field] | type) == "string" then
        $matches[0].values[$field]
      else
        error("missing or non-string resource field")
      end
  ' 2>/dev/null <<< "${managed_resources}"
}

condition_expression() {
  jq -er --arg address "$1" '
    [.[] | select(.address == $address)] as $matches
    | if
        ($matches | length) == 1 and
        ($matches[0].type == "google_project_iam_member") and
        ($matches[0].values.condition | type) == "array" and
        ($matches[0].values.condition | length) == 1 and
        ($matches[0].values.condition[0].expression | type) == "string"
      then
        $matches[0].values.condition[0].expression
      else
        error("invalid binding condition")
      end
  ' 2>/dev/null <<< "${managed_resources}"
}

is_known_address() {
  local address="$1"
  local candidate
  shift
  for candidate in "$@"; do
    [[ "${address}" == "${candidate}" ]] && return 0
  done
  return 1
}

evidence_sentinel_for_address() {
  case "$1" in
    'terraform_data.recovery_reconciliation_record["user:yousef@felidai.com"]')
      printf '%s\n' 'recovery-reconciliation-applied'
      ;;
    'terraform_data.project_creator_owner_adoption_record["user:yousef@felidai.com"]')
      printf '%s\n' 'project-creator-owner-adopted'
      ;;
    'terraform_data.recovery_cleanup_record["user:yousef@felidai.com"]')
      printf '%s\n' 'recovery-project-bindings-cleaned'
      ;;
    'terraform_data.project_creator_owner_removal_record["user:yousef@felidai.com"]')
      printf '%s\n' 'project-creator-owner-removed'
      ;;
    *) return 1 ;;
  esac
}

validate_steady_evidence_resources() {
  local resources="$1"
  local source_name="$2"
  local actual_addresses address count sentinel input output resource_type

  if ! actual_addresses="$(
    jq -ce '[
      .[]
      | select(.address | test("^terraform_data\\.(recovery_reconciliation_record|project_creator_owner_adoption_record|recovery_cleanup_record|project_creator_owner_removal_record)(\\[|$)"))
      | .address
    ] | sort' 2>/dev/null <<< "${resources}"
  )"; then
    fail "the steady-state ${source_name} evidence contract could not be evaluated"
  fi
  [[ "${actual_addresses}" == "${steady_evidence_json}" ]] || fail "steady mode requires the exact immutable evidence records in ${source_name}"

  for address in "${steady_evidence_addresses[@]}"; do
    count="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)] | length' 2>/dev/null <<< "${resources}")" || fail "the steady-state ${source_name} evidence contract could not be evaluated"
    (( count == 1 )) || fail "steady mode requires exactly one instance of every immutable evidence record in ${source_name}"
    resource_type="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].type' 2>/dev/null <<< "${resources}")" || fail "an immutable ${source_name} evidence record is not concrete"
    input="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].values.input | select(type == "string")' 2>/dev/null <<< "${resources}")" || fail "an immutable ${source_name} evidence record is not concrete"
    output="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].values.output | select(type == "string")' 2>/dev/null <<< "${resources}")" || fail "an immutable ${source_name} evidence record is not concrete"
    sentinel="$(evidence_sentinel_for_address "${address}")" || fail "an immutable evidence record has no sentinel contract"
    [[ "${resource_type}" == "terraform_data" && "${input}" == "${sentinel}" && "${output}" == "${sentinel}" ]] || fail "an immutable ${source_name} evidence record has an invalid sentinel"
  done
}

validate_planned_phase_evidence_inputs() {
  local evidence_resources unexpected_count duplicate_count address count input sentinel resource_mode resource_type

  if ! evidence_resources="$(
    jq -ce '[
      .[]
      | select(.address | test("^terraform_data\\.(recovery_reconciliation_record|project_creator_owner_adoption_record|recovery_cleanup_record|project_creator_owner_removal_record)(\\[|$)"))
    ]' 2>/dev/null <<< "${all_resources}"
  )"; then
    fail "the planned phase-evidence contract could not be evaluated"
  fi
  if ! unexpected_count="$(
    jq -er --argjson known "${steady_evidence_json}" '[
      .[]
      | select(.address as $address | ($known | index($address)) == null)
    ] | length' 2>/dev/null <<< "${evidence_resources}"
  )"; then
    fail "the planned phase-evidence contract could not be evaluated"
  fi
  (( unexpected_count == 0 )) || fail "the saved plan contains an unknown phase-evidence address"

  if ! duplicate_count="$(jq -er 'group_by(.address) | [.[] | select(length != 1)] | length' 2>/dev/null <<< "${evidence_resources}")"; then
    fail "the planned phase-evidence contract could not be evaluated"
  fi
  (( duplicate_count == 0 )) || fail "the saved plan contains an extra phase-evidence instance"

  for address in "${steady_evidence_addresses[@]}"; do
    count="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)] | length' 2>/dev/null <<< "${evidence_resources}")" || fail "the planned phase-evidence contract could not be evaluated"
    (( count == 0 )) && continue
    resource_mode="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].mode' 2>/dev/null <<< "${evidence_resources}")" || fail "a planned phase-evidence record is not concrete"
    resource_type="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].type' 2>/dev/null <<< "${evidence_resources}")" || fail "a planned phase-evidence record is not concrete"
    input="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].values.input | select(type == "string")' 2>/dev/null <<< "${evidence_resources}")" || fail "a planned phase-evidence input is not concrete"
    sentinel="$(evidence_sentinel_for_address "${address}")" || fail "a planned phase-evidence record has no sentinel contract"
    [[ "${resource_mode}" == "managed" && "${resource_type}" == "terraform_data" && "${input}" == "${sentinel}" ]] || fail "a planned phase-evidence record has an invalid input sentinel"
  done
}

validate_planned_phase_evidence_inputs

if [[ "${MODE}" == "steady" ]]; then
  nonconcrete_planned_iam_grant_count=
  if ! nonconcrete_planned_iam_grant_count="$(
    jq -er '
      [
        .[]
        | select(.type | test("^google_.*_iam_(member|binding)$"))
        | select(
            if (.type | endswith("_iam_member")) then
              (.values.role | type) != "string" or
              (.values.role | length) == 0 or
              (.values.member | type) != "string" or
              (.values.member | length) == 0
            else
              (.values.role | type) != "string" or
              (.values.role | length) == 0 or
              (.values.members | type) != "array" or
              (.values.members | length) == 0 or
              any(.values.members[]; type != "string" or length == 0)
            end
          )
      ] | length
    ' 2>/dev/null <<< "${managed_resources}"
  )"; then
    fail "the steady-state planned IAM principal contract could not be evaluated"
  fi
  (( nonconcrete_planned_iam_grant_count == 0 )) || fail "steady mode requires every planned IAM member or binding role and principal to be concrete"

  planned_direct_human_grant_count=
  if ! planned_direct_human_grant_count="$(
    jq -er '
      def is_human_principal:
        if type == "string" then
          startswith("user:") or startswith("group:")
        else
          false
        end;
      [
      .[]
      | select(.type | test("^google_.*_iam_(member|binding)$"))
      | select(
          (.values.member? | is_human_principal) or
          any(.values.members[]?; is_human_principal)
        )
      ] | length
    ' 2>/dev/null <<< "${managed_resources}"
  )"; then
    fail "the steady-state planned IAM principal contract could not be evaluated"
  fi
  (( planned_direct_human_grant_count == 0 )) || fail "steady mode forbids human principals in planned IAM grants"

  planned_managed_iam_policies=
  if ! planned_managed_iam_policies="$(
    jq -ce '[.[] | select(.type | test("^google_.*_iam_policy$"))]' 2>/dev/null <<< "${managed_resources}"
  )"; then
    fail "the steady-state planned authoritative IAM policy contract could not be evaluated"
  fi
  nonconcrete_policy_count=
  if ! nonconcrete_policy_count="$(
    jq -er '[
      .[]
      | (try (.values.policy_data | fromjson) catch null) as $policy
      | select(
          (.values.policy_data | type) != "string" or
          ($policy | type) != "object" or
          (($policy.bindings // []) | type) != "array" or
          any(
            ($policy.bindings // [])[];
            (.role | type) != "string" or
            (.members | type) != "array" or
            any(.members[]; type != "string")
          )
        )
    ] | length' 2>/dev/null <<< "${planned_managed_iam_policies}"
  )"; then
    fail "the steady-state planned authoritative IAM policy contract could not be evaluated"
  fi
  (( nonconcrete_policy_count == 0 )) || fail "steady mode requires concrete parseable planned authoritative IAM policy data"

  policy_human_grant_count=
  if ! policy_human_grant_count="$(
    jq -er '[
      .[]
      | (.values.policy_data | fromjson)
      | (.bindings // [])[]
      | .members[]
      | select(startswith("user:") or startswith("group:"))
    ] | length' 2>/dev/null <<< "${planned_managed_iam_policies}"
  )"; then
    fail "the steady-state planned authoritative IAM policy contract could not be evaluated"
  fi
  (( policy_human_grant_count == 0 )) || fail "steady mode forbids human principals in planned authoritative IAM policies"

  if ! jq -e '(.prior_state.values.root_module | type) == "object"' >/dev/null 2>&1 <<< "${plan_json}"; then
    fail "steady mode requires concrete prior-state evidence"
  fi
  prior_resources=
  if ! prior_resources="$(
    jq -ce '[
      .prior_state.values.root_module
      | recurse(.child_modules[]?)
      | .resources[]?
      | select((.address | type) == "string" and (.type | type) == "string" and (.values | type) == "object")
      | {address, mode: (.mode // "managed"), type, values}
      | select(.mode == "managed")
    ]' 2>/dev/null <<< "${plan_json}"
  )"; then
    fail "the steady-state prior-state contract could not be evaluated"
  fi

  prior_human_iam_count=
  if ! prior_human_iam_count="$(
    jq -er '[
      .[]
      | select(
          .address | test("^google_project_iam_member\\.(project_creator_owner|temporary_human_state_bucket_policy_read_access|temporary_human_state_bucket_policy_access)(\\[|$)")
        )
    ] | length' 2>/dev/null <<< "${prior_resources}"
  )"; then
    fail "the steady-state prior IAM contract could not be evaluated"
  fi
  (( prior_human_iam_count == 0 )) || fail "steady mode requires Owner and project-level recovery IAM resources absent from prior state"

  validate_steady_evidence_resources "${prior_resources}" "prior state"
  validate_steady_evidence_resources "${managed_resources}" "planned values"
fi

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

state_prefix_for_key() {
  case "$1" in
    bootstrap) printf '%s\n' 'seen-registry/bootstrap/prod' ;;
    production) printf '%s\n' 'seen-registry/prod' ;;
    *) return 1 ;;
  esac
}

validate_exact_state_policies() {
  local contract_mode="$1"
  local key address bucket prefix policy_count policy_type policy_bucket policy_data policy_json binding_count
  local reader_bindings locker_bindings writer_bindings human_members human_reader_bindings human_writer_bindings
  local locker_title locker_expression writer_title writer_expression state_resource lock_resource expected_writer
  local human_reader_title human_reader_expression human_writer_title human_writer_expression
  local reader_prefix="request.time < timestamp('" expiry_suffix="')" candidate_expiry
  local shared_migration_expiry='' complete_binding_count=''
  local bootstrap_policy_count production_policy_count prior_or_changed_policy_count

  case "${contract_mode}" in
    complete | owner | steady) ;;
    *) fail "the authoritative state-policy validation mode is invalid" ;;
  esac

  bootstrap_policy_count="$(resource_count 'google_storage_bucket_iam_policy.state["bootstrap"]')" || fail "the authoritative state-policy contract could not be evaluated"
  production_policy_count="$(resource_count 'google_storage_bucket_iam_policy.state["production"]')" || fail "the authoritative state-policy contract could not be evaluated"
  if [[ "${contract_mode}" == "complete" && ${bootstrap_policy_count} -eq 0 && ${production_policy_count} -eq 0 ]]; then
    prior_or_changed_policy_count="$(
      jq -er '
        (
          [
            .prior_state.values.root_module
            | recurse(.child_modules[]?)
            | .resources[]?
            | select(
                (.mode // "managed") == "managed" and
                (.address == "google_storage_bucket_iam_policy.state[\"bootstrap\"]" or
                 .address == "google_storage_bucket_iam_policy.state[\"production\"]")
              )
          ] +
          [
            .resource_changes[]
            | select(
                .mode == "managed" and
                (.address == "google_storage_bucket_iam_policy.state[\"bootstrap\"]" or
                 .address == "google_storage_bucket_iam_policy.state[\"production\"]")
              )
          ]
        ) | length
      ' 2>/dev/null <<< "${plan_json}"
    )" || fail "the complete authoritative state-policy absence contract could not be evaluated"
    (( prior_or_changed_policy_count == 0 )) || fail "complete mode forbids removing or omitting previously managed authoritative state policies"
    return 0
  fi
  (( bootstrap_policy_count == 1 && production_policy_count == 1 )) || fail "${contract_mode} mode requires either both authoritative state policies or neither policy before reconciliation"

  for key in bootstrap production; do
    address="google_storage_bucket_iam_policy.state[\"${key}\"]"
    bucket="$(state_bucket_for_key "${key}")" || fail "a steady state policy has no bucket contract"
    prefix="$(state_prefix_for_key "${key}")" || fail "a steady state policy has no object-prefix contract"
    policy_count="$(resource_count "${address}")" || fail "the steady authoritative state-policy contract could not be evaluated"
    (( policy_count == 1 )) || fail "${contract_mode} mode requires both authoritative state policies"
    policy_type="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].type' 2>/dev/null <<< "${managed_resources}")" || fail "an authoritative state policy is not concrete"
    policy_bucket="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].values.bucket | select(type == "string")' 2>/dev/null <<< "${managed_resources}")" || fail "an authoritative state policy is not concrete"
    [[ "${policy_type}" == "google_storage_bucket_iam_policy" ]] || fail "an authoritative state policy targets the wrong bucket"
    [[ "${policy_bucket}" == "${bucket}" || "${policy_bucket}" == "b/${bucket}" ]] || fail "an authoritative state policy targets the wrong bucket"
    policy_data="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].values.policy_data | select(type == "string")' 2>/dev/null <<< "${managed_resources}")" || fail "an authoritative state policy is not concrete"
    policy_json="$(
      jq -ce '
        select(
          type == "object" and
          (.bindings | type) == "array" and
          all(
            .bindings[];
            (.role | type) == "string" and
            (.members | type) == "array" and
            all(.members[]; type == "string")
          )
        )
      ' 2>/dev/null <<< "${policy_data}"
    )" || fail "an authoritative state policy is not concrete"
    binding_count="$(jq -er '.bindings | length' 2>/dev/null <<< "${policy_json}")" || fail "the steady authoritative state-policy bindings could not be evaluated"
    case "${contract_mode}" in
      steady)
        (( binding_count == 3 )) || fail "a steady authoritative state policy contains an extra or missing binding"
        ;;
      owner)
        (( binding_count == 5 )) || fail "Owner removal requires the exact five reviewed state-policy bindings on both buckets"
        ;;
      complete)
        (( binding_count == 3 || binding_count == 5 )) || fail "a complete authoritative state policy contains an extra or missing binding"
        if [[ -z "${complete_binding_count}" ]]; then
          complete_binding_count="${binding_count}"
        else
          [[ "${binding_count}" == "${complete_binding_count}" ]] || fail "complete mode requires both authoritative state policies to use one exact migration-access shape"
        fi
        ;;
    esac

    reader_bindings="$(jq -ce \
      --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateReader" \
      --arg apply "${APPLY_MEMBER}" \
      --arg plan "${PLAN_MEMBER}" \
      '[.bindings[] | select(.role == $role and ((.members | sort) == ([$apply, $plan] | sort)))]' \
      2>/dev/null <<< "${policy_json}")" || fail "the state-reader binding could not be evaluated"
    locker_bindings="$(jq -ce \
      --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateLocker" \
      --arg plan "${PLAN_MEMBER}" \
      '[.bindings[] | select(.role == $role and .members == [$plan])]' \
      2>/dev/null <<< "${policy_json}")" || fail "the state-locker binding could not be evaluated"
    writer_bindings="$(jq -ce \
      --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateWriter" \
      --arg apply "${APPLY_MEMBER}" \
      '[.bindings[] | select(.role == $role and .members == [$apply])]' \
      2>/dev/null <<< "${policy_json}")" || fail "the state-writer binding could not be evaluated"
    [[ "$(jq -er 'length' <<< "${reader_bindings}")" == 1 && "$(jq -er 'length' <<< "${locker_bindings}")" == 1 && "$(jq -er 'length' <<< "${writer_bindings}")" == 1 ]] || fail "an authoritative state policy contains an unreviewed principal or role"
    [[ "$(jq -ce '.[0].condition? // null' <<< "${reader_bindings}")" == "null" ]] || fail "the steady state-reader binding must be unconditional on its dedicated bucket"

    locker_title="$(jq -er '.[0].condition.title | select(type == "string")' 2>/dev/null <<< "${locker_bindings}")" || fail "the steady state-locker condition is not concrete"
    locker_expression="$(jq -er '.[0].condition.expression | select(type == "string")' 2>/dev/null <<< "${locker_bindings}")" || fail "the steady state-locker condition is not concrete"
    writer_title="$(jq -er '.[0].condition.title | select(type == "string")' 2>/dev/null <<< "${writer_bindings}")" || fail "the steady state-writer condition is not concrete"
    writer_expression="$(jq -er '.[0].condition.expression | select(type == "string")' 2>/dev/null <<< "${writer_bindings}")" || fail "the steady state-writer condition is not concrete"
    state_resource="projects/_/buckets/${bucket}/objects/${prefix}/default.tfstate"
    lock_resource="projects/_/buckets/${bucket}/objects/${prefix}/default.tflock"
    expected_writer="resource.name == '${state_resource}' || resource.name == '${lock_resource}'"
    [[ "${locker_title}" == "exact_${key}_plan_lock" && "${locker_expression}" == "resource.name == '${lock_resource}'" ]] || fail "the steady state-locker condition does not match the exact lock object"
    [[ "${writer_title}" == "exact_${key}_state_mutations" && "${writer_expression}" == "${expected_writer}" ]] || fail "the steady state-writer condition does not match the exact state objects"

    if (( binding_count == 3 )); then
      continue
    fi

    human_members="$(jq -ce '[.bindings[].members[] | select(startswith("user:") or startswith("group:"))] | sort' 2>/dev/null <<< "${policy_json}")" || fail "the human state-policy bindings could not be evaluated"
    [[ "${human_members}" == "[\"${HUMAN_MEMBER}\",\"${HUMAN_MEMBER}\"]" ]] || fail "a migration-enabled state policy requires only the reviewed human reader and writer bindings"
    human_reader_bindings="$(jq -ce \
      --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateReader" \
      --arg human "${HUMAN_MEMBER}" \
      '[.bindings[] | select(.role == $role and .members == [$human])]' \
      2>/dev/null <<< "${policy_json}")" || fail "the human state-reader binding could not be evaluated"
    human_writer_bindings="$(jq -ce \
      --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateWriter" \
      --arg human "${HUMAN_MEMBER}" \
      '[.bindings[] | select(.role == $role and .members == [$human])]' \
      2>/dev/null <<< "${policy_json}")" || fail "the human state-writer binding could not be evaluated"
    [[ "$(jq -er 'length' <<< "${human_reader_bindings}")" == 1 && "$(jq -er 'length' <<< "${human_writer_bindings}")" == 1 ]] || fail "a migration-enabled state policy requires exact human reader and writer bindings"

    human_reader_title="$(jq -er '.[0].condition.title | select(type == "string")' 2>/dev/null <<< "${human_reader_bindings}")" || fail "the human state-reader condition is not concrete"
    human_reader_expression="$(jq -er '.[0].condition.expression | select(type == "string")' 2>/dev/null <<< "${human_reader_bindings}")" || fail "the human state-reader condition is not concrete"
    human_writer_title="$(jq -er '.[0].condition.title | select(type == "string")' 2>/dev/null <<< "${human_writer_bindings}")" || fail "the human state-writer condition is not concrete"
    human_writer_expression="$(jq -er '.[0].condition.expression | select(type == "string")' 2>/dev/null <<< "${human_writer_bindings}")" || fail "the human state-writer condition is not concrete"
    [[ "${human_reader_title}" == "temporary_${key}_human_state_read" && "${human_writer_title}" == "temporary_${key}_human_state_mutations" ]] || fail "the human state-policy conditions use the wrong titles"
    [[ "${human_reader_expression}" == "${reader_prefix}"*"${expiry_suffix}" ]] || fail "the human state-reader condition is not exactly time bounded"
    candidate_expiry="${human_reader_expression#"${reader_prefix}"}"
    candidate_expiry="${candidate_expiry%"${expiry_suffix}"}"
    if ! jq -en --arg expiry "${candidate_expiry}" '$expiry | fromdateiso8601 | todateiso8601 == $expiry' >/dev/null 2>&1; then
      fail "the human state-policy expiry is not a canonical RFC3339 timestamp"
    fi
    if [[ -z "${shared_migration_expiry}" ]]; then
      shared_migration_expiry="${candidate_expiry}"
    else
      [[ "${candidate_expiry}" == "${shared_migration_expiry}" ]] || fail "the human state-policy bindings do not share one expiry"
    fi
    expected_writer="(resource.name == '${state_resource}' || resource.name == '${lock_resource}') && request.time < timestamp('${shared_migration_expiry}')"
    [[ "${human_writer_expression}" == "${expected_writer}" ]] || fail "the human state-writer condition does not match the exact state objects"
  done
}

if [[ "${MODE}" == "steady" ]]; then
  validate_exact_state_policies steady
elif [[ "${MODE}" == "complete" ]]; then
  validate_exact_state_policies complete
fi

validate_owner_removal_plan() {
  local owner_address='google_project_iam_member.project_creator_owner["user:yousef@felidai.com"]'
  local removal_record_address='terraform_data.project_creator_owner_removal_record["user:yousef@felidai.com"]'
  local owner_family_count removal_record_change_count owner_change owner_actions before_project before_role before_member after_value
  local expected_non_noops record_actions address key bucket prefix policy_count policy_type policy_bucket policy_actions
  local policy_data policy_json human_members reader_bindings writer_bindings reader_expression writer_expression
  local reader_title writer_title expiry='' candidate_expiry reader_prefix suffix state_resource lock_resource expected_writer

  owner_family_count="$(
    jq -er '[
      .resource_changes[]
      | select(.mode == "managed")
      | select(.address | startswith("google_project_iam_member.project_creator_owner"))
      | select(any(.change.actions[]?; . != "no-op"))
    ] | length' 2>/dev/null <<< "${plan_json}"
  )" || fail "the Owner-removal change contract could not be evaluated"
  removal_record_change_count="$(
    jq -er --arg address "${removal_record_address}" '[
      .resource_changes[]
      | select(.mode == "managed" and .address == $address)
      | select(any(.change.actions[]?; . != "no-op"))
    ] | length' 2>/dev/null <<< "${plan_json}"
  )" || fail "the Owner-removal record change contract could not be evaluated"
  (( owner_family_count == 0 && removal_record_change_count == 0 )) && return 0
  (( owner_family_count == 1 && removal_record_change_count == 1 )) || fail "complete mode requires the exact Owner-removal change pair"

  owner_change="$(jq -ce --arg address "${owner_address}" '[.resource_changes[] | select(.mode == "managed" and .address == $address)] | if length == 1 then .[0] else error("invalid Owner change") end' 2>/dev/null <<< "${plan_json}")" || fail "complete mode allows only the exact reviewed Owner removal"
  owner_actions="$(jq -ce '.change.actions' 2>/dev/null <<< "${owner_change}")" || fail "the Owner-removal change is not concrete"
  [[ "${owner_actions}" == '["delete"]' ]] || fail "the reviewed Owner change must be an exact delete"
  before_project="$(jq -er '.change.before.project | select(type == "string")' 2>/dev/null <<< "${owner_change}")" || fail "the Owner-removal prior grant is not concrete"
  before_role="$(jq -er '.change.before.role | select(type == "string")' 2>/dev/null <<< "${owner_change}")" || fail "the Owner-removal prior grant is not concrete"
  before_member="$(jq -er '.change.before.member | select(type == "string")' 2>/dev/null <<< "${owner_change}")" || fail "the Owner-removal prior grant is not concrete"
  after_value="$(jq -c '.change.after' 2>/dev/null <<< "${owner_change}")" || fail "the Owner-removal final grant is not concrete"
  [[ "${before_project}" == "${PROJECT_ID}" && "${before_role}" == "roles/owner" && "${before_member}" == "${HUMAN_MEMBER}" && "${after_value}" == "null" ]] || fail "the Owner-removal prior grant does not match the reviewed Owner"

  expected_non_noops="$(json_array "${owner_address}" "${removal_record_address}")" || fail "the Owner-removal isolation contract could not be constructed"
  [[ "${managed_non_noop_changes}" == "${expected_non_noops}" ]] || fail "the Owner-removal plan must isolate the Owner delete and removal-record create"
  record_actions="$(jq -ce --arg address "${removal_record_address}" '[.resource_changes[] | select(.mode == "managed" and .address == $address)] | if length == 1 then .[0].change.actions else error("invalid record change") end' 2>/dev/null <<< "${plan_json}")" || fail "the Owner-removal record change is not concrete"
  [[ "${record_actions}" == '["create"]' ]] || fail "the Owner-removal plan must create the immutable removal record"

  validate_exact_state_policies owner

  suffix="')"
  for key in bootstrap production; do
    address="google_storage_bucket_iam_policy.state[\"${key}\"]"
    bucket="$(state_bucket_for_key "${key}")" || fail "an Owner-removal state policy has no bucket contract"
    prefix="$(state_prefix_for_key "${key}")" || fail "an Owner-removal state policy has no object-prefix contract"
    policy_count="$(resource_count "${address}")" || fail "the Owner-removal state-policy contract could not be evaluated"
    (( policy_count == 1 )) || fail "Owner removal requires both authoritative state policies in planned values"
    policy_type="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].type' 2>/dev/null <<< "${managed_resources}")" || fail "an Owner-removal state policy is not concrete"
    policy_bucket="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].values.bucket | select(type == "string")' 2>/dev/null <<< "${managed_resources}")" || fail "an Owner-removal state policy is not concrete"
    [[ "${policy_type}" == "google_storage_bucket_iam_policy" && "${policy_bucket}" == "${bucket}" ]] || fail "an Owner-removal state policy targets the wrong bucket"
    policy_actions="$(jq -ce --arg address "${address}" '[.resource_changes[] | select(.mode == "managed" and .address == $address)] | if length == 1 then .[0].change.actions else error("invalid policy change") end' 2>/dev/null <<< "${plan_json}")" || fail "an Owner-removal state-policy change is not concrete"
    [[ "${policy_actions}" == '["no-op"]' ]] || fail "Owner removal requires both authoritative state policies unchanged"
    policy_data="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].values.policy_data | select(type == "string")' 2>/dev/null <<< "${managed_resources}")" || fail "an Owner-removal state policy is not concrete"
    policy_json="$(jq -ce 'select(type == "object" and (.bindings | type) == "array")' 2>/dev/null <<< "${policy_data}")" || fail "an Owner-removal state policy is not concrete"

    human_members="$(jq -ce '[.bindings[].members[]? | select(type == "string" and (startswith("user:") or startswith("group:")))] | sort' 2>/dev/null <<< "${policy_json}")" || fail "the Owner-removal human state bindings could not be evaluated"
    [[ "${human_members}" == "[\"${HUMAN_MEMBER}\",\"${HUMAN_MEMBER}\"]" ]] || fail "Owner removal requires only the reviewed human reader and writer state bindings"
    reader_bindings="$(jq -ce --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateReader" --arg human "${HUMAN_MEMBER}" '[.bindings[] | select(.role == $role and .members == [$human])] ' 2>/dev/null <<< "${policy_json}")" || fail "the Owner-removal human reader binding could not be evaluated"
    writer_bindings="$(jq -ce --arg role "projects/${PROJECT_ID}/roles/seenRegistryStateWriter" --arg human "${HUMAN_MEMBER}" '[.bindings[] | select(.role == $role and .members == [$human])] ' 2>/dev/null <<< "${policy_json}")" || fail "the Owner-removal human writer binding could not be evaluated"
    [[ "$(jq -er 'length' <<< "${reader_bindings}")" == 1 && "$(jq -er 'length' <<< "${writer_bindings}")" == 1 ]] || fail "Owner removal requires exact human reader and writer bindings on both state policies"
    reader_expression="$(jq -er '.[0].condition.expression | select(type == "string")' 2>/dev/null <<< "${reader_bindings}")" || fail "the Owner-removal human reader condition is not concrete"
    writer_expression="$(jq -er '.[0].condition.expression | select(type == "string")' 2>/dev/null <<< "${writer_bindings}")" || fail "the Owner-removal human writer condition is not concrete"
    reader_title="$(jq -er '.[0].condition.title | select(type == "string")' 2>/dev/null <<< "${reader_bindings}")" || fail "the Owner-removal human reader condition is not concrete"
    writer_title="$(jq -er '.[0].condition.title | select(type == "string")' 2>/dev/null <<< "${writer_bindings}")" || fail "the Owner-removal human writer condition is not concrete"
    [[ "${reader_title}" == "temporary_${key}_human_state_read" && "${writer_title}" == "temporary_${key}_human_state_mutations" ]] || fail "the Owner-removal human state conditions use the wrong titles"

    reader_prefix="request.time < timestamp('"
    [[ "${reader_expression}" == "${reader_prefix}"*"${suffix}" ]] || fail "the Owner-removal human reader condition is not exactly time bounded"
    candidate_expiry="${reader_expression#"${reader_prefix}"}"
    candidate_expiry="${candidate_expiry%"${suffix}"}"
    if ! jq -en --arg expiry "${candidate_expiry}" '$expiry | fromdateiso8601 | todateiso8601 == $expiry' >/dev/null 2>&1; then
      fail "the Owner-removal human state expiry is not a canonical RFC3339 timestamp"
    fi
    if [[ -z "${expiry}" ]]; then
      expiry="${candidate_expiry}"
    else
      [[ "${candidate_expiry}" == "${expiry}" ]] || fail "the Owner-removal human state bindings do not share one expiry"
    fi
    state_resource="projects/_/buckets/${bucket}/objects/${prefix}/default.tfstate"
    lock_resource="projects/_/buckets/${bucket}/objects/${prefix}/default.tflock"
    expected_writer="(resource.name == '${state_resource}' || resource.name == '${lock_resource}') && request.time < timestamp('${expiry}')"
    [[ "${writer_expression}" == "${expected_writer}" ]] || fail "the Owner-removal human writer condition does not match the exact state objects"
  done
}

if [[ "${MODE}" == "complete" ]]; then
  validate_owner_removal_plan
fi

expected_role_for_address() {
  case "$1" in
    'google_project_iam_member.infrastructure_project_iam[0]')
      printf 'projects/%s/roles/seenRegistryProjectIamApply\n' "${PROJECT_ID}"
      ;;
    google_project_iam_member.infrastructure_kms_policy_setter*)
      printf 'projects/%s/roles/seenRegistryKmsIamApply\n' "${PROJECT_ID}"
      ;;
    'google_project_iam_member.infrastructure_run_policy_setter["job"]')
      printf 'projects/%s/roles/seenRegistryRunJobIamApply\n' "${PROJECT_ID}"
      ;;
    'google_project_iam_member.infrastructure_run_policy_setter["service"]')
      printf 'projects/%s/roles/seenRegistryRunServiceIamApply\n' "${PROJECT_ID}"
      ;;
    google_project_iam_member.infrastructure_secret_policy_setter*)
      printf 'projects/%s/roles/seenRegistrySecretIamApply\n' "${PROJECT_ID}"
      ;;
    google_project_iam_member.infrastructure_registry_storage_policy_setter* | \
      google_project_iam_member.infrastructure_state_storage_policy_setter* | \
      google_project_iam_member.temporary_human_state_bucket_policy_access*)
      printf 'projects/%s/roles/seenRegistryStorageIamApply\n' "${PROJECT_ID}"
      ;;
    google_project_iam_member.temporary_human_state_bucket_policy_read_access*)
      printf 'projects/%s/roles/seenRegistryStateBucketPolicyReader\n' "${PROJECT_ID}"
      ;;
    *) return 1 ;;
  esac
}

expected_member_for_address() {
  case "$1" in
    google_project_iam_member.temporary_human_state_bucket_policy_read_access* | \
      google_project_iam_member.temporary_human_state_bucket_policy_access*)
      printf '%s\n' "${HUMAN_MEMBER}"
      ;;
    *) printf '%s\n' "${APPLY_MEMBER}" ;;
  esac
}

readonly PROJECT_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/datastore.user', 'roles/datastore.viewer'])"
readonly KMS_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['projects/${PROJECT_ID}/roles/seen_registry_prod_kms_signer', 'roles/cloudkms.publicKeyViewer'])"
readonly RUN_JOB_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/run.invoker', 'projects/${PROJECT_ID}/roles/seenRegistryJobViewer'])"
readonly RUN_SERVICE_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/run.invoker'])"
readonly SECRET_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/secretmanager.secretAccessor'])"
readonly REGISTRY_STORAGE_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['projects/${PROJECT_ID}/roles/seen_registry_prod_blob_creator', 'projects/${PROJECT_ID}/roles/seen_registry_prod_metadata_creator', 'projects/${PROJECT_ID}/roles/seen_registry_prod_pointer_replacer', 'projects/${PROJECT_ID}/roles/seen_registry_prod_quarantine_promoter', 'projects/${PROJECT_ID}/roles/seen_registry_prod_quarantine_writer', 'roles/storage.objectViewer'])"
readonly STATE_STORAGE_MODIFIED_ROLES="api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['projects/${PROJECT_ID}/roles/seenRegistryStateLocker', 'projects/${PROJECT_ID}/roles/seenRegistryStateReader', 'projects/${PROJECT_ID}/roles/seenRegistryStateWriter'])"

recovery_present_count=0
for address in "${recovery_addresses[@]}"; do
  count="$(resource_count "${address}")" || fail "the recovery binding contract could not be evaluated"
  recovery_present_count=$((recovery_present_count + count))
done

if [[ "${MODE}" == "recovery" || ${recovery_present_count} -gt 0 ]]; then
  (( recovery_present_count == ${#recovery_addresses[@]} )) || fail "the saved plan does not contain the exact recovery binding set"
fi

recovery_expiry=
if (( recovery_present_count > 0 )); then
  recovery_probe_address='google_project_iam_member.temporary_human_state_bucket_policy_read_access["bootstrap"]'
  recovery_probe_expression="$(condition_expression "${recovery_probe_address}")" || fail "a recovery binding condition is not concrete"
  recovery_probe_bucket="$(state_bucket_for_key bootstrap)"
  recovery_prefix="resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${recovery_probe_bucket}' && request.time < timestamp('"
  [[ "${recovery_probe_expression}" == "${recovery_prefix}"*"${RECOVERY_EXPIRY_SUFFIX}" ]] || fail "a recovery binding condition does not match its exact authorization contract"
  recovery_expiry="${recovery_probe_expression#"${recovery_prefix}"}"
  recovery_expiry="${recovery_expiry%"${RECOVERY_EXPIRY_SUFFIX}"}"
  if ! jq -en --arg expiry "${recovery_expiry}" '$expiry | fromdateiso8601 | todateiso8601 == $expiry' >/dev/null 2>&1; then
    fail "the recovery binding expiry is not a canonical RFC3339 timestamp"
  fi
fi

secret_project_number=
if [[ "${MODE}" != "recovery" ]]; then
  for address in "${steady_addresses[@]}"; do
    [[ "${address}" == google_project_iam_member.infrastructure_secret_policy_setter* ]] || continue
    count="$(resource_count "${address}")" || fail "the secret setter contract could not be evaluated"
    (( count == 0 )) && continue
    (( count == 1 )) || fail "a reviewed IAM binding address is duplicated"
    secret_name="$(binding_key "${address}")"
    expression="$(condition_expression "${address}")" || fail "a steady setter condition is not concrete"
    secret_prefix="resource.type == 'secretmanager.googleapis.com/Secret' && resource.name == 'projects/"
    secret_suffix="/secrets/${secret_name}' && ${SECRET_MODIFIED_ROLES}"
    [[ "${expression}" == "${secret_prefix}"*"${secret_suffix}" ]] || fail "a steady setter condition does not match its exact authorization contract"
    candidate_number="${expression#"${secret_prefix}"}"
    candidate_number="${candidate_number%"${secret_suffix}"}"
    [[ "${candidate_number}" =~ ^[1-9][0-9]*$ ]] || fail "a secret setter does not use a concrete numeric production project number"
    if [[ -z "${secret_project_number}" ]]; then
      secret_project_number="${candidate_number}"
    else
      [[ "${candidate_number}" == "${secret_project_number}" ]] || fail "secret setters do not use one production project number"
    fi
  done
fi

expected_expression_for_address() {
  local address="$1"
  local key bucket
  case "${address}" in
    'google_project_iam_member.infrastructure_project_iam[0]')
      printf '%s\n' "${PROJECT_MODIFIED_ROLES}"
      ;;
    google_project_iam_member.infrastructure_kms_policy_setter*)
      key="$(binding_key "${address}")"
      printf "resource.type == 'cloudkms.googleapis.com/CryptoKey' && resource.name == 'projects/%s/locations/us-central1/keyRings/seen-registry-prod/cryptoKeys/%s' && %s\n" "${PROJECT_ID}" "${key}" "${KMS_MODIFIED_ROLES}"
      ;;
    'google_project_iam_member.infrastructure_run_policy_setter["job"]')
      printf '%s\n' "${RUN_JOB_MODIFIED_ROLES}"
      ;;
    'google_project_iam_member.infrastructure_run_policy_setter["service"]')
      printf '%s\n' "${RUN_SERVICE_MODIFIED_ROLES}"
      ;;
    google_project_iam_member.infrastructure_secret_policy_setter*)
      [[ -n "${secret_project_number}" ]] || return 1
      key="$(binding_key "${address}")"
      printf "resource.type == 'secretmanager.googleapis.com/Secret' && resource.name == 'projects/%s/secrets/%s' && %s\n" "${secret_project_number}" "${key}" "${SECRET_MODIFIED_ROLES}"
      ;;
    google_project_iam_member.infrastructure_registry_storage_policy_setter*)
      key="$(binding_key "${address}")"
      printf "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s\n" "${key}" "${REGISTRY_STORAGE_MODIFIED_ROLES}"
      ;;
    google_project_iam_member.infrastructure_state_storage_policy_setter*)
      key="$(binding_key "${address}")"
      bucket="$(state_bucket_for_key "${key}")" || return 1
      printf "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s\n" "${bucket}" "${STATE_STORAGE_MODIFIED_ROLES}"
      ;;
    google_project_iam_member.temporary_human_state_bucket_policy_read_access*)
      [[ -n "${recovery_expiry}" ]] || return 1
      key="$(binding_key "${address}")"
      bucket="$(state_bucket_for_key "${key}")" || return 1
      printf "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && request.time < timestamp('%s')\n" "${bucket}" "${recovery_expiry}"
      ;;
    google_project_iam_member.temporary_human_state_bucket_policy_access*)
      [[ -n "${recovery_expiry}" ]] || return 1
      key="$(binding_key "${address}")"
      bucket="$(state_bucket_for_key "${key}")" || return 1
      printf "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s && request.time < timestamp('%s')\n" "${bucket}" "${STATE_STORAGE_MODIFIED_ROLES}" "${recovery_expiry}"
      ;;
    *) return 1 ;;
  esac
}

required_addresses=()
case "${MODE}" in
  recovery) required_addresses=("${recovery_addresses[@]}") ;;
  complete) required_addresses=("${steady_addresses[@]}") ;;
  steady) required_addresses=("${steady_addresses[@]}") ;;
esac
for address in "${required_addresses[@]}"; do
  count="$(resource_count "${address}")" || fail "the required IAM binding contract could not be evaluated"
  (( count == 1 )) || fail "the saved plan does not contain the exact required IAM binding set for this mode"
done

unexpected_family_count=
if ! unexpected_family_count="$(
  jq -er --argjson known "${known_bindings_json}" '[
    .[]
    | select(.address | test("^google_project_iam_member\\.(infrastructure_(project_iam|kms_policy_setter|run_policy_setter|secret_policy_setter|registry_storage_policy_setter|state_storage_policy_setter)|temporary_human_state_bucket_policy_(read_access|access))"))
    | select(.address as $address | ($known | index($address)) == null)
  ] | length' 2>/dev/null <<< "${managed_resources}"
)"; then
  fail "the IAM binding family contract could not be evaluated"
fi
(( unexpected_family_count == 0 )) || fail "the saved plan contains an unreviewed IAM binding address"

grant_bypass_count=
if ! grant_bypass_count="$(
  jq -er --argjson setters "${setter_roles_json}" --argjson known "${setter_bindings_json}" --arg pattern "${SETTER_ROLE_PATTERN}" '[
    .[]
    | select(.type | test("^google_.*_iam_(member|binding)$"))
    | select(
        (.values.role | type) == "string" and
        (.values.role as $role | (($setters | index($role)) != null or ($role | test($pattern))))
      )
    | select(.address as $address | ($known | index($address)) == null)
  ] | length' 2>/dev/null <<< "${managed_resources}"
)"; then
  fail "the IAM grant contract could not be evaluated"
fi
(( grant_bypass_count == 0 )) || fail "the saved plan grants a setter role outside its reviewed binding address"

unknown_modified_grant_count=
if ! unknown_modified_grant_count="$(
  jq -er --argjson known "${setter_bindings_json}" '[
    .[]
    | select(.type | test("^google_.*_iam_(member|binding)$"))
    | select(any(.values.condition[]?.expression?; type == "string" and contains("iam.googleapis.com/modifiedGrantsByRole")))
    | select(.address as $address | ($known | index($address)) == null)
  ] | length' 2>/dev/null <<< "${managed_resources}"
)"; then
  fail "the modified-grants contract could not be evaluated"
fi
(( unknown_modified_grant_count == 0 )) || fail "the saved plan contains an unreviewed modified-grants condition"

policy_data_bypass_count=
if ! policy_data_bypass_count="$(
  jq -er --argjson setters "${setter_roles_json}" --arg pattern "${SETTER_ROLE_PATTERN}" '[
    .[]
    | select(.values.policy_data? | type == "string")
    | .values.policy_data as $policy
    | select(
        any($setters[]; . as $role | $policy | contains($role)) or
        ($policy | test("/roles/seenRegistry(ProjectIamApply|KmsIamApply|RunJobIamApply|RunServiceIamApply|SecretIamApply|StorageIamApply)")) or
        (
          try ($policy | fromjson) catch null
          | [.. | objects | .role? | select(type == "string" and test($pattern))]
          | length > 0
        )
      )
  ] | length' 2>/dev/null <<< "${all_resources}"
)"; then
  fail "the authoritative IAM policy contract could not be evaluated"
fi
(( policy_data_bypass_count == 0 )) || fail "an authoritative IAM policy contains a setter role"

custom_role_contract() {
  case "$1" in
    'google_project_iam_custom_role.project_iam_apply[0]')
      printf '%s\t%s\n' 'seenRegistryProjectIamApply' '["resourcemanager.projects.setIamPolicy"]'
      ;;
    'google_project_iam_custom_role.resource_iam_setter["kms"]')
      printf '%s\t%s\n' 'seenRegistryKmsIamApply' '["cloudkms.cryptoKeys.setIamPolicy"]'
      ;;
    'google_project_iam_custom_role.resource_iam_setter["run_job"]')
      printf '%s\t%s\n' 'seenRegistryRunJobIamApply' '["run.jobs.setIamPolicy"]'
      ;;
    'google_project_iam_custom_role.resource_iam_setter["run_service"]')
      printf '%s\t%s\n' 'seenRegistryRunServiceIamApply' '["run.services.setIamPolicy"]'
      ;;
    'google_project_iam_custom_role.resource_iam_setter["secret"]')
      printf '%s\t%s\n' 'seenRegistrySecretIamApply' '["secretmanager.secrets.setIamPolicy"]'
      ;;
    'google_project_iam_custom_role.resource_iam_setter["storage"]')
      printf '%s\t%s\n' 'seenRegistryStorageIamApply' '["storage.buckets.setIamPolicy"]'
      ;;
    'google_project_iam_custom_role.state_reader[0]')
      printf '%s\t%s\n' 'seenRegistryStateReader' '["storage.buckets.get","storage.objects.get","storage.objects.list"]'
      ;;
    'google_project_iam_custom_role.state_bucket_policy_reader[0]')
      printf '%s\t%s\n' 'seenRegistryStateBucketPolicyReader' '["storage.buckets.get","storage.buckets.getIamPolicy"]'
      ;;
    *) return 1 ;;
  esac
}

custom_role_count="$(jq -er '[.[] | select(.type | test("^google_.*_iam_custom_role$"))] | length' 2>/dev/null <<< "${managed_resources}")" || fail "the custom-role contract could not be evaluated"
for address in "${known_custom_role_addresses[@]}"; do
  count="$(resource_count "${address}")" || fail "the custom-role contract could not be evaluated"
  (( count <= 1 )) || fail "a reviewed custom IAM role address is duplicated"
  if (( count == 1 )); then
    custom_type="$(jq -er --arg address "${address}" '[.[] | select(.address == $address)][0].type' 2>/dev/null <<< "${managed_resources}")" || fail "the custom-role contract could not be evaluated"
    [[ "${custom_type}" == "google_project_iam_custom_role" ]] || fail "a reviewed custom IAM role address uses the wrong resource type"
  fi
done
for (( index = 0; index < custom_role_count; index++ )); do
  custom_resource="$(jq -ce --argjson index "${index}" '[.[] | select(.type | test("^google_.*_iam_custom_role$"))] | .[$index]' 2>/dev/null <<< "${managed_resources}")" || fail "the custom-role contract could not be evaluated"
  address="$(jq -er '.address' 2>/dev/null <<< "${custom_resource}")" || fail "the custom-role contract could not be evaluated"
  role_id="$(jq -er '.values.role_id // ""' 2>/dev/null <<< "${custom_resource}")" || fail "the custom-role contract could not be evaluated"
  setter_permission_count="$(jq -er '[.values.permissions[]? | select(type == "string" and endswith(".setIamPolicy"))] | length' 2>/dev/null <<< "${custom_resource}")" || fail "the custom-role contract could not be evaluated"
  if ! is_known_address "${address}" "${known_custom_role_addresses[@]}"; then
    if (( setter_permission_count > 0 )) || [[ "${role_id}" =~ ^seenRegistry(ProjectIamApply|KmsIamApply|RunJobIamApply|RunServiceIamApply|SecretIamApply|StorageIamApply|StateReader|StateBucketPolicyReader)$ ]]; then
      fail "the saved plan contains an unreviewed custom IAM authority role"
    fi
    continue
  fi
  contract="$(custom_role_contract "${address}")" || fail "a custom IAM role address has no permission contract"
  expected_role_id="${contract%%$'\t'*}"
  expected_permissions="${contract#*$'\t'}"
  project="$(jq -er '.values.project' 2>/dev/null <<< "${custom_resource}")" || fail "a reviewed custom IAM role is not concrete"
  permissions="$(jq -ce '.values.permissions | if type == "array" and all(.[]; type == "string") then sort else error("invalid permissions") end' 2>/dev/null <<< "${custom_resource}")" || fail "a reviewed custom IAM role is not concrete"
  [[ "${project}" == "${PROJECT_ID}" && "${role_id}" == "${expected_role_id}" && "${permissions}" == "${expected_permissions}" ]] || fail "a reviewed custom IAM role does not match its exact permission contract"
done

if [[ "${MODE}" == "recovery" ]]; then
  count="$(resource_count 'google_project_iam_custom_role.state_bucket_policy_reader[0]')" || fail "the recovery reader role contract could not be evaluated"
  (( count == 1 )) || fail "recovery mode requires the reviewed state-reader custom role"
fi

validated_records='[]'
binding_addresses_to_validate=("${known_binding_addresses[@]}")
if [[ "${MODE}" == "recovery" ]]; then
  binding_addresses_to_validate=("${recovery_addresses[@]}")
fi
for address in "${binding_addresses_to_validate[@]}"; do
  count="$(resource_count "${address}")" || fail "the IAM binding contract could not be evaluated"
  (( count == 0 )) && continue
  (( count == 1 )) || fail "a reviewed IAM binding address is duplicated"
  actual_project="$(resource_value "${address}" project)" || fail "a reviewed IAM binding is not concrete"
  actual_member="$(resource_value "${address}" member)" || fail "a reviewed IAM binding is not concrete"
  actual_role="$(resource_value "${address}" role)" || fail "a reviewed IAM binding is not concrete"
  actual_expression="$(condition_expression "${address}")" || fail "a reviewed IAM binding must have one concrete condition"
  expected_role="$(expected_role_for_address "${address}")" || fail "a reviewed IAM binding address has no role contract"
  expected_member="$(expected_member_for_address "${address}")" || fail "a reviewed IAM binding address has no member contract"
  expected_expression="$(expected_expression_for_address "${address}")" || fail "a reviewed IAM binding address has no condition contract"
  [[ "${actual_project}" == "${PROJECT_ID}" ]] || fail "a reviewed IAM binding uses the wrong project"
  [[ "${actual_member}" == "${expected_member}" ]] || fail "a reviewed IAM binding uses the wrong member"
  [[ "${actual_role}" == "${expected_role}" ]] || fail "a reviewed IAM binding uses the wrong role"
  [[ "${actual_expression}" == "${expected_expression}" ]] || fail "a reviewed IAM binding condition does not match its exact authorization contract"
  record="$(jq -cn --arg address "${address}" --arg expression "${actual_expression}" '{address: $address, expression: $expression}')"
  validated_records="$(jq -c --argjson record "${record}" '. + [$record]' <<< "${validated_records}")"
done

validated_count="$(jq -er 'length' 2>/dev/null <<< "${validated_records}")" || fail "the validated IAM-condition contract could not be evaluated"
(( validated_count > 0 )) || fail "the saved plan contains no reviewed IAM condition to lint"
unique_expressions="$(jq -ce '[.[].expression] | unique' 2>/dev/null <<< "${validated_records}")" || fail "the IAM conditions could not be normalized"
unique_count="$(jq -er 'length' 2>/dev/null <<< "${unique_expressions}")" || fail "the IAM conditions could not be normalized"

lint_output="$(mktemp "${TMPDIR:-/tmp}/seen-registry-iam-lint.XXXXXX")"
cleanup() {
  rm -f -- "${lint_output}"
}
trap cleanup EXIT
chmod 0600 "${lint_output}"

for (( index = 0; index < unique_count; index++ )); do
  expression="$(jq -r --argjson index "${index}" '.[$index]' 2>/dev/null <<< "${unique_expressions}")"
  [[ -n "${expression}" ]] || fail "a reviewed IAM condition is not concrete"
  : > "${lint_output}"
  if ! gcloud alpha iam policies lint-condition \
    --expression="${expression}" \
    --title="seen_registry_iam_condition_$((index + 1))" \
    --format=json \
    --quiet > "${lint_output}" 2>/dev/null; then
    fail "the official IAM linter could not evaluate a condition"
  fi
  if ! jq -e '
    (type == "array" and length == 0) or
    (type == "object" and (
      (keys | length) == 0 or
      (keys == ["lintResults"] and (.lintResults | type == "array" and length == 0))
    ))
  ' "${lint_output}" >/dev/null 2>&1; then
    fail "the official IAM linter reported a finding"
  fi
done

printf 'Official IAM lint accepted %d unique condition(s) across %d validated %s-mode binding(s).\n' \
  "${unique_count}" "${validated_count}" "${MODE}"
