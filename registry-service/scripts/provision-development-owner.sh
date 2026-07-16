#!/usr/bin/env bash
set -euo pipefail
umask 077

PROJECT_ID="${GCP_PROJECT_ID:-portfolio-476219}"
REGION="${GCP_REGION:-us-central1}"
DEPLOY_EMAIL="${REGISTRY_DEPLOY_EMAIL:-gh-deploy@${PROJECT_ID}.iam.gserviceaccount.com}"
RUNTIME_ACCOUNT=seen-registry-dev
RUNTIME_EMAIL="${RUNTIME_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"
ARTIFACT_REPOSITORY=seen-registry
FIRESTORE_DATABASE=seen-registry-dev
KMS_KEY_RING=seen-registry-dev
QUARANTINE_BUCKET="${PROJECT_ID}-seen-registry-dev-quarantine"
BLOB_BUCKET="${PROJECT_ID}-seen-registry-dev-blobs"
METADATA_BUCKET="${PROJECT_ID}-seen-registry-dev-metadata"

export CLOUDSDK_CORE_PROJECT="${PROJECT_ID}"
export CLOUDSDK_PROJECT="${PROJECT_ID}"

for command in gcloud; do
  command -v "${command}" >/dev/null || {
    echo "missing required command: ${command}" >&2
    exit 69
  }
done

ACTIVE_ACCOUNT="$(gcloud auth list --filter=status:ACTIVE --format='value(account)')"
if [ -z "${ACTIVE_ACCOUNT}" ]; then
  echo "an active project-owner gcloud account is required" >&2
  exit 77
fi
gcloud projects describe "${PROJECT_ID}" --format='value(projectId)' \
  | grep -Fx "${PROJECT_ID}" >/dev/null

if ! gcloud iam service-accounts describe "${RUNTIME_EMAIL}" >/dev/null 2>&1; then
  gcloud iam service-accounts create "${RUNTIME_ACCOUNT}" \
    --display-name='Seen registry development runtime'
fi
gcloud iam service-accounts add-iam-policy-binding "${RUNTIME_EMAIL}" \
  --member="serviceAccount:${DEPLOY_EMAIL}" \
  --role=roles/iam.serviceAccountUser \
  --condition=None >/dev/null

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

if ! gcloud kms keyrings describe "${KMS_KEY_RING}" \
  --location "${REGION}" >/dev/null 2>&1; then
  gcloud kms keyrings create "${KMS_KEY_RING}" --location "${REGION}"
fi

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
  version_id="$(gcloud kms keys versions list \
    --key "${key}" \
    --keyring "${KMS_KEY_RING}" \
    --location "${REGION}" \
    --filter='state=ENABLED' \
    --sort-by='~name' \
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

gcloud iam service-accounts describe "${RUNTIME_EMAIL}" --format='value(email)' >/dev/null
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

echo 'Owner bootstrap completed for the isolated Seen development registry.'
