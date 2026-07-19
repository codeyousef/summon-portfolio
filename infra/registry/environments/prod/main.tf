module "registry" {
  source = "../../modules/environment"

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

  secret_versions                   = var.secret_versions
  portfolio_gateway_service_account = var.portfolio_gateway_service_account
  reviewer_service_account          = var.reviewer_service_account
  github_ci_enabled                 = var.github_ci_enabled
  github_repository                 = "codeyousef/summon-portfolio"
  github_repository_id              = "1091564909"
  github_repository_owner_id        = "10247142"
  github_ref                        = "refs/heads/master"
  github_workflow_ref               = "codeyousef/summon-portfolio/.github/workflows/deploy-seen-registry.yml@refs/heads/master"
  github_event_name                 = "push"
  github_environment                = "seen-registry-production-image"
  notification_channel_ids          = var.notification_channel_ids
  billing_account_id                = var.billing_account_id
  monthly_budget_usd                = var.monthly_budget_usd
  container_image                   = var.container_image
  signer_container_image            = var.signer_container_image
  network_cidr                      = "10.43.0.0/24"
  signer_jwks_all_apis_enabled      = var.signer_jwks_all_apis_enabled

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

check "dedicated_production_project" {
  assert {
    condition     = var.project_id != "portfolio-476219"
    error_message = "Production must use a project isolated from the current dev/portfolio project."
  }
}

check "production_ceremony_isolation" {
  assert {
    condition = (
      length(var.production_ceremony_operations) == 0 ||
      !var.enable_production_refresh_jobs
    )
    error_message = "A production ceremony must not be combined with refresh jobs or their independent signing authority."
  }
}

check "production_launch_requires_foundation" {
  assert {
    condition = var.enable_production_foundation || !(
      var.enable_production_read_only_api ||
      var.enable_production_root_verifier ||
      var.enable_production_refresh_jobs ||
      length(var.production_ceremony_operations) > 0 ||
      var.signer_jwks_all_apis_enabled ||
      var.github_ci_enabled
    )
    error_message = "Every production launch selector requires enable_production_foundation."
  }
}
