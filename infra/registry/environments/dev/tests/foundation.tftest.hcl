mock_provider "google" {}

run "foundation_is_safe_by_default" {
  command = plan

  variables {
    github_ci_enabled    = false
    workloads_enabled    = false
    refresh_jobs_enabled = false
    schedules_enabled    = false
    schedules_paused     = true
    ceremony_operations  = []
    edge_provisioned     = false
    edge_cutover_enabled = false
  }

  assert {
    condition     = length(output.application_service_uris) == 0
    error_message = "Development must not deploy workloads by default."
  }

  assert {
    condition     = length(output.signer_service_uris) == 0
    error_message = "Development must not deploy signers before reviewed pins are supplied."
  }

  assert {
    condition     = length(output.service_accounts) == 20
    error_message = "The foundation must retain every split workload and ceremony identity."
  }

  assert {
    condition     = output.workload_identity_provider == null && output.image_publisher_service_account == null
    error_message = "Development must not enable GitHub trust before its protected environment exists."
  }
}

run "reviewed_cutover_shape" {
  command = plan

  variables {
    container_image        = "us-central1-docker.pkg.dev/portfolio-476219/seen-registry/seen-registry@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    signer_container_image = "us-central1-docker.pkg.dev/portfolio-476219/seen-registry/seen-registry@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    online_key_version_numbers = {
      releases  = "1"
      security  = "2"
      snapshot  = "3"
      timestamp = "4"
    }
    online_public_keys_hex = {
      releases  = "1111111111111111111111111111111111111111111111111111111111111111"
      security  = "2222222222222222222222222222222222222222222222222222222222222222"
      snapshot  = "3333333333333333333333333333333333333333333333333333333333333333"
      timestamp = "4444444444444444444444444444444444444444444444444444444444444444"
    }
    trusted_root_v1_sha256 = "5555555555555555555555555555555555555555555555555555555555555555"
    secret_versions = {
      publisher_token                    = "1"
      trust_and_safety_token             = "2"
      security_token                     = "3"
      github_app_id                      = "4"
      github_app_private_key             = "5"
      gitlab_forge_token                 = "6"
      bootstrap_root_envelope            = "7"
      bootstrap_targets_envelope         = "8"
      targets_renewal_envelope           = "9"
      targets_releases_rotation_envelope = "10"
      targets_security_rotation_envelope = "11"
      root_rotation_envelope             = "12"
    }
    portfolio_gateway_service_account = "portfolio-gateway@portfolio-476219.iam.gserviceaccount.com"
    reviewer_service_account          = "registry-reviewer@portfolio-476219.iam.gserviceaccount.com"
    github_ci_enabled                 = true
    workloads_enabled                 = true
    refresh_jobs_enabled              = true
    ceremony_operations = [
      "offline_bootstrap_importer",
    ]
    schedules_enabled    = true
    schedules_paused     = true
    edge_provisioned     = true
    edge_cutover_enabled = true
  }

  assert {
    condition     = length(output.application_service_uris) == 3
    error_message = "The cutover must expose three independently routed application origins."
  }

  assert {
    condition     = length(output.signer_service_uris) == 4
    error_message = "The cutover must retain one role-locked signer per online TUF role."
  }

  assert {
    condition     = length(output.job_names) == 7
    error_message = "The cutover must include six long-lived/refresh jobs and only the one selected ephemeral ceremony job."
  }

  assert {
    condition     = output.edge != null
    error_message = "The final cutover shape must pre-provision the HTTPS edge without changing DNS."
  }

  assert {
    condition     = output.workload_identity_provider != null && output.image_publisher_service_account != null
    error_message = "The reviewed cutover must replace static CI keys with its claim-bound OIDC image publisher."
  }
}
