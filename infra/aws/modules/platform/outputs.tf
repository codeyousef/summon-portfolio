output "resources_enabled" {
  value = var.resources_enabled
}

output "hosts" {
  value = local.hosts
}

output "dev_gate_enabled" {
  value = var.resources_enabled && local.is_dev
}

output "path_rules" {
  description = "Host-scoped path rules reproduced from the Cloudflare edge Worker."
  value       = local.path_rules
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

output "table_indexes" {
  description = "Secondary index key attributes (hash, range) per table."
  value = {
    for table, spec in local.tables : table => {
      for name, index in spec.indexes : name => [index.hash_key, index.range_key]
    }
  }
}

output "cluster" {
  value = var.resources_enabled ? aws_ecs_cluster.main[0].name : null
}

output "cloudfront_domain" {
  value = local.site_distribution ? aws_cloudfront_distribution.site[0].domain_name : null
}

output "certificate_validation_records" {
  description = "ACM validation CNAMEs to mirror at the current DNS host before delegation, keyed by certificate and domain."
  value = merge([
    for certificate, resource in local.certificates : {
      for option in resource.domain_validation_options : "${certificate}:${option.domain_name}" => {
        name  = option.resource_record_name
        type  = option.resource_record_type
        value = option.resource_record_value
      }
    }
  ]...)
}


output "jobs_queue_url" {
  value = var.resources_enabled ? aws_sqs_queue.jobs[0].url : null
}

output "secret_arns" {
  description = "Empty secrets whose values the operator sets out of band before services start."
  value       = { for name, secret in aws_secretsmanager_secret.app : name => secret.arn }
}

output "network" {
  description = "Inputs for stacks that run beside the platform, such as the registry."
  value = var.resources_enabled ? {
    vpc_id                 = data.aws_vpc.edge[0].id
    public_subnet_ids      = data.aws_subnets.public[0].ids
    isolated_subnet_ids    = data.aws_subnets.isolated[0].ids
    cluster_arn            = aws_ecs_cluster.main[0].arn
    zone_id                = data.aws_route53_zone.primary[0].zone_id
    host_suffix            = local.host_suffix
    gateway_security_group = aws_security_group.ssr[0].id
  } : null
}
