check "enabled_configuration_is_complete" {
  assert {
    condition = !var.resources_enabled || (
      var.project_id == "felidai-dev" &&
      var.billing_account_id != null &&
      var.access_issuer != null &&
      var.access_application_audience != null &&
      var.access_service_token_client_id != null
    )
    error_message = "Enabled dev FinOps infrastructure requires the billing account and exact Access issuer, audience, and service-token client ID."
  }
}
