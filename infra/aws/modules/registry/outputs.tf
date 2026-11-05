output "enabled" {
  value = var.enabled
}

output "tables" {
  value = local.table_names
}

output "buckets" {
  value = local.bucket_names
}

output "runtimes" {
  description = "Runtime catalogue for this environment: kind, command, and active signer roles."
  value = {
    for name, runtime in local.runtimes : name => {
      kind    = runtime.kind
      command = runtime.command
      roles   = runtime.roles
    }
  }
}

output "signer_caller_bindings" {
  value = local.signer_caller_bindings
}

output "signing_keys" {
  description = "Key ARNs exist only after the separately approved KMS phase."
  value       = { for role, key in aws_kms_key.signing : role => key.arn }
}

output "api_lambda_url" {
  description = "Function URL of the read-only API, when it exists."
  value       = local.api_lambda ? aws_lambda_function_url.api[0].function_url : null
}

output "gateway_environment" {
  description = "Portfolio SSR gateway settings for the registry upstreams."
  value = local.routine_ready ? merge(
    {
      SEEN_REGISTRY_PUBLIC_HOST       = "seen.${var.network.host_suffix}"
      SEEN_REGISTRY_UPSTREAM_URL      = local.api_lambda_serving ? trimsuffix(aws_lambda_function_url.api[0].function_url, "/") : "https://${local.internal_hosts.api}"
      SEEN_REGISTRY_UPSTREAM_IDENTITY = "none"
    },
    local.is_dev ? {
      SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL  = "https://${local.internal_hosts["release-actions"]}"
      SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL = "https://${local.internal_hosts["security-actions"]}"
    } : {},
  ) : {}
}

output "schedules" {
  value = {
    for name, job in local.scheduled_jobs : name => {
      expression = job.schedule.expression
      state      = var.schedules_enabled ? "ENABLED" : "DISABLED"
    }
  }
}

output "secret_arns" {
  value = { for name, secret in aws_secretsmanager_secret.registry : name => secret.arn }
}

output "certificate_validation_records" {
  description = "ACM validation CNAME for the internal wildcard, to mirror at the current DNS host before delegation."
  value = var.enabled ? {
    for option in aws_acm_certificate.internal[0].domain_validation_options : option.domain_name => {
      name  = option.resource_record_name
      type  = option.resource_record_type
      value = option.resource_record_value
    }
  } : {}
}
