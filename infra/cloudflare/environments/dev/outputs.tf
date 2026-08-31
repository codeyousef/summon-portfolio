output "resources_enabled" {
  value = module.platform.resources_enabled
}

output "finops_access" {
  value = module.platform.finops_access
}

output "dev_access" {
  value = module.platform.dev_access
}

output "finops_wif_access_client_secret" {
  description = "Sensitive bootstrap value for the dev Worker secret."
  value       = module.platform.finops_wif_access_client_secret
  sensitive   = true
}

output "dev_machine_access" {
  value = module.platform.dev_machine_access
}

output "dev_machine_access_client_secrets" {
  description = "Sensitive bootstrap values for exact dev machine boundaries."
  value       = module.platform.dev_machine_access_client_secrets
  sensitive   = true
}

output "dev_remote_access" {
  description = "Dev-only path-specific remote Access contract."
  value       = module.platform.dev_remote_access
}

output "authoritative_database" {
  value = module.platform.authoritative_database
}

output "r2_buckets" {
  value = module.platform.r2_buckets
}

output "queues" {
  value = module.platform.queues
}

output "worker_release_contracts" {
  value = module.platform.worker_release_contracts
}

output "zone_import_contracts" {
  value = module.platform.zone_import_contracts
}

output "portfolio_firestore_runtime" {
  value = {
    project_id             = "portfolio-476219"
    database_id            = "portfolio-me-dev"
    location               = "me-central2"
    initial_write_mode     = "source"
    authoritative_platform = "gcp-firestore"
  }
}
