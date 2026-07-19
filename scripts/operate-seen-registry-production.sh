#!/usr/bin/env bash

set -euo pipefail

readonly PROD_PROJECT_ID="seen-registry-prod-476219"
readonly PROD_REGION="us-central1"
readonly PROD_KEY_RING="seen-registry-prod"
readonly PROD_IMAGE_PREFIX="${PROD_REGION}-docker.pkg.dev/${PROD_PROJECT_ID}/seen-registry/seen-registry@sha256:"

declare -a CLEANUP_PATHS=()

cleanup_sensitive_files() {
  if (( ${#CLEANUP_PATHS[@]} > 0 )); then
    rm -f -- "${CLEANUP_PATHS[@]}"
  fi
}

trap cleanup_sensitive_files EXIT

require_exact_runtime_contract() {
  : "${OPERATION:?OPERATION is required}"
  : "${OPERATION_CONFIRMATION:?OPERATION_CONFIRMATION is required}"
  : "${EXPECTED_OPERATOR_SERVICE_ACCOUNT:?EXPECTED_OPERATOR_SERVICE_ACCOUNT is required}"

  test "${OPERATION_CONFIRMATION}" = "OPERATE_SEEN_REGISTRY_PRODUCTION"

  local active_account
  active_account="$(gcloud auth list --filter=status:ACTIVE --format='value(account)')"
  test "${active_account}" = "${EXPECTED_OPERATOR_SERVICE_ACCOUNT}"
}

require_expected_next_version() {
  : "${EXPECTED_NEXT_VERSION:?EXPECTED_NEXT_VERSION is required for material operations}"
  [[ "${EXPECTED_NEXT_VERSION}" =~ ^[1-9][0-9]*$ ]]
  test -z "${EXPECTED_IMAGE_URI:-}"
}

next_numeric_version() {
  local highest=0
  local version
  while IFS= read -r version; do
    [[ "${version}" =~ ^[1-9][0-9]*$ ]]
    if (( version > highest )); then
      highest="${version}"
    fi
  done
  printf '%s\n' "$((highest + 1))"
}

create_kms_version() {
  local key_name="$1"
  require_expected_next_version
  test -z "${EXPECTED_PAYLOAD_SHA256:-}"

  local actual_next_version
  actual_next_version="$({
    gcloud kms keys versions list \
      --project "${PROD_PROJECT_ID}" \
      --location "${PROD_REGION}" \
      --keyring "${PROD_KEY_RING}" \
      --key "${key_name}" \
      --format='value(name)' |
      awk -F/ 'NF { print $NF }'
  } | next_numeric_version)"
  test "${actual_next_version}" = "${EXPECTED_NEXT_VERSION}"

  local create_result
  create_result="$(mktemp "${RUNNER_TEMP}/seen-registry-kms-version.XXXXXX.json")"
  CLEANUP_PATHS+=("${create_result}")
  chmod 0600 "${create_result}"

  if ! gcloud kms keys versions create \
    --project "${PROD_PROJECT_ID}" \
    --location "${PROD_REGION}" \
    --keyring "${PROD_KEY_RING}" \
    --key "${key_name}" \
    --format=json > "${create_result}" 2>/dev/null; then
    echo "The reviewed KMS version creation failed." >&2
    return 1
  fi

  jq -e \
    --arg expected_name "projects/${PROD_PROJECT_ID}/locations/${PROD_REGION}/keyRings/${PROD_KEY_RING}/cryptoKeys/${key_name}/cryptoKeyVersions/${EXPECTED_NEXT_VERSION}" \
    '.name == $expected_name and .algorithm == "EC_SIGN_ED25519" and .protectionLevel == "SOFTWARE" and .state == "ENABLED"' \
    "${create_result}" >/dev/null

  printf 'Created reviewed KMS version %s for %s.\n' "${EXPECTED_NEXT_VERSION}" "${key_name}" >> "${GITHUB_STEP_SUMMARY}"
}

add_secret_version() {
  local secret_name="$1"
  require_expected_next_version
  : "${EXPECTED_PAYLOAD_SHA256:?EXPECTED_PAYLOAD_SHA256 is required for secret material}"
  : "${MATERIAL_PAYLOAD_B64:?MATERIAL_PAYLOAD_B64 is required for secret material}"
  [[ "${EXPECTED_PAYLOAD_SHA256}" =~ ^[0-9a-f]{64}$ ]]
  [[ "${MATERIAL_PAYLOAD_B64}" =~ ^[A-Za-z0-9+/]+={0,2}$ ]]

  local actual_next_version
  actual_next_version="$({
    gcloud secrets versions list "${secret_name}" \
      --project "${PROD_PROJECT_ID}" \
      --format='value(name)' |
      awk -F/ 'NF { print $NF }'
  } | next_numeric_version)"
  test "${actual_next_version}" = "${EXPECTED_NEXT_VERSION}"

  local payload_file
  local add_result
  payload_file="$(mktemp "${RUNNER_TEMP}/seen-registry-secret-payload.XXXXXX")"
  add_result="$(mktemp "${RUNNER_TEMP}/seen-registry-secret-version.XXXXXX.json")"
  CLEANUP_PATHS+=("${payload_file}" "${add_result}")
  chmod 0600 "${payload_file}" "${add_result}"

  printf '%s' "${MATERIAL_PAYLOAD_B64}" | base64 --decode > "${payload_file}"
  local payload_size
  payload_size="$(stat -c '%s' "${payload_file}")"
  (( payload_size > 0 && payload_size <= 65536 ))
  test "$(sha256sum "${payload_file}" | awk '{print $1}')" = "${EXPECTED_PAYLOAD_SHA256}"

  if ! gcloud secrets versions add "${secret_name}" \
    --project "${PROD_PROJECT_ID}" \
    --data-file "${payload_file}" \
    --format=json > "${add_result}" 2>/dev/null; then
    echo "The reviewed secret-version addition failed." >&2
    return 1
  fi

  jq -e \
    --arg expected_name "projects/${PROD_PROJECT_ID}/secrets/${secret_name}/versions/${EXPECTED_NEXT_VERSION}" \
    '.name == $expected_name and .state == "ENABLED"' \
    "${add_result}" >/dev/null

  printf 'Added reviewed secret version %s for %s with SHA-256 %s.\n' \
    "${EXPECTED_NEXT_VERSION}" "${secret_name}" "${EXPECTED_PAYLOAD_SHA256}" >> "${GITHUB_STEP_SUMMARY}"
}

execute_job() {
  local job_name="$1"
  local runtime_service_account="$2"
  local expected_args_json="$3"

  test -z "${EXPECTED_NEXT_VERSION:-}"
  test -z "${EXPECTED_PAYLOAD_SHA256:-}"
  : "${EXPECTED_IMAGE_URI:?EXPECTED_IMAGE_URI is required for job operations}"
  [[ "${EXPECTED_IMAGE_URI}" == "${PROD_IMAGE_PREFIX}"[0-9a-f][0-9a-f]* ]]
  [[ "${EXPECTED_IMAGE_URI}" =~ @sha256:[0-9a-f]{64}$ ]]

  local job_definition
  local job_definition_diagnostics
  local execution_result
  local execution_diagnostics
  job_definition="$(mktemp "${RUNNER_TEMP}/seen-registry-job-definition.XXXXXX.json")"
  job_definition_diagnostics="$(mktemp "${RUNNER_TEMP}/seen-registry-job-definition.XXXXXX.log")"
  execution_result="$(mktemp "${RUNNER_TEMP}/seen-registry-execution.XXXXXX.json")"
  execution_diagnostics="$(mktemp "${RUNNER_TEMP}/seen-registry-execution.XXXXXX.log")"
  CLEANUP_PATHS+=(
    "${job_definition}"
    "${job_definition_diagnostics}"
    "${execution_result}"
    "${execution_diagnostics}"
  )
  chmod 0600 \
    "${job_definition}" \
    "${job_definition_diagnostics}" \
    "${execution_result}" \
    "${execution_diagnostics}"

  if ! gcloud run jobs describe "${job_name}" \
    --project "${PROD_PROJECT_ID}" \
    --region "${PROD_REGION}" \
    --format=json > "${job_definition}" 2> "${job_definition_diagnostics}"; then
    echo "The reviewed production job preflight failed; diagnostics remain private to the protected runner." >&2
    return 1
  fi

  jq -e \
    --arg job "${job_name}" \
    --arg service_account "${runtime_service_account}" \
    --arg image "${EXPECTED_IMAGE_URI}" \
    --argjson expected_args "${expected_args_json}" \
    '
      .metadata.name == $job and
      .spec.template.spec.parallelism == 1 and
      .spec.template.spec.taskCount == 1 and
      .spec.template.spec.template.spec.maxRetries == 0 and
      .spec.template.spec.template.spec.serviceAccountName == $service_account and
      (.spec.template.spec.template.spec.containers | length) == 1 and
      .spec.template.spec.template.spec.containers[0].name == "registry" and
      .spec.template.spec.template.spec.containers[0].image == $image and
      .spec.template.spec.template.spec.containers[0].args == $expected_args
    ' "${job_definition}" >/dev/null

  if ! gcloud run jobs execute "${job_name}" \
    --project "${PROD_PROJECT_ID}" \
    --region "${PROD_REGION}" \
    --wait \
    --format=json > "${execution_result}" 2> "${execution_diagnostics}"; then
    echo "The reviewed production job execution failed; diagnostics remain private to the protected runner." >&2
    return 1
  fi

  jq -e \
    --arg job "${job_name}" \
    --arg service_account "${runtime_service_account}" \
    --arg image "${EXPECTED_IMAGE_URI}" \
    --argjson expected_args "${expected_args_json}" \
    '
      .metadata.labels["run.googleapis.com/job"] == $job and
      (.metadata.name | type == "string" and test("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")) and
      .spec.parallelism == 1 and
      .spec.taskCount == 1 and
      .spec.template.spec.maxRetries == 0 and
      .spec.template.spec.serviceAccountName == $service_account and
      (.spec.template.spec.containers | length) == 1 and
      .spec.template.spec.containers[0].image == $image and
      .spec.template.spec.containers[0].args == $expected_args and
      .status.succeededCount == 1 and
      (.status.failedCount // 0) == 0 and
      (.status.runningCount // 0) == 0 and
      any(.status.conditions[]; .type == "Completed" and .status == "True")
    ' "${execution_result}" >/dev/null

  local execution_name
  execution_name="$(jq -r '.metadata.name' "${execution_result}")"
  printf 'Completed reviewed job %s as execution %s with image %s.\n' \
    "${job_name}" "${execution_name}" "${EXPECTED_IMAGE_URI}" >> "${GITHUB_STEP_SUMMARY}"
}

main() {
  require_exact_runtime_contract

  case "${OPERATION}" in
    create_releases_key_version)
      create_kms_version "seen-registry-prod-releases"
      ;;
    create_security_key_version)
      create_kms_version "seen-registry-prod-security"
      ;;
    create_snapshot_key_version)
      create_kms_version "seen-registry-prod-snapshot"
      ;;
    create_timestamp_key_version)
      create_kms_version "seen-registry-prod-timestamp"
      ;;
    add_bootstrap_root_envelope)
      add_secret_version "seen-registry-prod-root-envelope"
      ;;
    add_bootstrap_targets_envelope)
      add_secret_version "seen-registry-prod-targets-envelope"
      ;;
    add_targets_renewal_envelope)
      add_secret_version "seen-registry-prod-targets-renewal-envelope"
      ;;
    add_targets_releases_rotation_envelope)
      add_secret_version "seen-registry-prod-targets-releases-rotation-envelope"
      ;;
    add_targets_security_rotation_envelope)
      add_secret_version "seen-registry-prod-targets-security-rotation-envelope"
      ;;
    add_root_rotation_envelope)
      add_secret_version "seen-registry-prod-root-rotation-envelope"
      ;;
    run_offline_bootstrap)
      execute_job \
        "seen-registry-prod-offline-bootstrap" \
        "seen-reg-prod-offline-boot@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["import-offline-bootstrap"]'
      ;;
    run_online_bootstrap)
      execute_job \
        "seen-registry-prod-online-bootstrap" \
        "seen-reg-prod-online-bootstrap@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["bootstrap-online"]'
      ;;
    run_root_verifier)
      execute_job \
        "seen-registry-prod-root-verify-v2" \
        "seen-reg-prod-root-verifier@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["verify-root-chain"]'
      ;;
    run_release_refresh)
      execute_job \
        "seen-registry-prod-release-refresh-v2" \
        "seen-reg-prod-release-refresh@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["refresh-releases-once"]'
      ;;
    run_security_refresh)
      execute_job \
        "seen-registry-prod-security-refresh-v2" \
        "seen-reg-prod-security-refresh@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["refresh-security-once"]'
      ;;
    run_targets_renewal)
      execute_job \
        "seen-registry-prod-targets-renewal" \
        "seen-reg-prod-targets-renew@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["import-offline-targets-renewal","/var/run/seen-transfer/envelope.json"]'
      ;;
    run_targets_releases_rotation)
      execute_job \
        "seen-registry-prod-targets-release-rotate" \
        "seen-reg-prod-release-rotate@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["import-offline-targets-rotation","releases","/var/run/seen-transfer/envelope.json"]'
      ;;
    run_targets_security_rotation)
      execute_job \
        "seen-registry-prod-targets-security-rotate" \
        "seen-reg-prod-security-rotate@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["import-offline-targets-rotation","security","/var/run/seen-transfer/envelope.json"]'
      ;;
    run_root_import)
      execute_job \
        "seen-registry-prod-root-import" \
        "seen-reg-prod-root-importer@${PROD_PROJECT_ID}.iam.gserviceaccount.com" \
        '["import-offline-root-rotation","/var/run/seen-transfer/envelope.json"]'
      ;;
    *)
      echo "Unsupported production registry operation." >&2
      return 1
      ;;
  esac
}

main "$@"
