locals {
  roles = toset(["releases", "security", "snapshot", "timestamp"])

  common_labels = merge({
    application = "seen-registry"
    environment = var.environment
    managed_by  = "opentofu"
  }, var.labels)

  required_services = toset([
    "artifactregistry.googleapis.com",
    "billingbudgets.googleapis.com",
    "cloudkms.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "cloudscheduler.googleapis.com",
    "certificatemanager.googleapis.com",
    "compute.googleapis.com",
    "dns.googleapis.com",
    "firestore.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "logging.googleapis.com",
    "monitoring.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "storage.googleapis.com",
  ])

  service_account_descriptions = {
    api                        = "Seen registry ${var.environment} verification-only API"
    source                     = "Seen registry ${var.environment} source verifier"
    scanner                    = "Seen registry ${var.environment} isolated scanner"
    promoter                   = "Seen registry ${var.environment} promotion coordinator"
    release_actions            = "Seen registry ${var.environment} release action coordinator"
    security_actions           = "Seen registry ${var.environment} security action coordinator"
    release_refresh            = "Seen registry ${var.environment} releases refresh coordinator"
    security_refresh           = "Seen registry ${var.environment} security refresh coordinator"
    signer_releases            = "Seen registry ${var.environment} releases signer"
    signer_security            = "Seen registry ${var.environment} security signer"
    signer_snapshot            = "Seen registry ${var.environment} snapshot signer"
    signer_timestamp           = "Seen registry ${var.environment} timestamp signer and commit authority"
    scheduler                  = "Seen registry ${var.environment} scheduler caller"
    root_verifier              = "Seen registry ${var.environment} read-only root verifier"
    offline_bootstrap_importer = "Seen registry ${var.environment} ephemeral offline bootstrap importer"
    online_bootstrap           = "Seen registry ${var.environment} ephemeral online bootstrap coordinator"
    targets_renewal            = "Seen registry ${var.environment} ephemeral targets renewal importer"
    targets_releases_rotation  = "Seen registry ${var.environment} ephemeral releases targets rotation importer"
    targets_security_rotation  = "Seen registry ${var.environment} ephemeral security targets rotation importer"
    root_importer              = "Seen registry ${var.environment} ephemeral root importer"
  }

  service_account_ids = {
    api                        = var.service_account_ids.api
    source                     = var.service_account_ids.source
    scanner                    = var.service_account_ids.scanner
    promoter                   = var.service_account_ids.promoter
    release_actions            = var.service_account_ids.release_actions
    security_actions           = var.service_account_ids.security_actions
    release_refresh            = var.service_account_ids.release_refresh
    security_refresh           = var.service_account_ids.security_refresh
    signer_releases            = var.service_account_ids.signer_releases
    signer_security            = var.service_account_ids.signer_security
    signer_snapshot            = var.service_account_ids.signer_snapshot
    signer_timestamp           = var.service_account_ids.signer_timestamp
    scheduler                  = var.service_account_ids.scheduler
    root_verifier              = var.service_account_ids.root_verifier
    offline_bootstrap_importer = var.service_account_ids.offline_bootstrap_importer
    online_bootstrap           = var.service_account_ids.online_bootstrap
    targets_renewal            = var.service_account_ids.targets_renewal
    targets_releases_rotation  = var.service_account_ids.targets_releases_rotation
    targets_security_rotation  = var.service_account_ids.targets_security_rotation
    root_importer              = var.service_account_ids.root_importer
  }

  secret_names = {
    publisher_token                    = var.secret_names.publisher_token
    trust_and_safety_token             = var.secret_names.trust_and_safety_token
    security_token                     = var.secret_names.security_token
    github_app_id                      = var.secret_names.github_app_id
    github_app_private_key             = var.secret_names.github_app_private_key
    gitlab_forge_token                 = var.secret_names.gitlab_forge_token
    bootstrap_root_envelope            = var.secret_names.bootstrap_root_envelope
    bootstrap_targets_envelope         = var.secret_names.bootstrap_targets_envelope
    targets_renewal_envelope           = var.secret_names.targets_renewal_envelope
    targets_releases_rotation_envelope = var.secret_names.targets_releases_rotation_envelope
    targets_security_rotation_envelope = var.secret_names.targets_security_rotation_envelope
    root_rotation_envelope             = var.secret_names.root_rotation_envelope
  }

  metadata_object_prefix   = "projects/_/buckets/${var.bucket_names.metadata}/objects/${var.object_prefix}/metadata/"
  quarantine_object_prefix = "projects/_/buckets/${var.bucket_names.quarantine}/objects/${var.object_prefix}/quarantine/"
  public_object_prefix     = "projects/_/buckets/${var.bucket_names.public}/objects/${var.object_prefix}/blobs/sha256/"
  timestamp_object_name    = "${local.metadata_object_prefix}timestamp.json"
  root_pointer_object_name = "${local.metadata_object_prefix}root.json"
  registry_web_origin      = regex("^https://[^/]+", var.registry_origin)

  online_key_versions = {
    for role, key in google_kms_crypto_key.online : role => "${key.id}/cryptoKeyVersions/${var.online_key_version_numbers[role]}"
    if var.enabled && contains(keys(var.online_key_version_numbers), role)
  }

  identity_environment = {
    REGISTRY_ENVIRONMENT   = var.runtime_environment
    REGISTRY_REPOSITORY_ID = var.repository_id
    REGISTRY_ORIGIN        = var.registry_origin
    REGISTRY_OBJECT_PREFIX = var.object_prefix
  }

  firestore_environment = {
    GOOGLE_CLOUD_PROJECT        = var.project_id
    REGISTRY_FIRESTORE_DATABASE = var.firestore_database
  }

  quarantine_environment = {
    REGISTRY_QUARANTINE_BUCKET = var.bucket_names.quarantine
  }

  public_environment = {
    REGISTRY_PUBLIC_BUCKET = var.bucket_names.public
  }

  metadata_environment = {
    REGISTRY_METADATA_BUCKET = var.bucket_names.metadata
  }

  api_environment = merge(
    local.identity_environment,
    local.firestore_environment,
    local.quarantine_environment,
    local.public_environment,
    local.metadata_environment,
    {
      REGISTRY_STORAGE_MODE         = "gcp"
      REGISTRY_PUBLIC_DELAY_SECONDS = tostring(var.public_delay_seconds)
      REGISTRY_WRITER_MODE          = "opaque-dev"
      REGISTRY_WRITER_PRINCIPAL     = "internal-${var.environment}-publisher"
      REGISTRY_OWNER_ALLOWLIST      = "seen"
      REGISTRY_WRITERS_ENABLED      = var.environment == "dev" ? "true" : "false"
      REGISTRY_PROMOTION_MODE       = "disabled"
    },
  )

  coordinator_environment = merge(
    local.identity_environment,
    local.firestore_environment,
    local.metadata_environment,
    { REGISTRY_STORAGE_MODE = "gcp" },
  )

  metadata_maintenance_environment = merge(
    local.identity_environment,
    local.metadata_environment,
    {
      GOOGLE_CLOUD_PROJECT  = var.project_id
      REGISTRY_STORAGE_MODE = "gcp"
    },
  )

  review_environment = merge(
    local.identity_environment,
    local.firestore_environment,
    local.quarantine_environment,
    { REGISTRY_PUBLIC_DELAY_SECONDS = tostring(var.public_delay_seconds) },
  )

  promotion_environment = merge(
    local.review_environment,
    local.public_environment,
    local.metadata_environment,
  )

  public_key_environment = {
    for role, value in var.online_public_keys_hex : "REGISTRY_KMS_${upper(role)}_PUBLIC_KEY_HEX" => value
  }
}

check "environment_pair" {
  assert {
    condition = (
      var.environment == "dev" && var.runtime_environment == "development"
      ) || (
      var.environment == "prod" && var.runtime_environment == "production"
    )
    error_message = "The short and signed environment names do not match."
  }
}

check "workload_inputs" {
  assert {
    condition = !var.enabled || !var.workloads_enabled || (
      var.container_image != null &&
      var.signer_container_image != null &&
      toset(keys(var.online_key_version_numbers)) == local.roles &&
      toset(keys(var.online_public_keys_hex)) == local.roles &&
      var.trusted_root_v1_sha256 != null
    )
    error_message = "Enabled workloads require separate immutable application and signer images plus concrete versions and public keys for all online roles."
  }
}

check "production_is_inert" {
  assert {
    condition = var.environment != "prod" || !var.enabled || (
      !var.workloads_enabled && !var.schedules_enabled && length(var.ceremony_operations) == 0
    )
    error_message = "Production workloads, schedules, and ceremonies remain disabled until the production launch gate is intentionally changed."
  }
}
