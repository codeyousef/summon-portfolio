output "service_accounts" {
  description = "Runtime service-account emails keyed by narrowly scoped workload."
  value = var.enabled ? {
    for key, account in google_service_account.runtime : key => account.email
  } : {}
}

output "bucket_names" {
  value = var.enabled ? {
    for key, bucket in google_storage_bucket.registry : key => bucket.name
  } : {}
}

output "online_key_versions" {
  description = "Concrete public KMS resource names consumed by role-locked signer services."
  value       = local.online_key_versions
}

output "application_service_uris" {
  value = var.enabled ? {
    for key, service in google_cloud_run_v2_service.application : key => service.uri
  } : {}
}

output "signer_service_uris" {
  value = var.enabled && local.signers_enabled ? {
    for key, service in google_cloud_run_v2_service.signer : key => service.uri
  } : {}
}

output "enabled_services" {
  description = "Service APIs enabled by the foundation; exposed for deployment-contract tests."
  value       = var.enabled ? toset(keys(google_project_service.required)) : toset([])
}

output "signer_deletion_protection" {
  description = "Deletion-protection state for selected signers; exposed for ceremony cleanup tests."
  value = var.enabled ? {
    for key, service in google_cloud_run_v2_service.signer : key => service.deletion_protection
  } : {}
}

output "application_deletion_protection" {
  description = "Deletion-protection state for selected application services; exposed for lifecycle tests."
  value = var.enabled ? {
    for key, service in google_cloud_run_v2_service.application : key => service.deletion_protection
  } : {}
}

output "long_lived_job_deletion_protection" {
  description = "Deletion-protection state for selected long-lived jobs; exposed for lifecycle tests."
  value = var.enabled ? {
    for key, job in google_cloud_run_v2_job.long_lived : key => job.deletion_protection
  } : {}
}

output "cloudkms_audit_log_types" {
  description = "Cloud KMS audit-log types enabled for signing-alert coverage."
  value = var.enabled && var.monitoring_enabled ? toset([
    for config in google_project_iam_audit_config.cloudkms_data_read[0].audit_log_config : config.log_type
  ]) : toset([])
}

output "kms_signing_metric_filters" {
  description = "Unexpected-signing metric filters keyed by TUF role; exposed for monitoring-contract tests."
  value = var.enabled && var.monitoring_enabled ? {
    for role in local.roles : role => google_logging_metric.registry_alert["unexpected_kms_signing_${role}"].filter
  } : {}
}

output "operational_event_metric_filters" {
  description = "Runtime event filters keyed by monitored operational event."
  value = var.enabled && var.monitoring_enabled ? {
    signer_rejection = google_logging_metric.registry_alert["signer_authorization_denied"].filter
    service_expiry   = google_logging_metric.registry_alert["metadata_expiry_breach_service"].filter
    job_expiry       = google_logging_metric.registry_alert["metadata_expiry_breach_job"].filter
  } : {}
}

output "gateway_environment" {
  description = "Exact public host and selected independently routable private origins for the portfolio gateway."
  value = var.enabled && local.api_enabled && !var.edge_cutover_enabled ? merge(
    {
      SEEN_REGISTRY_PUBLIC_HOST  = var.uptime_host
      SEEN_REGISTRY_UPSTREAM_URL = google_cloud_run_v2_service.application["api"].uri
    },
    var.workloads_enabled ? {
      SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL  = google_cloud_run_v2_service.application["release_actions"].uri
      SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL = google_cloud_run_v2_service.application["security_actions"].uri
    } : {},
  ) : {}
}

output "read_only_gateway_environment" {
  description = "Credential-free production catalog gateway contract. Action upstreams are intentionally absent."
  value = var.enabled && local.read_only_api_enabled ? {
    SEEN_REGISTRY_PUBLIC_HOST  = var.uptime_host
    SEEN_REGISTRY_UPSTREAM_URL = google_cloud_run_v2_service.application["api"].uri
  } : {}
}

output "long_lived_job_names" {
  value = var.enabled ? {
    for key, job in google_cloud_run_v2_job.long_lived : key => job.name
  } : {}
}

output "ceremony_job_names" {
  value = var.enabled && length(var.ceremony_operations) > 0 ? {
    for key, job in google_cloud_run_v2_job.ceremony : key => job.name
  } : {}
}

output "network" {
  value = var.enabled ? try({
    network    = google_compute_network.registry[0].name
    subnetwork = google_compute_subnetwork.registry[0].name
  }, null) : null
}

output "edge" {
  description = "Pre-provisioned HTTPS edge. DNS is intentionally not changed by this stack."
  value = var.enabled && var.workloads_enabled && var.edge_provisioned ? {
    ipv4_address     = google_compute_global_address.edge[0].address
    certificate_name = google_certificate_manager_certificate.edge[0].name
    certificate_dns_authorizations = {
      for domain, authorization in google_certificate_manager_dns_authorization.edge : domain => {
        name = try(authorization.dns_resource_record[0].name, null)
        type = try(authorization.dns_resource_record[0].type, null)
        data = try(authorization.dns_resource_record[0].data, null)
      }
    }
    https_forward_rule = google_compute_global_forwarding_rule.edge_https[0].name
    url_map            = google_compute_url_map.edge[0].name
  } : null
}

output "workload_identity_provider" {
  description = "Full GitHub OIDC provider resource name consumed by google-github-actions/auth."
  value       = var.enabled && var.github_ci_enabled ? try(google_iam_workload_identity_pool_provider.github[0].name, null) : null
}

output "image_publisher_service_account" {
  description = "Artifact Registry-only GitHub CI service-account email."
  value       = var.enabled && var.github_ci_enabled ? try(google_service_account.image_publisher[0].email, null) : null
}
