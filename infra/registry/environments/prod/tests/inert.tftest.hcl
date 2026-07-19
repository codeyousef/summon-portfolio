mock_provider "google" {}

run "production_is_inert_by_default" {
  command = plan

  variables {
    project_id = "seen-registry-prod-placeholder"
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
}
