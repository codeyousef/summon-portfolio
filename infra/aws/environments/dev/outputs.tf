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
