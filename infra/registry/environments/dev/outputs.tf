output "gateway_environment" {
  value = module.registry.gateway_environment
}

output "service_accounts" {
  value = module.registry.service_accounts
}

output "application_service_uris" {
  value = module.registry.application_service_uris
}

output "signer_service_uris" {
  value = module.registry.signer_service_uris
}

output "job_names" {
  value = merge(module.registry.long_lived_job_names, module.registry.ceremony_job_names)
}

output "edge" {
  value = module.registry.edge
}

output "workload_identity_provider" {
  value = module.registry.workload_identity_provider
}

output "image_publisher_service_account" {
  value = module.registry.image_publisher_service_account
}
