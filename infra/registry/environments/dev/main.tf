module "registry" {
  source = "../../modules/environment"

  enabled             = true
  project_id          = var.project_id
  region              = var.region
  environment         = "dev"
  runtime_environment = "development"
  name_prefix         = "seen-registry-dev"
  repository_id       = "seen-dev-registry-v1"
  registry_origin     = "https://seen.dev.yousef.codes/packages"
  uptime_host         = "seen.dev.yousef.codes"

  firestore_database  = "seen-registry-dev"
  artifact_repository = "seen-registry"
  bucket_names = {
    quarantine = "${var.project_id}-seen-registry-dev-quarantine"
    public     = "${var.project_id}-seen-registry-dev-blobs"
    metadata   = "${var.project_id}-seen-registry-dev-metadata"
    private    = "${var.project_id}-seen-registry-dev-private"
    evidence   = "${var.project_id}-seen-registry-dev-evidence"
    backup     = "${var.project_id}-seen-registry-dev-backup"
  }

  kms_key_ring = "seen-registry-dev"
  online_key_names = {
    releases  = "seen-registry-dev-releases"
    security  = "seen-registry-dev-security"
    snapshot  = "seen-registry-dev-snapshot"
    timestamp = "seen-registry-dev-timestamp"
  }
  online_key_version_numbers = var.online_key_version_numbers
  online_public_keys_hex     = var.online_public_keys_hex
  trusted_root_v1_sha256     = var.trusted_root_v1_sha256

  service_account_ids = {
    api                        = "seen-reg-dev-api-v2"
    source                     = "seen-reg-dev-source-v2"
    scanner                    = "seen-reg-dev-scanner-v2"
    promoter                   = "seen-reg-dev-promoter-v2"
    release_actions            = "seen-reg-dev-release-actions"
    security_actions           = "seen-reg-dev-security-actions"
    release_refresh            = "seen-reg-dev-release-refresh"
    security_refresh           = "seen-reg-dev-security-refresh"
    signer_releases            = "seen-reg-dev-sign-releases"
    signer_security            = "seen-reg-dev-sign-security"
    signer_snapshot            = "seen-reg-dev-sign-snapshot"
    signer_timestamp           = "seen-reg-dev-sign-timestamp"
    scheduler                  = "seen-reg-dev-scheduler-v2"
    root_verifier              = "seen-reg-dev-root-verifier"
    offline_bootstrap_importer = "seen-reg-dev-offline-bootstrap"
    online_bootstrap           = "seen-reg-dev-online-bootstrap"
    targets_renewal            = "seen-reg-dev-targets-renew"
    targets_releases_rotation  = "seen-reg-dev-release-rotate"
    targets_security_rotation  = "seen-reg-dev-security-rotate"
    root_importer              = "seen-reg-dev-root-importer"
  }

  secret_names = {
    publisher_token                    = "seen-registry-dev-publisher-token"
    trust_and_safety_token             = "seen-registry-dev-trust-and-safety-token"
    security_token                     = "seen-registry-dev-security-token"
    github_app_id                      = "seen-registry-dev-github-app-id"
    github_app_private_key             = "seen-registry-dev-github-app-private-key"
    gitlab_forge_token                 = "seen-registry-dev-gitlab-forge-token"
    bootstrap_root_envelope            = "seen-registry-dev-root-envelope"
    bootstrap_targets_envelope         = "seen-registry-dev-targets-envelope"
    targets_renewal_envelope           = "seen-registry-dev-targets-renewal-envelope"
    targets_releases_rotation_envelope = "seen-registry-dev-targets-releases-rotation-envelope"
    targets_security_rotation_envelope = "seen-registry-dev-targets-security-rotation-envelope"
    root_rotation_envelope             = "seen-registry-dev-root-rotation-envelope"
  }
  secret_replica_locations = {
    publisher_token            = ["us-central1"]
    bootstrap_root_envelope    = ["us-central1"]
    bootstrap_targets_envelope = ["us-central1"]
  }

  secret_versions                   = var.secret_versions
  portfolio_gateway_service_account = var.portfolio_gateway_service_account
  reviewer_service_account          = var.reviewer_service_account
  github_ci_enabled                 = var.github_ci_enabled
  github_repository                 = "codeyousef/summon-portfolio"
  github_repository_id              = "1091564909"
  github_repository_owner_id        = "10247142"
  github_ref                        = "refs/heads/dev"
  github_workflow_ref               = "codeyousef/summon-portfolio/.github/workflows/deploy-seen-registry.yml@refs/heads/dev"
  github_event_name                 = "push"
  github_environment                = "seen-registry-development-image"
  notification_channel_ids          = var.notification_channel_ids
  billing_account_id                = var.billing_account_id
  monthly_budget_usd                = var.monthly_budget_usd
  container_image                   = var.container_image
  signer_container_image            = var.signer_container_image
  workloads_enabled                 = var.workloads_enabled
  refresh_jobs_enabled              = var.refresh_jobs_enabled
  ceremony_operations               = var.ceremony_operations
  schedules_enabled                 = var.schedules_enabled
  schedules_paused                  = var.schedules_paused
  network_cidr                      = "10.42.0.0/24"
  signer_jwks_all_apis_enabled      = true
  edge_provisioned                  = var.edge_provisioned
  edge_cutover_enabled              = var.edge_cutover_enabled
  portfolio_fallback_service        = "portfolio-dev"
  edge_certificate_domains          = ["seen.dev.yousef.codes"]
}
