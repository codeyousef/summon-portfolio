#!/usr/bin/env bash
set -euo pipefail
umask 077

PROJECT_ID="${GCP_PROJECT_ID:-portfolio-476219}"
REGION="${GCP_REGION:-us-central1}"
DEPLOY_EMAIL="${REGISTRY_DEPLOY_EMAIL:-gh-deploy@${PROJECT_ID}.iam.gserviceaccount.com}"
RUNTIME_ACCOUNT=seen-registry-dev
RUNTIME_EMAIL="${RUNTIME_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"
SOURCE_ACCOUNT=seen-registry-dev-source
SOURCE_EMAIL="${SOURCE_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"
SCANNER_ACCOUNT=seen-registry-dev-scanner
SCANNER_EMAIL="${SCANNER_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"
PROMOTER_ACCOUNT=seen-registry-dev-promoter
PROMOTER_EMAIL="${PROMOTER_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"
SECURITY_ACCOUNT=seen-registry-dev-security
SECURITY_EMAIL="${SECURITY_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"
ARTIFACT_REPOSITORY=seen-registry
FIRESTORE_DATABASE=seen-registry-dev
KMS_KEY_RING=seen-registry-dev
QUARANTINE_BUCKET="${PROJECT_ID}-seen-registry-dev-quarantine"
BLOB_BUCKET="${PROJECT_ID}-seen-registry-dev-blobs"
METADATA_BUCKET="${PROJECT_ID}-seen-registry-dev-metadata"
GITHUB_APP_ID_SECRET=seen-registry-dev-github-app-id
GITHUB_APP_PRIVATE_KEY_SECRET=seen-registry-dev-github-app-private-key
GITLAB_FORGE_SECRET=seen-registry-dev-gitlab-forge-token
TRUST_AND_SAFETY_SECRET=seen-registry-dev-trust-and-safety-token
SECURITY_TOKEN_SECRET=seen-registry-dev-security-token
SOURCE_JOB=seen-registry-dev-source-verify
SCANNER_JOB=seen-registry-dev-scan
PROMOTER_JOB=seen-registry-dev-promote
ENABLE_REVIEW_SCHEDULES="${REGISTRY_ENABLE_REVIEW_SCHEDULES:-false}"

export CLOUDSDK_CORE_PROJECT="${PROJECT_ID}"
export CLOUDSDK_PROJECT="${PROJECT_ID}"

for command in gcloud openssl; do
  command -v "${command}" >/dev/null || {
    echo "missing required command: ${command}" >&2
    exit 69
  }
done

ensure_generated_secret() {
  local secret_name="$1"
  if ! gcloud secrets describe "${secret_name}" >/dev/null 2>&1; then
    gcloud secrets create "${secret_name}" --replication-policy=automatic
  fi
  local enabled_version
  enabled_version="$(gcloud secrets versions list "${secret_name}" \
    --filter='state=ENABLED' \
    --sort-by='~createTime' \
    --limit=1 \
    --format='value(name)')"
  if [ -z "${enabled_version}" ]; then
    openssl rand -hex 32 \
      | gcloud secrets versions add "${secret_name}" --data-file=- >/dev/null
  fi
  gcloud secrets add-iam-policy-binding "${secret_name}" \
    --member="serviceAccount:${RUNTIME_EMAIL}" \
    --role=roles/secretmanager.secretAccessor \
    --condition=None >/dev/null
  gcloud secrets add-iam-policy-binding "${secret_name}" \
    --member="serviceAccount:${DEPLOY_EMAIL}" \
    --role=roles/secretmanager.viewer \
    --condition=None >/dev/null
}

ACTIVE_ACCOUNT="$(gcloud auth list --filter=status:ACTIVE --format='value(account)')"
if [ -z "${ACTIVE_ACCOUNT}" ]; then
  echo "an active project-owner gcloud account is required" >&2
  exit 77
fi
gcloud projects describe "${PROJECT_ID}" --format='value(projectId)' \
  | grep -Fx "${PROJECT_ID}" >/dev/null

case "${ENABLE_REVIEW_SCHEDULES}" in
  true|false) ;;
  *)
    echo 'REGISTRY_ENABLE_REVIEW_SCHEDULES must be true or false' >&2
    exit 64
    ;;
esac

ensure_service_account() {
  local account="$1"
  local display_name="$2"
  local email="${account}@${PROJECT_ID}.iam.gserviceaccount.com"
  if ! gcloud iam service-accounts describe "${email}" >/dev/null 2>&1; then
    gcloud iam service-accounts create "${account}" --display-name="${display_name}"
  fi
}

ensure_service_account "${RUNTIME_ACCOUNT}" 'Seen registry development runtime'
ensure_service_account "${SOURCE_ACCOUNT}" 'Seen registry development source verifier'
ensure_service_account "${SCANNER_ACCOUNT}" 'Seen registry development package scanner'
ensure_service_account "${PROMOTER_ACCOUNT}" 'Seen registry development release promoter'
ensure_service_account "${SECURITY_ACCOUNT}" 'Seen registry development security publisher'

ensure_generated_secret "${TRUST_AND_SAFETY_SECRET}"
ensure_generated_secret "${SECURITY_TOKEN_SECRET}"

for runtime_email in \
  "${RUNTIME_EMAIL}" \
  "${SOURCE_EMAIL}" \
  "${SCANNER_EMAIL}" \
  "${PROMOTER_EMAIL}" \
  "${SECURITY_EMAIL}"; do
  gcloud iam service-accounts add-iam-policy-binding "${runtime_email}" \
    --member="serviceAccount:${DEPLOY_EMAIL}" \
    --role=roles/iam.serviceAccountUser \
    --condition=None >/dev/null
done

if ! gcloud artifacts repositories describe "${ARTIFACT_REPOSITORY}" \
  --location "${REGION}" >/dev/null 2>&1; then
  gcloud artifacts repositories create "${ARTIFACT_REPOSITORY}" \
    --location "${REGION}" \
    --repository-format docker \
    --description='Isolated Seen registry images'
fi
gcloud artifacts repositories add-iam-policy-binding "${ARTIFACT_REPOSITORY}" \
  --location "${REGION}" \
  --member="serviceAccount:${DEPLOY_EMAIL}" \
  --role=roles/artifactregistry.writer \
  --condition=None >/dev/null

if ! gcloud firestore databases describe \
  --database "${FIRESTORE_DATABASE}" >/dev/null 2>&1; then
  gcloud firestore databases create \
    --database "${FIRESTORE_DATABASE}" \
    --location "${REGION}" \
    --type firestore-native \
    --delete-protection
fi

for bucket in "${QUARANTINE_BUCKET}" "${BLOB_BUCKET}" "${METADATA_BUCKET}"; do
  if ! gcloud storage buckets describe "gs://${bucket}" >/dev/null 2>&1; then
    gcloud storage buckets create "gs://${bucket}" \
      --location "${REGION}" \
      --uniform-bucket-level-access \
      --public-access-prevention \
      --soft-delete-duration=7d
  fi
  gcloud storage buckets update "gs://${bucket}" \
    --uniform-bucket-level-access \
    --public-access-prevention >/dev/null
  gcloud storage buckets add-iam-policy-binding "gs://${bucket}" \
    --member="serviceAccount:${RUNTIME_EMAIL}" \
    --role=roles/storage.objectUser \
    --condition=None >/dev/null
done
gcloud storage buckets update "gs://${METADATA_BUCKET}" --versioning >/dev/null

# Review workers receive bucket roles individually. In particular, the
# scanner has read-only quarantine access and no public or metadata role.
for review_reader in "${SOURCE_EMAIL}" "${SCANNER_EMAIL}"; do
  gcloud storage buckets add-iam-policy-binding "gs://${QUARANTINE_BUCKET}" \
    --member="serviceAccount:${review_reader}" \
    --role=roles/storage.objectViewer \
    --condition=None >/dev/null
done
gcloud storage buckets add-iam-policy-binding "gs://${QUARANTINE_BUCKET}" \
  --member="serviceAccount:${PROMOTER_EMAIL}" \
  --role=roles/storage.objectUser \
  --condition=None >/dev/null
gcloud storage buckets add-iam-policy-binding "gs://${BLOB_BUCKET}" \
  --member="serviceAccount:${PROMOTER_EMAIL}" \
  --role=roles/storage.objectCreator \
  --condition=None >/dev/null
gcloud storage buckets add-iam-policy-binding "gs://${BLOB_BUCKET}" \
  --member="serviceAccount:${PROMOTER_EMAIL}" \
  --role=roles/storage.objectViewer \
  --condition=None >/dev/null
for metadata_writer in "${PROMOTER_EMAIL}" "${SECURITY_EMAIL}"; do
  gcloud storage buckets add-iam-policy-binding "gs://${METADATA_BUCKET}" \
    --member="serviceAccount:${metadata_writer}" \
    --role=roles/storage.objectUser \
    --condition=None >/dev/null
done

# Forge credentials are deliberately separate from writer/bootstrap secrets.
# Creating the containers is safe and idempotent; an owner adds the GitHub App
# ID and downloaded PEM private key out of band. The source worker uses them to
# mint a repository-scoped installation token for each execution; it never
# persists a short-lived installation token. GitLab remains optional.
for forge_secret in \
  "${GITHUB_APP_ID_SECRET}" \
  "${GITHUB_APP_PRIVATE_KEY_SECRET}" \
  "${GITLAB_FORGE_SECRET}"; do
  if ! gcloud secrets describe "${forge_secret}" >/dev/null 2>&1; then
    gcloud secrets create "${forge_secret}" --replication-policy=automatic
  fi
  gcloud secrets add-iam-policy-binding "${forge_secret}" \
    --member="serviceAccount:${SOURCE_EMAIL}" \
    --role=roles/secretmanager.secretAccessor \
    --condition=None >/dev/null
  gcloud secrets add-iam-policy-binding "${forge_secret}" \
    --member="serviceAccount:${DEPLOY_EMAIL}" \
    --role=roles/secretmanager.viewer \
    --condition=None >/dev/null
done

if ! gcloud kms keyrings describe "${KMS_KEY_RING}" \
  --location "${REGION}" >/dev/null 2>&1; then
  gcloud kms keyrings create "${KMS_KEY_RING}" --location "${REGION}"
fi
gcloud kms keyrings add-iam-policy-binding "${KMS_KEY_RING}" \
  --location "${REGION}" \
  --member="serviceAccount:${DEPLOY_EMAIL}" \
  --role=roles/cloudkms.viewer \
  --condition=None >/dev/null

for role in releases security snapshot timestamp; do
  key="seen-registry-dev-${role}"
  if ! gcloud kms keys describe "${key}" \
    --keyring "${KMS_KEY_RING}" \
    --location "${REGION}" >/dev/null 2>&1; then
    gcloud kms keys create "${key}" \
      --keyring "${KMS_KEY_RING}" \
      --location "${REGION}" \
      --purpose asymmetric-signing \
      --default-algorithm ec-sign-ed25519 \
      --protection-level software
  fi
  gcloud kms keys add-iam-policy-binding "${key}" \
    --keyring "${KMS_KEY_RING}" \
    --location "${REGION}" \
    --member="serviceAccount:${RUNTIME_EMAIL}" \
    --role=roles/cloudkms.signerVerifier \
    --condition=None >/dev/null
  gcloud kms keys add-iam-policy-binding "${key}" \
    --keyring "${KMS_KEY_RING}" \
    --location "${REGION}" \
    --member="serviceAccount:${DEPLOY_EMAIL}" \
    --role=roles/cloudkms.publicKeyViewer \
    --condition=None >/dev/null
  case "${role}" in
    releases)
      signing_accounts=("${PROMOTER_EMAIL}")
      ;;
    security)
      signing_accounts=("${SECURITY_EMAIL}")
      ;;
    snapshot|timestamp)
      signing_accounts=("${PROMOTER_EMAIL}" "${SECURITY_EMAIL}")
      ;;
  esac
  for signing_account in "${signing_accounts[@]}"; do
    gcloud kms keys add-iam-policy-binding "${key}" \
      --keyring "${KMS_KEY_RING}" \
      --location "${REGION}" \
      --member="serviceAccount:${signing_account}" \
      --role=roles/cloudkms.signerVerifier \
      --condition=None >/dev/null
  done
  version_id="$(gcloud kms keys versions list \
    --key "${key}" \
    --keyring "${KMS_KEY_RING}" \
    --location "${REGION}" \
    --filter='state=ENABLED' \
    --sort-by='~createTime' \
    --limit=1 \
    --format='value(name)')"
  version_id="${version_id##*/}"
  if ! [[ "${version_id}" =~ ^[1-9][0-9]*$ ]]; then
    echo "no enabled signing key version for ${role}" >&2
    exit 70
  fi
done

DATABASE_RESOURCE="projects/${PROJECT_ID}/databases/${FIRESTORE_DATABASE}"
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${RUNTIME_EMAIL}" \
  --role=roles/datastore.user \
  --condition="^:^title=seen_registry_dev_database:expression=resource.name == '${DATABASE_RESOURCE}' || resource.name.startsWith('${DATABASE_RESOURCE}/')" \
  >/dev/null
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${DEPLOY_EMAIL}" \
  --role=roles/datastore.viewer \
  --condition="^:^title=seen_registry_dev_database_view:expression=resource.name == '${DATABASE_RESOURCE}' || resource.name.startsWith('${DATABASE_RESOURCE}/')" \
  >/dev/null
for review_identity in \
  "${SOURCE_EMAIL}" \
  "${SCANNER_EMAIL}" \
  "${PROMOTER_EMAIL}" \
  "${SECURITY_EMAIL}"; do
  condition_title="seen_registry_dev_database_${review_identity%%@*}"
  condition_title="${condition_title//-/_}"
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${review_identity}" \
    --role=roles/datastore.user \
    --condition="^:^title=${condition_title}:expression=resource.name == '${DATABASE_RESOURCE}' || resource.name.startsWith('${DATABASE_RESOURCE}/')" \
    >/dev/null
done

for runtime_email in \
  "${RUNTIME_EMAIL}" \
  "${SOURCE_EMAIL}" \
  "${SCANNER_EMAIL}" \
  "${PROMOTER_EMAIL}" \
  "${SECURITY_EMAIL}"; do
  gcloud iam service-accounts describe "${runtime_email}" --format='value(email)' >/dev/null
done
gcloud artifacts repositories describe "${ARTIFACT_REPOSITORY}" --location "${REGION}" >/dev/null
gcloud firestore databases describe --database "${FIRESTORE_DATABASE}" >/dev/null
for bucket in "${QUARANTINE_BUCKET}" "${BLOB_BUCKET}" "${METADATA_BUCKET}"; do
  gcloud storage buckets describe "gs://${bucket}" >/dev/null
done
for role in releases security snapshot timestamp; do
  gcloud kms keys describe "seen-registry-dev-${role}" \
    --keyring "${KMS_KEY_RING}" \
    --location "${REGION}" >/dev/null
done

# Schedules are an explicit owner decision. The normal bootstrap creates only
# inert identities and resources; rerun with REGISTRY_ENABLE_REVIEW_SCHEDULES=true
# after the deployment workflow has created all three Cloud Run jobs.
if [ "${ENABLE_REVIEW_SCHEDULES}" = true ]; then
  gcloud services enable cloudscheduler.googleapis.com >/dev/null

  SCHEDULER_ACCOUNT=seen-registry-dev-scheduler
  SCHEDULER_EMAIL="${SCHEDULER_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"
  ensure_service_account "${SCHEDULER_ACCOUNT}" 'Seen registry development review scheduler'

  PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
  SCHEDULER_SERVICE_AGENT="service-${PROJECT_NUMBER}@gcp-sa-cloudscheduler.iam.gserviceaccount.com"
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${SCHEDULER_SERVICE_AGENT}" \
    --role=roles/cloudscheduler.serviceAgent \
    --condition=None >/dev/null

  for review_job in "${SOURCE_JOB}" "${SCANNER_JOB}" "${PROMOTER_JOB}"; do
    if ! gcloud run jobs describe "${review_job}" --region "${REGION}" >/dev/null 2>&1; then
      echo "Cloud Run job ${review_job} is missing; deploy the registry first, then rerun this owner command." >&2
      exit 70
    fi
    gcloud run jobs add-iam-policy-binding "${review_job}" \
      --region "${REGION}" \
      --member="serviceAccount:${SCHEDULER_EMAIL}" \
      --role=roles/run.invoker >/dev/null
  done

  upsert_schedule() {
    local schedule_name="$1"
    local review_job="$2"
    local schedule="$3"
    local description="$4"
    local uri="https://run.googleapis.com/v2/projects/${PROJECT_ID}/locations/${REGION}/jobs/${review_job}:run"
    local action=create
    local header_flag=--headers=Content-Type=application/json
    if gcloud scheduler jobs describe "${schedule_name}" --location "${REGION}" >/dev/null 2>&1; then
      action=update
      header_flag=--update-headers=Content-Type=application/json
    fi
    gcloud scheduler jobs "${action}" http "${schedule_name}" \
      --location "${REGION}" \
      --description "${description}" \
      --schedule "${schedule}" \
      --time-zone UTC \
      --uri "${uri}" \
      --http-method POST \
      "${header_flag}" \
      --message-body '{}' \
      --oauth-service-account-email "${SCHEDULER_EMAIL}" \
      --oauth-token-scope https://www.googleapis.com/auth/cloud-platform \
      --attempt-deadline 30s \
      --max-retry-attempts 0 \
      --quiet
  }

  upsert_schedule \
    seen-registry-dev-source-verify-schedule \
    "${SOURCE_JOB}" \
    '*/5 * * * *' \
    'Verify one quarantined Seen package source every five minutes'
  upsert_schedule \
    seen-registry-dev-scan-schedule \
    "${SCANNER_JOB}" \
    '2-59/5 * * * *' \
    'Scan one source-verified Seen package every five minutes'
  upsert_schedule \
    seen-registry-dev-promote-schedule \
    "${PROMOTER_JOB}" \
    '0 * * * *' \
    'Promote one eligible Seen package each hour'
fi

echo 'Owner bootstrap completed for the isolated Seen development registry.'
