output "resources_enabled" {
  value = module.platform.resources_enabled
}

output "hosts" {
  value = module.platform.hosts
}

output "dev_gate_enabled" {
  value = module.platform.dev_gate_enabled
}

output "schedules" {
  value = module.platform.schedules
}

output "buckets" {
  value = module.platform.buckets
}

output "tables" {
  value = module.platform.tables
}

output "cluster" {
  value = module.platform.cluster
}

output "cloudfront_domain" {
  value = module.platform.cloudfront_domain
}

output "jobs_queue_url" {
  value = module.platform.jobs_queue_url
}

output "secret_arns" {
  value = module.platform.secret_arns
}

output "registry" {
  value = {
    tables                 = module.registry.tables
    buckets                = module.registry.buckets
    runtimes               = module.registry.runtimes
    signer_caller_bindings = module.registry.signer_caller_bindings
    signing_keys           = module.registry.signing_keys
    schedules              = module.registry.schedules
    secret_arns            = module.registry.secret_arns
  }
}

output "certificate_validation_records" {
  description = "Mirror these at Cloudflare (DNS only) until the zone is delegated to Route 53."
  value       = merge(module.platform.certificate_validation_records, module.registry.certificate_validation_records)
}
