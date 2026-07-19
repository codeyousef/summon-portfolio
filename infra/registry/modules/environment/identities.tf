locals {
  # Production foundation is applied before any runtime selector is enabled.
  # Pre-provisioning the executor on this finite set avoids giving the steady
  # identity service-account IAM mutation authority during later saved-plan
  # rollouts. This is deliberately one binding per named runtime account, not
  # a project-level serviceAccountUser grant.
  executor_required_act_as_identities = setunion(
    var.workloads_enabled ? toset([
      "api",
      "source",
      "scanner",
      "promoter",
      "release_actions",
      "security_actions",
      "root_verifier",
    ]) : toset([]),
    local.read_only_api_enabled ? toset(["api"]) : toset([]),
    local.root_verifier_job_enabled ? toset(["root_verifier"]) : toset([]),
    var.refresh_jobs_enabled ? toset(["release_refresh", "security_refresh"]) : toset([]),
    var.schedules_enabled && var.workloads_enabled ? toset(["scheduler"]) : toset([]),
    var.ceremony_operations,
    toset([for role in local.selected_signer_roles : "signer_${role}"]),
  )
}

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

resource "google_service_account_iam_member" "infrastructure_executor_act_as" {
  for_each = var.enabled && var.infrastructure_executor_service_account != null ? var.infrastructure_executor_act_as_identities : toset([])

  service_account_id = "projects/${var.project_id}/serviceAccounts/${local.service_account_ids[each.value]}@${var.project_id}.iam.gserviceaccount.com"
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${var.infrastructure_executor_service_account}"

  depends_on = [google_service_account.runtime]

  lifecycle {
    precondition {
      condition     = contains(keys(local.service_account_ids), each.value)
      error_message = "Infrastructure executor actAs may target only one of the finite named registry runtime identities."
    }

    precondition {
      condition     = length(setsubtract(local.executor_required_act_as_identities, var.infrastructure_executor_act_as_identities)) == 0
      error_message = "Infrastructure executor actAs must cover every identity required by the selected services, jobs, signers, ceremonies, and schedules."
    }
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
