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

output "application_service_uris" {
  value = module.registry.application_service_uris
}

output "read_only_gateway_environment" {
  value = module.registry.read_only_gateway_environment
}

output "signer_service_uris" {
  value = module.registry.signer_service_uris
}

output "long_lived_job_names" {
  value = module.registry.long_lived_job_names
}

output "ceremony_job_names" {
  value = module.registry.ceremony_job_names
}

output "job_operations_authorizations" {
  value = module.registry.job_operations_authorizations
}

output "online_key_versions" {
  value = module.registry.online_key_versions
}
