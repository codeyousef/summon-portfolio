mock_provider "google" {}

run "read_only_api_has_no_writer_surfaces" {
  command = plan

  variables {
    project_id                        = "seen-registry-prod-placeholder"
    enable_production_foundation      = true
    enable_production_read_only_api   = true
    enable_production_root_verifier   = true
    github_ci_enabled                 = true
    notification_channel_ids          = ["projects/seen-registry-prod-placeholder/notificationChannels/1"]
    portfolio_gateway_service_account = "portfolio@portfolio-476219.iam.gserviceaccount.com"
    container_image                   = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    online_public_keys_hex = {
      releases  = "1111111111111111111111111111111111111111111111111111111111111111"
      security  = "2222222222222222222222222222222222222222222222222222222222222222"
      snapshot  = "3333333333333333333333333333333333333333333333333333333333333333"
      timestamp = "4444444444444444444444444444444444444444444444444444444444444444"
    }
  }

  assert {
    condition     = toset(keys(output.application_service_uris)) == toset(["api"])
    error_message = "The read-only launch must create only the API application service."
  }

  assert {
    condition = toset(keys(output.read_only_gateway_environment)) == toset([
      "SEEN_REGISTRY_PUBLIC_HOST",
      "SEEN_REGISTRY_UPSTREAM_URL",
    ])
    error_message = "The read-only gateway contract must omit both action upstreams."
  }

  assert {
    condition     = length(output.signer_service_uris) == 0
    error_message = "The API-only plan must not create signer services."
  }

  assert {
    condition     = toset(keys(output.long_lived_job_names)) == toset(["root_verifier"])
    error_message = "Only the explicitly selected read-only root verifier may accompany the API."
  }

  assert {
    condition     = length(output.ceremony_job_names) == 0
    error_message = "The API-only plan must not create ceremony jobs."
  }

  assert {
    condition = (
      alltrue([for protected in values(module.registry.application_deletion_protection) : !protected]) &&
      alltrue([for protected in values(module.registry.long_lived_job_deletion_protection) : !protected])
    )
    error_message = "Production API and verifier definitions must remain removable through a complete saved plan."
  }

  assert {
    condition     = output.workload_identity_provider != null && output.image_publisher_service_account != null
    error_message = "The separately enabled production image publisher must expose its WIF provider and service account."
  }

  assert {
    condition     = contains(module.registry.enabled_services, "sts.googleapis.com")
    error_message = "GitHub workload identity federation requires the Security Token Service API."
  }

  assert {
    condition = (
      module.registry.cloudkms_audit_log_types == toset(["DATA_READ"]) &&
      alltrue([
        for filter in values(module.registry.kms_signing_metric_filters) :
        strcontains(filter, "protoPayload.methodName=\"AsymmetricSign\"") &&
        !strcontains(filter, "KeyManagementService.AsymmetricSign")
      ])
    )
    error_message = "Unexpected-signing alerts require Cloud KMS DATA_READ logs and the exact AsymmetricSign audit method."
  }

  assert {
    condition = (
      strcontains(module.registry.operational_event_metric_filters.signer_rejection, "textPayload:\"event=tuf_signing_rejected\"") &&
      strcontains(module.registry.operational_event_metric_filters.service_expiry, "textPayload:\"event=tuf_metadata_expiry_breach\"") &&
      strcontains(module.registry.operational_event_metric_filters.service_expiry, "textPayload:\"environment=production\"") &&
      strcontains(module.registry.operational_event_metric_filters.job_expiry, "textPayload:\"event=tuf_metadata_expiry_breach\"")
    )
    error_message = "Operational alerts must consume the exact text events emitted by the runtime logger."
  }
}

run "refresh_jobs_are_narrow_and_schedule_free" {
  command = plan

  variables {
    project_id                     = "seen-registry-prod-placeholder"
    enable_production_foundation   = true
    enable_production_refresh_jobs = true
    signer_jwks_all_apis_enabled   = true
    notification_channel_ids       = ["projects/seen-registry-prod-placeholder/notificationChannels/1"]
    container_image                = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    signer_container_image         = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    trusted_root_v1_sha256         = "5555555555555555555555555555555555555555555555555555555555555555"
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
  }

  assert {
    condition     = length(output.application_service_uris) == 0 && length(output.read_only_gateway_environment) == 0
    error_message = "Refresh-only selection must not create API or action origins."
  }

  assert {
    condition     = toset(keys(output.signer_service_uris)) == toset(["releases", "security", "snapshot", "timestamp"])
    error_message = "Refresh jobs require exactly one role-locked signer per online role."
  }

  assert {
    condition = (
      alltrue([for protected in values(module.registry.signer_deletion_protection) : !protected]) &&
      alltrue([for protected in values(module.registry.long_lived_job_deletion_protection) : !protected])
    )
    error_message = "Production refresh jobs and signers must be removable before an isolated ceremony."
  }

  assert {
    condition     = toset(keys(output.long_lived_job_names)) == toset(["release_refresh", "security_refresh"])
    error_message = "Refresh-only selection must omit source, scanner, promoter, and root-verifier jobs."
  }

  assert {
    condition     = length(output.ceremony_job_names) == 0
    error_message = "Refresh selection must not imply ceremony authority."
  }
}

run "offline_bootstrap_is_one_use_and_signer_free" {
  command = plan

  variables {
    project_id                     = "seen-registry-prod-placeholder"
    enable_production_foundation   = true
    notification_channel_ids       = ["projects/seen-registry-prod-placeholder/notificationChannels/1"]
    production_ceremony_operations = ["offline_bootstrap_importer"]
    container_image                = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    online_public_keys_hex = {
      releases  = "1111111111111111111111111111111111111111111111111111111111111111"
      security  = "2222222222222222222222222222222222222222222222222222222222222222"
      snapshot  = "3333333333333333333333333333333333333333333333333333333333333333"
      timestamp = "4444444444444444444444444444444444444444444444444444444444444444"
    }
    secret_versions = {
      bootstrap_root_envelope    = "1"
      bootstrap_targets_envelope = "2"
    }
  }

  assert {
    condition     = length(output.application_service_uris) == 0 && length(output.signer_service_uris) == 0 && length(output.long_lived_job_names) == 0
    error_message = "Offline bootstrap must not imply API, signer, or long-lived job resources."
  }

  assert {
    condition     = toset(keys(output.ceremony_job_names)) == toset(["offline_bootstrap_importer"])
    error_message = "Offline bootstrap must create only its one selected ephemeral job."
  }
}

run "online_bootstrap_has_only_bootstrap_signing_authority" {
  command = plan

  variables {
    project_id                     = "seen-registry-prod-placeholder"
    enable_production_foundation   = true
    notification_channel_ids       = ["projects/seen-registry-prod-placeholder/notificationChannels/1"]
    production_ceremony_operations = ["online_bootstrap"]
    signer_jwks_all_apis_enabled   = true
    container_image                = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    signer_container_image         = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    trusted_root_v1_sha256         = "5555555555555555555555555555555555555555555555555555555555555555"
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
  }

  assert {
    condition     = length(output.application_service_uris) == 0 && length(output.long_lived_job_names) == 0
    error_message = "Online bootstrap must not imply API, action, review, or refresh resources."
  }

  assert {
    condition     = toset(keys(output.signer_service_uris)) == toset(["releases", "security", "snapshot", "timestamp"])
    error_message = "Online bootstrap requires exactly the four role-locked signers."
  }

  assert {
    condition     = alltrue([for protected in values(module.registry.signer_deletion_protection) : !protected])
    error_message = "Ceremony-only signers must be removable when the selected operation is cleared."
  }

  assert {
    condition     = toset(keys(output.ceremony_job_names)) == toset(["online_bootstrap"])
    error_message = "Online bootstrap must create only its one selected ephemeral coordinator."
  }
}

run "targets_renewal_grants_only_required_signers" {
  command = plan

  variables {
    project_id                     = "seen-registry-prod-placeholder"
    enable_production_foundation   = true
    notification_channel_ids       = ["projects/seen-registry-prod-placeholder/notificationChannels/1"]
    production_ceremony_operations = ["targets_renewal"]
    signer_jwks_all_apis_enabled   = true
    container_image                = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    signer_container_image         = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    trusted_root_v1_sha256         = "5555555555555555555555555555555555555555555555555555555555555555"
    online_key_version_numbers = {
      snapshot  = "3"
      timestamp = "4"
    }
    online_public_keys_hex = {
      releases  = "1111111111111111111111111111111111111111111111111111111111111111"
      security  = "2222222222222222222222222222222222222222222222222222222222222222"
      snapshot  = "3333333333333333333333333333333333333333333333333333333333333333"
      timestamp = "4444444444444444444444444444444444444444444444444444444444444444"
    }
    secret_versions = {
      targets_renewal_envelope = "1"
    }
  }

  assert {
    condition     = length(output.application_service_uris) == 0 && length(output.long_lived_job_names) == 0
    error_message = "Targets renewal must not imply API, action, review, or refresh resources."
  }

  assert {
    condition     = toset(keys(output.signer_service_uris)) == toset(["snapshot", "timestamp"])
    error_message = "Targets renewal must grant only snapshot and timestamp signer authority."
  }

  assert {
    condition     = alltrue([for protected in values(module.registry.signer_deletion_protection) : !protected])
    error_message = "Ceremony-only renewal signers must be removable after the one-use operation."
  }

  assert {
    condition     = toset(keys(output.ceremony_job_names)) == toset(["targets_renewal"])
    error_message = "Targets renewal must create only its one selected ephemeral coordinator."
  }
}

run "ceremony_rejects_unrelated_refresh_authority" {
  command = plan

  variables {
    project_id                     = "seen-registry-prod-placeholder"
    enable_production_foundation   = true
    enable_production_refresh_jobs = true
    notification_channel_ids       = ["projects/seen-registry-prod-placeholder/notificationChannels/1"]
    production_ceremony_operations = ["targets_renewal"]
    signer_jwks_all_apis_enabled   = true
    container_image                = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    signer_container_image         = "us-central1-docker.pkg.dev/seen-registry-prod-placeholder/seen-registry/registry-service@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    trusted_root_v1_sha256         = "5555555555555555555555555555555555555555555555555555555555555555"
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
    secret_versions = {
      targets_renewal_envelope = "1"
    }
  }

  expect_failures = [check.production_ceremony_isolation]
}
