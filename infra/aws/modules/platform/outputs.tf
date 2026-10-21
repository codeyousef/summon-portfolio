output "resources_enabled" {
  value = var.resources_enabled
}

output "hosts" {
  value = local.hosts
}

output "dev_gate_enabled" {
  value = var.resources_enabled && local.is_dev
}

output "rewrite_routes" {
  description = "Path routes reproduced from the Cloudflare edge Worker."
  value       = local.rewrite_routes
}

output "schedules" {
  value = {
    for name, schedule in local.schedules : name => merge(schedule, {
      state = var.schedules_enabled ? "ENABLED" : "DISABLED"
    })
  }
}

output "buckets" {
  value = {
    for purpose, spec in local.buckets : purpose => merge(spec, {
      name           = local.bucket_names[purpose]
      served_via_cdn = contains(keys(local.cdn_buckets), purpose)
    })
  }
}

output "tables" {
  value = local.table_names
}

output "cluster" {
  value = var.resources_enabled ? aws_ecs_cluster.main[0].name : null
}

output "cloudfront_domain" {
  value = var.resources_enabled ? aws_cloudfront_distribution.site[0].domain_name : null
}

output "jobs_queue_url" {
  value = var.resources_enabled ? aws_sqs_queue.jobs[0].url : null
}

output "secret_arns" {
  description = "Empty secrets whose values the operator sets out of band before services start."
  value       = { for name, secret in aws_secretsmanager_secret.app : name => secret.arn }
}
