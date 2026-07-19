mock_provider "google" {}

run "production_is_inert_by_default" {
  command = plan

  variables {
    project_id = "seen-registry-prod-476219"
  }

  assert {
    condition     = length(output.service_accounts) == 0
    error_message = "The default production plan must create no identities."
  }

  assert {
    condition     = length(output.bucket_names) == 0
    error_message = "The default production plan must create no buckets."
  }

  assert {
    condition     = output.network == null
    error_message = "The default production plan must create no network."
  }

  assert {
    condition     = output.workload_identity_provider == null && output.image_publisher_service_account == null
    error_message = "The inert production root must not create a CI trust path."
  }

  assert {
    condition = (
      length(output.application_service_uris) == 0 &&
      length(output.read_only_gateway_environment) == 0 &&
      length(output.signer_service_uris) == 0 &&
      length(output.long_lived_job_names) == 0 &&
      length(output.ceremony_job_names) == 0
    )
    error_message = "The inert production root must not create API, signer, refresh, or ceremony resources."
  }

  assert {
    condition = (
      !var.enable_production_foundation &&
      !var.enable_production_read_only_api &&
      !var.enable_production_root_verifier &&
      !var.enable_production_refresh_jobs &&
      length(var.production_ceremony_operations) == 0 &&
      !var.signer_jwks_all_apis_enabled &&
      !var.github_ci_enabled &&
      !var.bootstrap_production_policies_effective &&
      !var.bootstrap_creator_owner_removed
    )
    error_message = "Every production selector and the creator-Owner cleanup assertion must remain false by default."
  }

  assert {
    condition     = var.iac_executor_service_account == "seen-registry-prod-iac@seen-registry-prod-476219.iam.gserviceaccount.com"
    error_message = "The production module must grant narrow infrastructure access only to the bootstrap-created apply identity."
  }

  assert {
    condition     = !strcontains(file("${path.root}/versions.tf"), "impersonate_service_account")
    error_message = "The production provider must consume workload-federated ADC directly instead of self-impersonating the apply identity."
  }
}
