locals {
  bootstrap_owned_services = toset([
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "monitoring.googleapis.com",
    "orgpolicy.googleapis.com",
    "serviceusage.googleapis.com",
    "storage.googleapis.com",
    "sts.googleapis.com",
  ])

  infrastructure_executor_act_as_identities = toset([
    "api",
    "root_verifier",
    "release_refresh",
    "security_refresh",
    "signer_releases",
    "signer_security",
    "signer_snapshot",
    "signer_timestamp",
    "offline_bootstrap_importer",
    "online_bootstrap",
    "targets_renewal",
    "targets_releases_rotation",
    "targets_security_rotation",
    "root_importer",
  ])
}

data "google_monitoring_notification_channel" "operations" {
  count = var.enable_production_foundation ? 1 : 0

  project      = var.project_id
  display_name = "Seen Registry Production Operations"
  type         = "email"
  user_labels = {
    application = "seen-registry"
    environment = "prod"
    managed_by  = "opentofu"
  }
}

resource "terraform_data" "production_launch_gate" {
  count = var.enable_production_foundation ? 1 : 0

  input = {
    notification_channel_id     = try(data.google_monitoring_notification_channel.operations[0].name, null)
    notification_channel_status = try(data.google_monitoring_notification_channel.operations[0].verification_status, null)
  }

  lifecycle {
    precondition {
      condition     = try(data.google_monitoring_notification_channel.operations[0].name, null) == try(one(var.notification_channel_ids), null)
      error_message = "The configured production notification channel must be the exact live bootstrap-managed channel."
    }

    precondition {
      condition     = try(data.google_monitoring_notification_channel.operations[0].enabled, false)
      error_message = "The production notification channel must be enabled."
    }

    precondition {
      condition     = try(data.google_monitoring_notification_channel.operations[0].verification_status, null) == "VERIFIED"
      error_message = "The production email notification channel must be VERIFIED before foundation resources are planned."
    }
  }
}

module "registry" {
  source = "../../modules/environment"

  depends_on = [terraform_data.production_launch_gate]

  enabled             = var.enable_production_foundation
  project_id          = var.project_id
  region              = var.region
  environment         = "prod"
  runtime_environment = "production"
  name_prefix         = "seen-registry-prod"
  repository_id       = "seen-prod-registry-v1"
  registry_origin     = "https://seen.yousef.codes/packages"
  uptime_host         = "seen.yousef.codes"

  firestore_database  = "seen-registry-prod"
  artifact_repository = "seen-registry"
  bucket_names = {
    quarantine = "${var.project_id}-seen-registry-prod-quarantine"
    public     = "${var.project_id}-seen-registry-prod-blobs"
    metadata   = "${var.project_id}-seen-registry-prod-metadata"
    private    = "${var.project_id}-seen-registry-prod-private"
    evidence   = "${var.project_id}-seen-registry-prod-evidence"
    backup     = "${var.project_id}-seen-registry-prod-backup"
  }

  kms_key_ring = "seen-registry-prod"
  online_key_names = {
    releases  = "seen-registry-prod-releases"
    security  = "seen-registry-prod-security"
    snapshot  = "seen-registry-prod-snapshot"
    timestamp = "seen-registry-prod-timestamp"
  }
  online_key_version_numbers = var.online_key_version_numbers
  online_public_keys_hex     = var.online_public_keys_hex
  trusted_root_v1_sha256     = var.trusted_root_v1_sha256

  service_account_ids = {
    api                        = "seen-registry-prod"
    source                     = "seen-registry-prod-source"
    scanner                    = "seen-registry-prod-scanner"
    promoter                   = "seen-registry-prod-promoter"
    release_actions            = "seen-reg-prod-release-actions"
    security_actions           = "seen-registry-prod-security"
    release_refresh            = "seen-reg-prod-release-refresh"
    security_refresh           = "seen-reg-prod-security-refresh"
    signer_releases            = "seen-reg-prod-sign-releases"
    signer_security            = "seen-reg-prod-sign-security"
    signer_snapshot            = "seen-reg-prod-sign-snapshot"
    signer_timestamp           = "seen-reg-prod-sign-timestamp"
    scheduler                  = "seen-registry-prod-scheduler"
    root_verifier              = "seen-reg-prod-root-verifier"
    offline_bootstrap_importer = "seen-reg-prod-offline-boot"
    online_bootstrap           = "seen-reg-prod-online-bootstrap"
    targets_renewal            = "seen-reg-prod-targets-renew"
    targets_releases_rotation  = "seen-reg-prod-release-rotate"
    targets_security_rotation  = "seen-reg-prod-security-rotate"
    root_importer              = "seen-reg-prod-root-importer"
  }

  secret_names = {
    publisher_token                    = "seen-registry-prod-publisher-token"
    trust_and_safety_token             = "seen-registry-prod-trust-and-safety-token"
    security_token                     = "seen-registry-prod-security-token"
    github_app_id                      = "seen-registry-prod-github-app-id"
    github_app_private_key             = "seen-registry-prod-github-app-private-key"
    gitlab_forge_token                 = "seen-registry-prod-gitlab-forge-token"
    bootstrap_root_envelope            = "seen-registry-prod-root-envelope"
    bootstrap_targets_envelope         = "seen-registry-prod-targets-envelope"
    targets_renewal_envelope           = "seen-registry-prod-targets-renewal-envelope"
    targets_releases_rotation_envelope = "seen-registry-prod-targets-releases-rotation-envelope"
    targets_security_rotation_envelope = "seen-registry-prod-targets-security-rotation-envelope"
    root_rotation_envelope             = "seen-registry-prod-root-rotation-envelope"
  }

  secret_versions                           = var.secret_versions
  portfolio_gateway_service_account         = var.portfolio_gateway_service_account
  reviewer_service_account                  = var.reviewer_service_account
  github_ci_enabled                         = var.github_ci_enabled
  github_repository                         = "codeyousef/summon-portfolio"
  github_repository_id                      = "1091564909"
  github_repository_owner_id                = "10247142"
  github_ref                                = "refs/heads/master"
  github_workflow_ref                       = "codeyousef/summon-portfolio/.github/workflows/deploy-seen-registry.yml@refs/heads/master"
  github_event_name                         = "push"
  github_environment                        = "seen-registry-production-image"
  notification_channel_ids                  = var.notification_channel_ids
  billing_account_id                        = var.billing_account_id
  monthly_budget_usd                        = var.monthly_budget_usd
  container_image                           = var.container_image
  signer_container_image                    = var.signer_container_image
  network_cidr                              = "10.43.0.0/24"
  signer_jwks_all_apis_enabled              = var.signer_jwks_all_apis_enabled
  externally_managed_services               = local.bootstrap_owned_services
  infrastructure_executor_service_account   = var.iac_executor_service_account
  infrastructure_executor_act_as_identities = local.infrastructure_executor_act_as_identities

  # Production launch surfaces are separately gated and remain inert by
  # default. The writer stack, schedules, unpause, and edge are not exposed by
  # this root.
  workloads_enabled         = false
  read_only_api_enabled     = var.enable_production_read_only_api
  root_verifier_job_enabled = var.enable_production_root_verifier
  refresh_jobs_enabled      = var.enable_production_refresh_jobs
  ceremony_operations       = var.production_ceremony_operations
  schedules_enabled         = false
  schedules_paused          = true
  edge_provisioned          = false
  edge_cutover_enabled      = false
}
