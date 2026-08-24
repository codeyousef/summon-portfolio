output "resources_enabled" {
  value = module.platform.resources_enabled
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
    database_id            = "portfolio-me-prod"
    location               = "me-central2"
    initial_write_mode     = "source"
    authoritative_platform = "gcp-firestore"
  }
}
