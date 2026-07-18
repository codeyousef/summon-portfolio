resource "google_service_account" "runtime" {
  for_each = var.enabled ? local.service_account_ids : {}

  project      = var.project_id
  account_id   = each.value
  display_name = local.service_account_descriptions[each.key]
  description  = local.service_account_descriptions[each.key]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "metadata_creator" {
  count = var.enabled ? 1 : 0

  project     = var.project_id
  role_id     = "seen_registry_${var.environment}_metadata_creator"
  title       = "Seen registry ${var.environment} immutable metadata creator"
  description = "Creates new immutable metadata objects; cannot replace or delete an existing object."
  permissions = ["storage.objects.create"]
}

resource "google_project_iam_custom_role" "pointer_replacer" {
  count = var.enabled ? 1 : 0

  project     = var.project_id
  role_id     = "seen_registry_${var.environment}_pointer_replacer"
  title       = "Seen registry ${var.environment} exact pointer replacer"
  description = "Reads and conditionally replaces one explicitly conditioned mutable object."
  permissions = [
    "storage.objects.create",
    "storage.objects.delete",
    "storage.objects.get",
  ]
}

resource "google_project_iam_custom_role" "quarantine_writer" {
  count = var.enabled ? 1 : 0

  project     = var.project_id
  role_id     = "seen_registry_${var.environment}_quarantine_writer"
  title       = "Seen registry ${var.environment} quarantine writer"
  description = "Creates and verifies quarantined uploads without delete or overwrite authority."
  permissions = [
    "storage.objects.create",
    "storage.objects.get",
  ]
}

resource "google_project_iam_custom_role" "quarantine_promoter" {
  count = var.enabled ? 1 : 0

  project     = var.project_id
  role_id     = "seen_registry_${var.environment}_quarantine_promoter"
  title       = "Seen registry ${var.environment} quarantine promoter"
  description = "Reads and removes an exact quarantined object after promotion."
  permissions = [
    "storage.objects.delete",
    "storage.objects.get",
  ]
}

resource "google_project_iam_custom_role" "public_blob_creator" {
  count = var.enabled ? 1 : 0

  project     = var.project_id
  role_id     = "seen_registry_${var.environment}_blob_creator"
  title       = "Seen registry ${var.environment} immutable public blob creator"
  description = "Creates and verifies immutable content-addressed blobs without replacement authority."
  permissions = [
    "storage.objects.create",
    "storage.objects.get",
  ]
}

resource "google_project_iam_custom_role" "kms_exact_signer" {
  count = var.enabled ? 1 : 0

  project     = var.project_id
  role_id     = "seen_registry_${var.environment}_kms_signer"
  title       = "Seen registry ${var.environment} exact KMS signer"
  description = "Signs with an explicitly bound asymmetric key; cannot retrieve public keys or inspect key metadata."
  permissions = ["cloudkms.cryptoKeyVersions.useToSign"]
}
