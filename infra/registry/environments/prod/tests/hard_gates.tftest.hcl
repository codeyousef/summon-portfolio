mock_provider "google" {}

run "rejects_any_unreviewed_project" {
  command = plan

  variables {
    project_id = "portfolio-476219"
  }

  expect_failures = [var.project_id]
}

run "rejects_child_selector_without_foundation" {
  command = plan

  variables {
    project_id                      = "seen-registry-prod-476219"
    bootstrap_creator_owner_removed = true
    enable_production_read_only_api = true
  }

  expect_failures = [var.enable_production_foundation]
}

run "rejects_runtime_before_creator_owner_cleanup" {
  command = plan

  override_data {
    target = data.google_monitoring_notification_channel.operations
    values = {
      name                = "projects/seen-registry-prod-476219/notificationChannels/1"
      enabled             = true
      verification_status = "VERIFIED"
    }
  }

  variables {
    project_id                              = "seen-registry-prod-476219"
    bootstrap_production_policies_effective = true
    enable_production_foundation            = true
    enable_production_root_verifier         = true
    notification_channel_ids                = ["projects/seen-registry-prod-476219/notificationChannels/1"]
    container_image                         = "us-central1-docker.pkg.dev/seen-registry-prod-476219/seen-registry/seen-registry@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    online_public_keys_hex = {
      releases  = "1111111111111111111111111111111111111111111111111111111111111111"
      security  = "2222222222222222222222222222222222222222222222222222222222222222"
      snapshot  = "3333333333333333333333333333333333333333333333333333333333333333"
      timestamp = "4444444444444444444444444444444444444444444444444444444444444444"
    }
  }

  expect_failures = [var.bootstrap_creator_owner_removed]
}

run "rejects_wrong_gateway_identity" {
  command = plan

  variables {
    project_id                        = "seen-registry-prod-476219"
    portfolio_gateway_service_account = "other@portfolio-476219.iam.gserviceaccount.com"
  }

  expect_failures = [var.portfolio_gateway_service_account]
}

run "rejects_cross_project_notification_channel" {
  command = plan

  variables {
    project_id                              = "seen-registry-prod-476219"
    bootstrap_production_policies_effective = true
    enable_production_foundation            = true
    notification_channel_ids                = ["projects/portfolio-476219/notificationChannels/1"]
  }

  expect_failures = [var.notification_channel_ids]
}

run "rejects_unverified_notification_channel" {
  command = plan

  variables {
    project_id                              = "seen-registry-prod-476219"
    bootstrap_production_policies_effective = true
    enable_production_foundation            = true
    notification_channel_ids                = ["projects/seen-registry-prod-476219/notificationChannels/1"]
  }

  override_data {
    target = data.google_monitoring_notification_channel.operations
    values = {
      name                = "projects/seen-registry-prod-476219/notificationChannels/1"
      enabled             = true
      verification_status = "UNVERIFIED"
    }
  }

  expect_failures = [terraform_data.production_launch_gate]
}

run "rejects_foundation_before_bootstrap_policies_are_effective" {
  command = plan

  override_data {
    target = data.google_monitoring_notification_channel.operations
    values = {
      name                = "projects/seen-registry-prod-476219/notificationChannels/1"
      enabled             = true
      verification_status = "VERIFIED"
    }
  }

  variables {
    project_id                   = "seen-registry-prod-476219"
    enable_production_foundation = true
    notification_channel_ids     = ["projects/seen-registry-prod-476219/notificationChannels/1"]
  }

  expect_failures = [var.enable_production_foundation]
}

run "rejects_wrong_infrastructure_executor" {
  command = plan

  variables {
    project_id                   = "seen-registry-prod-476219"
    iac_executor_service_account = "other@seen-registry-prod-476219.iam.gserviceaccount.com"
  }

  expect_failures = [var.iac_executor_service_account]
}

run "rejects_custom_job_operations_identity" {
  command = plan

  variables {
    project_id                     = "seen-registry-prod-476219"
    job_operations_service_account = "other@seen-registry-prod-476219.iam.gserviceaccount.com"
  }

  expect_failures = [var.job_operations_service_account]
}

run "rejects_cross_project_job_operations_identity" {
  command = plan

  variables {
    project_id                     = "seen-registry-prod-476219"
    job_operations_service_account = "seen-registry-prod-job-runner@other-project.iam.gserviceaccount.com"
  }

  expect_failures = [var.job_operations_service_account]
}

run "rejects_custom_job_operations_viewer_role" {
  command = plan

  variables {
    project_id                 = "seen-registry-prod-476219"
    job_operations_viewer_role = "projects/seen-registry-prod-476219/roles/customJobViewer"
  }

  expect_failures = [var.job_operations_viewer_role]
}

run "production_executor_act_as_scope_is_exact" {
  command = plan

  variables {
    project_id = "seen-registry-prod-476219"
  }

  assert {
    condition = (
      local.infrastructure_executor_act_as_identities == toset([
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
      ]) &&
      length(local.infrastructure_executor_act_as_identities) == 14 &&
      length(setintersection(
        local.infrastructure_executor_act_as_identities,
        toset([
          "source",
          "scanner",
          "promoter",
          "release_actions",
          "security_actions",
          "scheduler",
        ]),
      )) == 0
    )
    error_message = "Production actAs must cover exactly the 14 selectable API, verifier, refresh, signer, and ceremony identities and exclude every hard-disabled writer or scheduler identity."
  }
}
