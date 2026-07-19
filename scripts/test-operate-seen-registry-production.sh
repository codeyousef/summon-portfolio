#!/usr/bin/env bash

set -euo pipefail

fake_record_call() {
  printf '%s\n' "$1" >> "${FAKE_GCLOUD_LOG:?FAKE_GCLOUD_LOG is required}"
}

fake_flag_value() {
  local expected_flag="$1"
  shift

  while (( $# > 0 )); do
    if [[ "$1" == "${expected_flag}" ]]; then
      (( $# >= 2 )) || return 1
      printf '%s\n' "$2"
      return 0
    fi
    shift
  done
  return 1
}

fake_job_definition() {
  local name="${FAKE_EXPECTED_JOB_NAME}"
  local task_count=1
  local parallelism=1
  local max_retries=0
  local service_account="${FAKE_EXPECTED_JOB_SERVICE_ACCOUNT}"
  local container_name=registry
  local image="${FAKE_EXPECTED_IMAGE}"
  local args_json="${FAKE_EXPECTED_JOB_ARGS_JSON}"

  case "${FAKE_JOB_MUTATION:-}" in
    '') ;;
    name) name="${name}-altered" ;;
    task_count) task_count=2 ;;
    parallelism) parallelism=2 ;;
    max_retries) max_retries=1 ;;
    service_account) service_account="altered@seen-registry-prod-476219.iam.gserviceaccount.com" ;;
    container_name) container_name=altered ;;
    image) image="us-central1-docker.pkg.dev/seen-registry-prod-476219/seen-registry/seen-registry@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" ;;
    args) args_json='["altered"]' ;;
    execution_status) ;;
    *) return 64 ;;
  esac

  jq -n \
    --arg name "${name}" \
    --arg service_account "${service_account}" \
    --arg container_name "${container_name}" \
    --arg image "${image}" \
    --argjson task_count "${task_count}" \
    --argjson parallelism "${parallelism}" \
    --argjson max_retries "${max_retries}" \
    --argjson args "${args_json}" \
    '{
      metadata: {name: $name},
      spec: {
        template: {
          spec: {
            parallelism: $parallelism,
            taskCount: $task_count,
            template: {
              spec: {
                maxRetries: $max_retries,
                serviceAccountName: $service_account,
                containers: [{
                  name: $container_name,
                  image: $image,
                  args: $args
                }]
              }
            }
          }
        }
      }
    }'
}

fake_job_execution() {
  local succeeded_count=1
  local failed_count=0
  local completion_status=True

  if [[ "${FAKE_JOB_MUTATION:-}" == execution_status ]]; then
    succeeded_count=0
    failed_count=1
    completion_status=False
  fi

  jq -n \
    --arg job "${FAKE_EXPECTED_JOB_NAME}" \
    --arg execution "${FAKE_EXPECTED_JOB_NAME}-semantic-test" \
    --arg service_account "${FAKE_EXPECTED_JOB_SERVICE_ACCOUNT}" \
    --arg image "${FAKE_EXPECTED_IMAGE}" \
    --arg completion_status "${completion_status}" \
    --argjson args "${FAKE_EXPECTED_JOB_ARGS_JSON}" \
    --argjson succeeded_count "${succeeded_count}" \
    --argjson failed_count "${failed_count}" \
    '{
      metadata: {
        name: $execution,
        labels: {"run.googleapis.com/job": $job}
      },
      spec: {
        parallelism: 1,
        taskCount: 1,
        template: {
          spec: {
            maxRetries: 0,
            serviceAccountName: $service_account,
            containers: [{image: $image, args: $args}]
          }
        }
      },
      status: {
        succeededCount: $succeeded_count,
        failedCount: $failed_count,
        runningCount: 0,
        conditions: [{type: "Completed", status: $completion_status}]
      }
    }'
}

fake_kms_version() {
  local version="${FAKE_EXPECTED_VERSION}"
  local algorithm=EC_SIGN_ED25519
  local protection_level=SOFTWARE
  local state=ENABLED

  case "${FAKE_KMS_MUTATION:-}" in
    '') ;;
    version) version="$((FAKE_EXPECTED_VERSION + 1))" ;;
    algorithm) algorithm=RSA_SIGN_PSS_2048_SHA256 ;;
    protection) protection_level=HSM ;;
    state) state=DISABLED ;;
    *) return 64 ;;
  esac

  jq -n \
    --arg name "projects/seen-registry-prod-476219/locations/us-central1/keyRings/seen-registry-prod/cryptoKeys/${FAKE_EXPECTED_KMS_KEY}/cryptoKeyVersions/${version}" \
    --arg algorithm "${algorithm}" \
    --arg protection_level "${protection_level}" \
    --arg state "${state}" \
    '{
      name: $name,
      algorithm: $algorithm,
      protectionLevel: $protection_level,
      state: $state
    }'
}

fake_gcloud() {
  if [[ "${1:-}" == auth && "${2:-}" == list ]]; then
    fake_record_call auth-list
    printf '%s\n' "${FAKE_ACTIVE_ACCOUNT:?FAKE_ACTIVE_ACCOUNT is required}"
    return 0
  fi

  if [[ "${1:-}" == run && "${2:-}" == jobs && "${3:-}" == describe ]]; then
    fake_record_call job-describe
    test "${4:-}" = "${FAKE_EXPECTED_JOB_NAME}"
    fake_job_definition
    return 0
  fi

  if [[ "${1:-}" == run && "${2:-}" == jobs && "${3:-}" == execute ]]; then
    fake_record_call job-execute
    test "${4:-}" = "${FAKE_EXPECTED_JOB_NAME}"
    fake_job_execution
    return 0
  fi

  if [[ "${1:-}" == secrets && "${2:-}" == versions && "${3:-}" == list ]]; then
    fake_record_call secret-list
    test "${4:-}" = "${FAKE_EXPECTED_SECRET_NAME}"
    printf '%s\n' \
      "projects/seen-registry-prod-476219/secrets/${FAKE_EXPECTED_SECRET_NAME}/versions/1" \
      "projects/seen-registry-prod-476219/secrets/${FAKE_EXPECTED_SECRET_NAME}/versions/2"
    return 0
  fi

  if [[ "${1:-}" == secrets && "${2:-}" == versions && "${3:-}" == add ]]; then
    fake_record_call secret-add
    test "${4:-}" = "${FAKE_EXPECTED_SECRET_NAME}"

    local data_file
    data_file="$(fake_flag_value --data-file "$@")"
    test -f "${data_file}"
    sha256sum "${data_file}" | awk '{print $1}' > "${FAKE_CAPTURED_PAYLOAD_SHA_FILE}"

    jq -n \
      --arg name "projects/seen-registry-prod-476219/secrets/${FAKE_EXPECTED_SECRET_NAME}/versions/${FAKE_EXPECTED_VERSION}" \
      '{name: $name, state: "ENABLED"}'
    return 0
  fi

  if [[ "${1:-}" == kms && "${2:-}" == keys && "${3:-}" == versions && "${4:-}" == list ]]; then
    fake_record_call kms-list
    test "$(fake_flag_value --key "$@")" = "${FAKE_EXPECTED_KMS_KEY}"
    printf '%s\n' \
      "projects/seen-registry-prod-476219/locations/us-central1/keyRings/seen-registry-prod/cryptoKeys/${FAKE_EXPECTED_KMS_KEY}/cryptoKeyVersions/1" \
      "projects/seen-registry-prod-476219/locations/us-central1/keyRings/seen-registry-prod/cryptoKeys/${FAKE_EXPECTED_KMS_KEY}/cryptoKeyVersions/2"
    return 0
  fi

  if [[ "${1:-}" == kms && "${2:-}" == keys && "${3:-}" == versions && "${4:-}" == create ]]; then
    fake_record_call kms-create
    test "$(fake_flag_value --key "$@")" = "${FAKE_EXPECTED_KMS_KEY}"
    fake_kms_version
    return 0
  fi

  printf 'Unexpected fake gcloud invocation.\n' >&2
  return 64
}

if [[ "${SEEN_REGISTRY_FAKE_GCLOUD:-}" == 1 ]]; then
  fake_gcloud "$@" && exit 0
  status=$?
  exit "${status}"
fi

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_SCRIPT="$(realpath "${BASH_SOURCE[0]}")"
readonly OPERATIONS_SCRIPT="${SCRIPT_DIR}/operate-seen-registry-production.sh"
readonly ORIGINAL_PATH="${PATH}"
readonly PROD_PROJECT_ID=seen-registry-prod-476219
readonly MATERIALS_ACCOUNT="seen-registry-prod-materials@${PROD_PROJECT_ID}.iam.gserviceaccount.com"
readonly JOBS_ACCOUNT="seen-registry-prod-job-runner@${PROD_PROJECT_ID}.iam.gserviceaccount.com"
readonly JOB_NAME=seen-registry-prod-release-refresh-v2
readonly JOB_SERVICE_ACCOUNT="seen-reg-prod-release-refresh@${PROD_PROJECT_ID}.iam.gserviceaccount.com"
readonly JOB_ARGS_JSON='["refresh-releases-once"]'
readonly IMAGE_URI="us-central1-docker.pkg.dev/${PROD_PROJECT_ID}/seen-registry/seen-registry@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
readonly SECRET_NAME=seen-registry-prod-targets-renewal-envelope
readonly KMS_KEY=seen-registry-prod-releases
readonly EXPECTED_VERSION=3

for required_command in awk base64 bash find jq mktemp paste realpath sha256sum; do
  command -v "${required_command}" >/dev/null || fail "${required_command} is required"
done
test -f "${OPERATIONS_SCRIPT}" || fail "production operations script was not found"

TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/seen-registry-operations-test.XXXXXX")"
cleanup_test_root() {
  rm -rf -- "${TEST_ROOT}"
}
trap cleanup_test_root EXIT

readonly FAKE_BIN="${TEST_ROOT}/bin"
mkdir -p "${FAKE_BIN}" "${TEST_ROOT}/cases"
ln -s "${TEST_SCRIPT}" "${FAKE_BIN}/gcloud"

CASE_DIR=
RUNNER_TEMP_DIR=
CALL_LOG=
CAPTURED_PAYLOAD_SHA_FILE=
CASE_STDOUT=
CASE_STDERR=
CASE_SUMMARY=

start_case() {
  local case_name="$1"
  CASE_DIR="${TEST_ROOT}/cases/${case_name}"
  RUNNER_TEMP_DIR="${CASE_DIR}/runner"
  CALL_LOG="${CASE_DIR}/gcloud.log"
  CAPTURED_PAYLOAD_SHA_FILE="${CASE_DIR}/captured-payload.sha256"
  CASE_STDOUT="${CASE_DIR}/stdout"
  CASE_STDERR="${CASE_DIR}/stderr"
  CASE_SUMMARY="${CASE_DIR}/summary"

  mkdir -p "${RUNNER_TEMP_DIR}"
  : > "${CALL_LOG}"
  : > "${CASE_STDOUT}"
  : > "${CASE_STDERR}"
  : > "${CASE_SUMMARY}"
}

invoke_operation() {
  local operation="$1"
  local operator_account="$2"
  local expected_next_version="$3"
  local expected_payload_sha256="$4"
  local expected_image_uri="$5"
  local material_payload_b64="$6"
  local mutation="$7"
  local fake_expected_job_name="${8:-${JOB_NAME}}"
  local fake_expected_job_service_account="${9:-${JOB_SERVICE_ACCOUNT}}"
  local fake_expected_job_args_json="${10:-${JOB_ARGS_JSON}}"

  (
    export PATH="${FAKE_BIN}:${ORIGINAL_PATH}"
    export SEEN_REGISTRY_FAKE_GCLOUD=1
    export FAKE_GCLOUD_LOG="${CALL_LOG}"
    export FAKE_CAPTURED_PAYLOAD_SHA_FILE="${CAPTURED_PAYLOAD_SHA_FILE}"
    export FAKE_ACTIVE_ACCOUNT="${operator_account}"
    export FAKE_EXPECTED_JOB_NAME="${fake_expected_job_name}"
    export FAKE_EXPECTED_JOB_SERVICE_ACCOUNT="${fake_expected_job_service_account}"
    export FAKE_EXPECTED_JOB_ARGS_JSON="${fake_expected_job_args_json}"
    export FAKE_EXPECTED_IMAGE="${IMAGE_URI}"
    export FAKE_EXPECTED_SECRET_NAME="${SECRET_NAME}"
    export FAKE_EXPECTED_KMS_KEY="${KMS_KEY}"
    export FAKE_EXPECTED_VERSION="${EXPECTED_VERSION}"
    export FAKE_JOB_MUTATION="${mutation}"
    export FAKE_KMS_MUTATION="${mutation}"

    export RUNNER_TEMP="${RUNNER_TEMP_DIR}"
    export GITHUB_STEP_SUMMARY="${CASE_SUMMARY}"
    export OPERATION="${operation}"
    export OPERATION_CONFIRMATION=OPERATE_SEEN_REGISTRY_PRODUCTION
    export EXPECTED_OPERATOR_SERVICE_ACCOUNT="${operator_account}"
    export EXPECTED_NEXT_VERSION="${expected_next_version}"
    export EXPECTED_PAYLOAD_SHA256="${expected_payload_sha256}"
    export EXPECTED_IMAGE_URI="${expected_image_uri}"
    if [[ -n "${material_payload_b64}" ]]; then
      export MATERIAL_PAYLOAD_B64="${material_payload_b64}"
    else
      unset MATERIAL_PAYLOAD_B64
    fi

    bash "${OPERATIONS_SCRIPT}"
  ) > "${CASE_STDOUT}" 2> "${CASE_STDERR}"
}

expect_calls() {
  local expected_calls="$1"
  local actual_calls
  actual_calls="$(paste -sd, "${CALL_LOG}")"
  [[ "${actual_calls}" == "${expected_calls}" ]] || \
    fail "${CASE_DIR##*/} used unexpected gcloud call order"
}

expect_runner_clean() {
  local leftover
  leftover="$(find "${RUNNER_TEMP_DIR}" -mindepth 1 -print -quit)"
  [[ -z "${leftover}" ]] || fail "${CASE_DIR##*/} left an operations temp file"
}

test_job_contract() {
  local -a job_cases=(
    'run_offline_bootstrap|seen-registry-prod-offline-bootstrap|seen-reg-prod-offline-boot@seen-registry-prod-476219.iam.gserviceaccount.com|["import-offline-bootstrap"]'
    'run_online_bootstrap|seen-registry-prod-online-bootstrap|seen-reg-prod-online-bootstrap@seen-registry-prod-476219.iam.gserviceaccount.com|["bootstrap-online"]'
    'run_root_verifier|seen-registry-prod-root-verify-v2|seen-reg-prod-root-verifier@seen-registry-prod-476219.iam.gserviceaccount.com|["verify-root-chain"]'
    'run_release_refresh|seen-registry-prod-release-refresh-v2|seen-reg-prod-release-refresh@seen-registry-prod-476219.iam.gserviceaccount.com|["refresh-releases-once"]'
    'run_security_refresh|seen-registry-prod-security-refresh-v2|seen-reg-prod-security-refresh@seen-registry-prod-476219.iam.gserviceaccount.com|["refresh-security-once"]'
    'run_targets_renewal|seen-registry-prod-targets-renewal|seen-reg-prod-targets-renew@seen-registry-prod-476219.iam.gserviceaccount.com|["import-offline-targets-renewal","/var/run/seen-transfer/envelope.json"]'
    'run_targets_releases_rotation|seen-registry-prod-targets-release-rotate|seen-reg-prod-release-rotate@seen-registry-prod-476219.iam.gserviceaccount.com|["import-offline-targets-rotation","releases","/var/run/seen-transfer/envelope.json"]'
    'run_targets_security_rotation|seen-registry-prod-targets-security-rotate|seen-reg-prod-security-rotate@seen-registry-prod-476219.iam.gserviceaccount.com|["import-offline-targets-rotation","security","/var/run/seen-transfer/envelope.json"]'
    'run_root_import|seen-registry-prod-root-import|seen-reg-prod-root-importer@seen-registry-prod-476219.iam.gserviceaccount.com|["import-offline-root-rotation","/var/run/seen-transfer/envelope.json"]'
  )

  local job_case
  local operation
  local expected_job_name
  local expected_service_account
  local expected_args_json
  for job_case in "${job_cases[@]}"; do
    IFS='|' read -r \
      operation \
      expected_job_name \
      expected_service_account \
      expected_args_json <<< "${job_case}"

    start_case "job-valid-${operation}"
    if ! invoke_operation \
      "${operation}" \
      "${JOBS_ACCOUNT}" \
      '' \
      '' \
      "${IMAGE_URI}" \
      '' \
      '' \
      "${expected_job_name}" \
      "${expected_service_account}" \
      "${expected_args_json}"; then
      fail "exact ${operation} job target, runtime identity, or arguments were rejected"
    fi
    expect_calls auth-list,job-describe,job-execute
    expect_runner_clean
  done

  local mutation
  for mutation in name task_count parallelism max_retries service_account container_name image args; do
    start_case "job-preflight-${mutation}"
    if invoke_operation \
      run_release_refresh "${JOBS_ACCOUNT}" '' '' "${IMAGE_URI}" '' "${mutation}"; then
      fail "altered job preflight ${mutation} was accepted"
    fi
    expect_calls auth-list,job-describe
    expect_runner_clean
  done

  start_case job-failed-execution
  if invoke_operation \
    run_release_refresh "${JOBS_ACCOUNT}" '' '' "${IMAGE_URI}" '' execution_status; then
    fail "unsuccessful job execution was accepted"
  fi
  expect_calls auth-list,job-describe,job-execute
  expect_runner_clean
}

test_unsupported_operation_exclusion() {
  start_case excluded-source-verifier-operation
  if invoke_operation \
    run_source_verifier "${JOBS_ACCOUNT}" '' '' "${IMAGE_URI}" '' ''; then
    fail "excluded source-verifier operation was accepted"
  fi
  expect_calls auth-list
  expect_runner_clean

  start_case unknown-operation
  if invoke_operation \
    unsupported_production_mutation "${MATERIALS_ACCOUNT}" '' '' '' '' ''; then
    fail "unknown production operation was accepted"
  fi
  expect_calls auth-list
  expect_runner_clean
}

test_secret_contract() {
  local payload
  local payload_b64
  local payload_sha256
  payload='{"kind":"semantic-test-envelope","version":3}'
  payload_b64="$(printf '%s' "${payload}" | base64 -w 0)"
  payload_sha256="$(printf '%s' "${payload}" | sha256sum | awk '{print $1}')"

  start_case secret-valid
  if ! invoke_operation \
    add_targets_renewal_envelope \
    "${MATERIALS_ACCOUNT}" \
    "${EXPECTED_VERSION}" \
    "${payload_sha256}" \
    '' \
    "${payload_b64}" \
    ''; then
    fail "exact secret payload contract was rejected"
  fi
  expect_calls auth-list,secret-list,secret-add
  test -f "${CAPTURED_PAYLOAD_SHA_FILE}" || fail "fake gcloud did not inspect decoded payload bytes"
  test "$(< "${CAPTURED_PAYLOAD_SHA_FILE}")" = "${payload_sha256}" || \
    fail "secret payload was not decoded to the exact reviewed bytes"
  if grep -Fq -- "${payload}" "${CASE_STDOUT}" "${CASE_STDERR}"; then
    fail "secret payload appeared in operation output"
  fi
  if grep -Fq -- "${payload_b64}" "${CASE_STDOUT}" "${CASE_STDERR}"; then
    fail "encoded secret payload appeared in operation output"
  fi
  expect_runner_clean

  start_case secret-wrong-next-version
  if invoke_operation \
    add_targets_renewal_envelope \
    "${MATERIALS_ACCOUNT}" \
    4 \
    "${payload_sha256}" \
    '' \
    "${payload_b64}" \
    ''; then
    fail "incorrect next secret version was accepted"
  fi
  expect_calls auth-list,secret-list
  expect_runner_clean

  start_case secret-wrong-payload-sha
  if invoke_operation \
    add_targets_renewal_envelope \
    "${MATERIALS_ACCOUNT}" \
    "${EXPECTED_VERSION}" \
    0000000000000000000000000000000000000000000000000000000000000000 \
    '' \
    "${payload_b64}" \
    ''; then
    fail "incorrect secret payload SHA-256 was accepted"
  fi
  expect_calls auth-list,secret-list
  expect_runner_clean
}

test_kms_contract() {
  start_case kms-valid
  if ! invoke_operation \
    create_releases_key_version \
    "${MATERIALS_ACCOUNT}" \
    "${EXPECTED_VERSION}" \
    '' \
    '' \
    '' \
    ''; then
    fail "exact KMS version contract was rejected"
  fi
  expect_calls auth-list,kms-list,kms-create
  expect_runner_clean

  start_case kms-wrong-next-version
  if invoke_operation \
    create_releases_key_version "${MATERIALS_ACCOUNT}" 4 '' '' '' ''; then
    fail "incorrect next KMS version was accepted"
  fi
  expect_calls auth-list,kms-list
  expect_runner_clean

  local mutation
  for mutation in version algorithm protection state; do
    start_case "kms-returned-${mutation}"
    if invoke_operation \
      create_releases_key_version \
      "${MATERIALS_ACCOUNT}" \
      "${EXPECTED_VERSION}" \
      '' \
      '' \
      '' \
      "${mutation}"; then
      fail "altered returned KMS ${mutation} was accepted"
    fi
    expect_calls auth-list,kms-list,kms-create
    expect_runner_clean
  done
}

test_job_contract
test_unsupported_operation_exclusion
test_secret_contract
test_kms_contract

printf 'Protected production operation semantic tests passed.\n'
