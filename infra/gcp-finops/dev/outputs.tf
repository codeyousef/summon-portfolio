output "enabled" {
  value = var.resources_enabled
}

output "worker_configuration" {
  description = "Non-secret values used to generate the dev Worker GCP billing configuration."
  value = var.resources_enabled ? {
    billing_project_id     = var.project_id
    billing_dataset        = local.billing_source_dataset_id
    billing_table          = local.standard_export_table_id
    billing_location       = var.billing_dataset_location
    wif_provider_audience  = "//iam.googleapis.com/${google_iam_workload_identity_pool_provider.access[0].name}"
    wif_service_account    = local.finops_reader_email
    maximum_bytes_billed   = 50000000
    authoritative_database = "firestore"
  } : null
}
