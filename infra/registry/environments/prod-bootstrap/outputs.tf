output "organization_default_network_policy" {
  value = var.enable_organization_guardrails ? google_org_policy_policy.skip_default_network[0].name : null
}

output "organization_guardrail_effective" {
  value = var.organization_guardrail_effective
}

output "enabled_control_project_services" {
  value = toset(keys(google_project_service.control))
}

output "production_project_id" {
  value = var.enable_production_project_creation ? google_project.production[0].project_id : null
}

output "production_project_number" {
  value = var.enable_production_project_creation ? google_project.production[0].number : null
}

output "production_project_creation_verified" {
  value = var.production_project_creation_verified
}

output "production_state_backend" {
  value = var.enable_production_project_bootstrap ? local.state_backends.production : null
}

output "bootstrap_state_backend" {
  value = var.enable_production_project_bootstrap ? local.state_backends.bootstrap : null
}

output "production_notification_channel_id" {
  value = var.enable_production_project_bootstrap ? google_monitoring_notification_channel.operations[0].name : null
}

output "production_notification_channel_verification_status" {
  value = var.enable_production_project_bootstrap ? google_monitoring_notification_channel.operations[0].verification_status : null
}

output "infrastructure_plan_service_account" {
  value = var.enable_production_project_bootstrap ? google_service_account.infrastructure["plan"].email : null
}

output "project_executor_service_account" {
  value = var.enable_production_project_bootstrap ? google_service_account.infrastructure["apply"].email : null
}

output "materials_operations_service_account" {
  value = var.enable_production_project_bootstrap ? google_service_account.infrastructure["materials"].email : null
}

output "job_operations_service_account" {
  value = var.enable_production_project_bootstrap ? google_service_account.infrastructure["jobs"].email : null
}

output "infrastructure_workload_identity_pool" {
  value = var.enable_production_project_bootstrap ? google_iam_workload_identity_pool.infrastructure[0].name : null
}

output "infrastructure_workload_identity_providers" {
  value = var.enable_production_project_bootstrap ? {
    for identity, provider in google_iam_workload_identity_pool_provider.infrastructure :
    identity => provider.name if contains(keys(local.infrastructure_identities), identity)
  } : {}
}

output "operations_workload_identity_providers" {
  value = var.enable_production_project_bootstrap ? {
    for identity, provider in google_iam_workload_identity_pool_provider.infrastructure :
    identity => provider.name if contains(keys(local.operations_identities), identity)
  } : {}
}

output "infrastructure_custom_roles" {
  value = var.enable_production_project_bootstrap ? {
    plan                 = local.infrastructure_role_names.plan
    apply                = local.infrastructure_role_names.apply
    project_iam          = local.infrastructure_role_names.project_iam
    resource_iam_setters = local.resource_iam_setter_role_names
  } : null
}

output "materials_operations_custom_roles" {
  value = var.enable_production_project_bootstrap ? local.material_role_names : null
}

output "job_operations_viewer_role" {
  value = var.enable_production_project_bootstrap ? local.job_operations_viewer_role_name : null
}

output "organization_bootstrap_refresh_role" {
  value = var.enable_production_project_bootstrap ? local.organization_refresh_role_name : null
}

output "billing_account_refresh_role" {
  value = var.enable_production_project_bootstrap ? local.billing_refresh_role_name : null
}

output "control_project_refresh_role" {
  value = var.enable_production_project_bootstrap ? local.control_project_refresh_role_name : null
}

output "active_bootstrap_identity" {
  value = length(data.google_client_openid_userinfo.bootstrap) == 1 ? data.google_client_openid_userinfo.bootstrap[0].email : null
}

output "project_creator_owner_adopted" {
  value = var.adopt_project_creator_owner
}

output "project_creator_owner_removal_approved" {
  value = var.approve_project_creator_owner_removal
}

output "project_creator_owner_removed" {
  value = var.project_creator_owner_removed
}

output "project_executor_handoff_complete" {
  value = var.project_executor_handoff_complete
}

output "production_foundation_applied" {
  value = var.production_foundation_applied
}

output "production_image_publisher_foundation_applied" {
  value = var.production_image_publisher_foundation_applied
}

output "temporary_human_state_migration_access_enabled" {
  value = var.enable_temporary_human_state_migration_access
}

output "state_bucket_iam_reconciliation_enabled" {
  value = var.enable_state_bucket_iam_reconciliation
}

output "temporary_human_state_bucket_policy_access_enabled" {
  value = var.enable_temporary_human_state_bucket_policy_access
}

output "temporary_human_state_bucket_policy_access_verified" {
  value = var.temporary_human_state_bucket_policy_access_verified
}

output "temporary_human_state_bucket_policy_access_removal_approved" {
  value = var.approve_temporary_human_state_bucket_policy_access_removal
}

output "temporary_human_state_bucket_policy_access_removed" {
  value = var.temporary_human_state_bucket_policy_access_removed
}

output "enabled_bootstrap_services" {
  value = var.enable_production_project_bootstrap ? toset(keys(google_project_service.bootstrap)) : toset([])
}

output "automatic_default_service_account_grants_policy" {
  value = var.enable_production_project_bootstrap ? google_org_policy_policy.automatic_default_service_account_grants[0].name : null
}

output "automatic_default_service_account_grants_policy_effective" {
  value = var.automatic_default_service_account_grants_policy_effective
}

output "portfolio_gateway_exception_enabled" {
  value = var.enable_portfolio_gateway_exception
}

output "portfolio_gateway_exception_effective" {
  value = var.portfolio_gateway_exception_effective
}
