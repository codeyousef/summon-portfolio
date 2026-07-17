output "service_accounts" {
  value = module.registry.service_accounts
}

output "bucket_names" {
  value = module.registry.bucket_names
}

output "network" {
  value = module.registry.network
}

output "workload_identity_provider" {
  value = module.registry.workload_identity_provider
}

output "image_publisher_service_account" {
  value = module.registry.image_publisher_service_account
}
