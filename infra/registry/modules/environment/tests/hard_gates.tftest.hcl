mock_provider "google" {}

variables {
  enabled             = true
  project_id          = "seen-registry-prod-placeholder"
  region              = "us-central1"
  environment         = "prod"
  runtime_environment = "production"
  name_prefix         = "seen-registry-prod"
  repository_id       = "seen-prod-registry-v1"
  registry_origin     = "https://seen.yousef.codes/packages"
  uptime_host         = "seen.yousef.codes"

  firestore_database = "seen-registry-prod"
  bucket_names = {
    quarantine = "seen-registry-prod-placeholder-quarantine"
    public     = "seen-registry-prod-placeholder-public"
    metadata   = "seen-registry-prod-placeholder-metadata"
    private    = "seen-registry-prod-placeholder-private"
    evidence   = "seen-registry-prod-placeholder-evidence"
    backup     = "seen-registry-prod-placeholder-backup"
  }

  kms_key_ring = "seen-registry-prod"
  online_key_names = {
    releases  = "seen-registry-prod-releases"
    security  = "seen-registry-prod-security"
    snapshot  = "seen-registry-prod-snapshot"
    timestamp = "seen-registry-prod-timestamp"
  }
  online_public_keys_hex = {
    releases  = "1111111111111111111111111111111111111111111111111111111111111111"
    security  = "2222222222222222222222222222222222222222222222222222222222222222"
    snapshot  = "3333333333333333333333333333333333333333333333333333333333333333"
    timestamp = "4444444444444444444444444444444444444444444444444444444444444444"
  }

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

  read_only_api_enabled                   = true
  infrastructure_executor_service_account = "seen-registry-prod-iac@seen-registry-prod-placeholder.iam.gserviceaccount.com"
  infrastructure_executor_act_as_identities = [
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
  ]
  portfolio_gateway_service_account = "portfolio@portfolio-476219.iam.gserviceaccount.com"
  notification_channel_ids          = ["projects/seen-registry-prod-placeholder/notificationChannels/1"]
  container_image                   = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}

run "valid_read_only_contract_plans" {
  command = plan

  assert {
    condition     = toset(keys(output.application_service_uris)) == toset(["api"])
    error_message = "The valid production fixture must plan exactly the read-only API application service."
  }

  assert {
    condition = (
      toset(keys(google_service_account_iam_member.infrastructure_executor_act_as)) == toset(var.infrastructure_executor_act_as_identities) &&
      alltrue([
        for binding in values(google_service_account_iam_member.infrastructure_executor_act_as) :
        binding.role == "roles/iam.serviceAccountUser" &&
        binding.member == "serviceAccount:seen-registry-prod-iac@seen-registry-prod-placeholder.iam.gserviceaccount.com"
      ])
    )
    error_message = "The executor must receive one exact serviceAccountUser binding on every explicitly approved production runtime account during foundation."
  }

  assert {
    condition = (
      google_artifact_registry_repository_iam_member.infrastructure_executor_reader[0].role == "roles/artifactregistry.reader" &&
      google_artifact_registry_repository_iam_member.infrastructure_executor_reader[0].member == "serviceAccount:seen-registry-prod-iac@seen-registry-prod-placeholder.iam.gserviceaccount.com"
    )
    error_message = "The production executor must receive read-only access to exactly the deployment image repository."
  }

  assert {
    condition = alltrue([
      for key in values(google_kms_crypto_key.online) :
      key.skip_initial_version_creation
    ])
    error_message = "The foundation may create only empty KMS key containers; concrete key versions require a separate reviewed authority phase."
  }
}

run "environment_pair_is_a_hard_input_gate" {
  command = plan

  variables {
    runtime_environment = "development"
  }

  expect_failures = [var.runtime_environment]
}

run "bootstrap_owned_service_exclusions_are_bounded" {
  command = plan

  variables {
    externally_managed_services = [
      "cloudresourcemanager.googleapis.com",
      "iam.googleapis.com",
      "iamcredentials.googleapis.com",
      "monitoring.googleapis.com",
      "orgpolicy.googleapis.com",
      "serviceusage.googleapis.com",
      "storage.googleapis.com",
      "sts.googleapis.com",
    ]
  }

  assert {
    condition = length(setintersection(output.enabled_services, toset([
      "cloudresourcemanager.googleapis.com",
      "iam.googleapis.com",
      "iamcredentials.googleapis.com",
      "monitoring.googleapis.com",
      "serviceusage.googleapis.com",
      "storage.googleapis.com",
      "sts.googleapis.com",
    ]))) == 0
    error_message = "The reusable module must not also own APIs delegated to the production bootstrap root."
  }
}

run "unreviewed_service_exclusion_is_rejected" {
  command = plan

  variables {
    externally_managed_services = ["run.googleapis.com"]
  }

  expect_failures = [var.externally_managed_services]
}

run "read_only_api_is_production_only" {
  command = plan

  variables {
    environment         = "dev"
    runtime_environment = "development"
  }

  expect_failures = [var.read_only_api_enabled]
}

run "production_writer_shape_is_rejected" {
  command = plan

  variables {
    read_only_api_enabled        = false
    workloads_enabled            = true
    signer_jwks_all_apis_enabled = true
    signer_container_image       = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-signer@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    trusted_root_v1_sha256       = "5555555555555555555555555555555555555555555555555555555555555555"
    online_key_version_numbers = {
      releases  = "1"
      security  = "2"
      snapshot  = "3"
      timestamp = "4"
    }
    secret_versions = {
      publisher_token        = "1"
      trust_and_safety_token = "2"
      security_token         = "3"
      github_app_id          = "4"
      github_app_private_key = "5"
    }
  }

  expect_failures = [var.enabled]
}

run "container_image_is_required_by_selected_workloads" {
  command = plan

  variables {
    container_image = null
  }

  expect_failures = [var.container_image]
}

run "online_public_keys_must_be_distinct" {
  command = plan

  variables {
    online_public_keys_hex = {
      releases  = "1111111111111111111111111111111111111111111111111111111111111111"
      security  = "1111111111111111111111111111111111111111111111111111111111111111"
      snapshot  = "3333333333333333333333333333333333333333333333333333333333333333"
      timestamp = "4444444444444444444444444444444444444444444444444444444444444444"
    }
  }

  expect_failures = [var.online_public_keys_hex]
}

run "jwks_exception_requires_selected_signers" {
  command = plan

  variables {
    signer_jwks_all_apis_enabled = true
  }

  expect_failures = [var.signer_jwks_all_apis_enabled]
}

run "api_requires_gateway_or_edge_cutover" {
  command = plan

  variables {
    portfolio_gateway_service_account = null
  }

  expect_failures = [var.portfolio_gateway_service_account]
}

run "production_cloud_run_requires_the_executor" {
  command = plan

  variables {
    infrastructure_executor_service_account   = null
    infrastructure_executor_act_as_identities = []
  }

  expect_failures = [var.infrastructure_executor_service_account]
}

run "executor_must_be_a_distinct_in_project_service_account" {
  command = plan

  variables {
    infrastructure_executor_service_account = "seen-registry-prod@seen-registry-prod-placeholder.iam.gserviceaccount.com"
  }

  expect_failures = [var.infrastructure_executor_service_account]
}

run "executor_act_as_is_preprovisioned_before_refresh_selection" {
  command = plan

  variables {
    read_only_api_enabled        = false
    refresh_jobs_enabled         = true
    signer_jwks_all_apis_enabled = true
    signer_container_image       = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-signer@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    trusted_root_v1_sha256       = "5555555555555555555555555555555555555555555555555555555555555555"
    online_key_version_numbers = {
      releases  = "1"
      security  = "2"
      snapshot  = "3"
      timestamp = "4"
    }
  }

  assert {
    condition     = toset(keys(google_service_account_iam_member.infrastructure_executor_act_as)) == toset(var.infrastructure_executor_act_as_identities)
    error_message = "Refresh selection must reuse the finite actAs bindings pre-provisioned during foundation."
  }
}

run "executor_act_as_remains_finite_for_development_writer_shape" {
  command = plan

  variables {
    environment                  = "dev"
    runtime_environment          = "development"
    read_only_api_enabled        = false
    workloads_enabled            = true
    signer_jwks_all_apis_enabled = true
    signer_container_image       = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-signer@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    trusted_root_v1_sha256       = "5555555555555555555555555555555555555555555555555555555555555555"
    infrastructure_executor_act_as_identities = [
      "api",
      "source",
      "scanner",
      "promoter",
      "release_actions",
      "security_actions",
      "release_refresh",
      "security_refresh",
      "signer_releases",
      "signer_security",
      "signer_snapshot",
      "signer_timestamp",
      "scheduler",
      "root_verifier",
      "offline_bootstrap_importer",
      "online_bootstrap",
      "targets_renewal",
      "targets_releases_rotation",
      "targets_security_rotation",
      "root_importer",
    ]
    online_key_version_numbers = {
      releases  = "1"
      security  = "2"
      snapshot  = "3"
      timestamp = "4"
    }
    secret_versions = {
      publisher_token        = "1"
      trust_and_safety_token = "2"
      security_token         = "3"
      github_app_id          = "4"
      github_app_private_key = "5"
    }
  }

  assert {
    condition     = toset(keys(google_service_account_iam_member.infrastructure_executor_act_as)) == toset(keys(local.service_account_ids))
    error_message = "Executor actAs must remain bounded to the module's finite named runtime accounts."
  }
}

run "production_executor_rejects_hard_disabled_act_as_identity" {
  command = plan

  variables {
    infrastructure_executor_act_as_identities = [
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
      "scheduler",
    ]
  }

  expect_failures = [var.infrastructure_executor_act_as_identities]
}

run "ceremony_requires_exact_secret_versions" {
  command = plan

  variables {
    ceremony_operations = ["offline_bootstrap_importer"]
  }

  expect_failures = [var.secret_versions]
}

run "production_monitoring_requires_a_channel" {
  command = plan

  variables {
    notification_channel_ids = []
  }

  expect_failures = [var.notification_channel_ids]
}

run "github_ci_requires_complete_pinned_claims" {
  command = plan

  variables {
    github_ci_enabled = true
  }

  expect_failures = [var.github_ci_enabled]
}

run "github_claim_values_reject_condition_injection" {
  command = plan

  variables {
    github_ci_enabled          = true
    github_repository          = "codeyousef/summon-portfolio"
    github_repository_id       = "1091564909"
    github_repository_owner_id = "10247142"
    github_ref                 = "refs/heads/master' || true || '"
    github_workflow_ref        = "codeyousef/summon-portfolio/.github/workflows/deploy-seen-registry.yml@refs/heads/master' || true || '"
    github_event_name          = "push"
    github_environment         = "seen-registry-production-image"
  }

  expect_failures = [var.github_ci_enabled]
}
