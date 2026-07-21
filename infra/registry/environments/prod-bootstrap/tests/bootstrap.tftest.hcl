mock_provider "google" {}

mock_provider "google" {
  alias = "project"
}

mock_provider "google" {
  alias = "bootstrap_identity"
}

override_data {
  target = data.google_client_openid_userinfo.bootstrap
  values = {
    email = "yousef@felidai.com"
  }
}

override_data {
  target = data.google_project.production_verified
  values = {
    number          = "123456789012"
    org_id          = "567958019562"
    billing_account = "ABCDEF-123456-ABCDEF"
  }
}

override_resource {
  target = google_project.production
  values = {
    number = "123456789012"
  }
}

override_resource {
  target = google_service_account.infrastructure
  values = {
    name  = "projects/seen-registry-prod-476219/serviceAccounts/mock-infrastructure@seen-registry-prod-476219.iam.gserviceaccount.com"
    email = "mock-infrastructure@seen-registry-prod-476219.iam.gserviceaccount.com"
  }
}

override_resource {
  target = google_iam_workload_identity_pool.infrastructure
  values = {
    name = "projects/123456789012/locations/global/workloadIdentityPools/seen-registry-prod-infra"
  }
}

override_data {
  target = data.google_iam_policy.state
  values = {
    policy_data = "{\"bindings\": []}"
  }
}

# Keep every mocked run hermetic from ignored local bootstrap inputs. Individual
# runs opt into only the phase gates they exercise.
variables {
  enable_control_project_apis                                = false
  github_infrastructure_environments_reviewed                = false
  github_operations_environments_reviewed                    = false
  enable_organization_guardrails                             = false
  organization_guardrail_effective                           = false
  enable_organization_policy_admin_lease                     = false
  enable_production_project_creation                         = false
  production_project_creation_verified                       = false
  enable_production_project_bootstrap                        = false
  enable_project_policy_admin_lease                          = false
  policy_admin_lease_expiry                                  = null
  enable_temporary_human_state_migration_access              = false
  temporary_human_state_migration_access_expiry              = null
  enable_temporary_human_state_bucket_policy_access          = false
  temporary_human_state_bucket_policy_access_expiry          = null
  temporary_human_state_bucket_policy_access_verified        = false
  approve_temporary_human_state_bucket_policy_access_removal = false
  temporary_human_state_bucket_policy_access_removed         = false
  enable_state_bucket_iam_reconciliation                     = false
  project_executor_handoff_complete                          = false
  production_foundation_applied                              = false
  production_image_publisher_foundation_applied              = false
  project_creator_owner_member                               = null
  adopt_project_creator_owner                                = false
  approve_project_creator_owner_removal                      = false
  project_creator_owner_removed                              = false
  enable_portfolio_gateway_exception                         = false
  portfolio_gateway_exception_effective                      = false
  managed_member_policy_effective                            = false
  automatic_default_service_account_grants_policy_effective  = false
}

run "bootstrap_is_inert_by_default" {
  command = plan

  variables {
    project_id                 = "seen-registry-prod-476219"
    organization_id            = "567958019562"
    billing_account_id         = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members = ["user:yousef@felidai.com"]
    notification_email         = "yousef@felidai.com"
  }

  assert {
    condition = (
      output.organization_default_network_policy == null &&
      output.production_project_id == null &&
      output.production_project_number == null &&
      output.production_state_backend == null &&
      output.bootstrap_state_backend == null &&
      output.production_notification_channel_id == null &&
      output.infrastructure_plan_service_account == null &&
      output.project_executor_service_account == null &&
      output.materials_operations_service_account == null &&
      output.job_operations_service_account == null &&
      output.infrastructure_workload_identity_pool == null &&
      output.active_bootstrap_identity == null &&
      length(output.enabled_control_project_services) == 0 &&
      length(output.enabled_bootstrap_services) == 0 &&
      !var.enable_production_project_creation &&
      !var.production_project_creation_verified &&
      !var.enable_production_project_bootstrap &&
      !var.enable_organization_policy_admin_lease &&
      !var.enable_project_policy_admin_lease &&
      var.policy_admin_lease_expiry == null &&
      !var.enable_temporary_human_state_bucket_policy_access &&
      var.temporary_human_state_bucket_policy_access_expiry == null &&
      !var.temporary_human_state_bucket_policy_access_verified &&
      !var.approve_temporary_human_state_bucket_policy_access_removal &&
      !var.temporary_human_state_bucket_policy_access_removed &&
      length(split("timeadd(plantimestamp(), \"4h\")", file("${path.root}/variables.tf"))) == 3 &&
      length(split("timeadd(plantimestamp(), \"24h\")", file("${path.root}/variables.tf"))) == 2 &&
      strcontains(file("${path.root}/variables.tf"), "timecmp(var.policy_admin_lease_expiry, timeadd(plantimestamp(), \"4h\")) <= 0") &&
      strcontains(file("${path.root}/variables.tf"), "timecmp(var.temporary_human_state_bucket_policy_access_expiry, timeadd(plantimestamp(), \"4h\")) <= 0") &&
      strcontains(file("${path.root}/variables.tf"), "timecmp(var.temporary_human_state_migration_access_expiry, timeadd(plantimestamp(), \"24h\")) <= 0") &&
      length(split("ignore_changes  = [input]", file("${path.root}/main.tf"))) == 5 &&
      length(data.google_project.production_verified) == 0 &&
      length(google_storage_bucket.state) == 0 &&
      length(google_storage_bucket_iam_policy.state) == 0 &&
      length(google_project_iam_custom_role.state_reader) == 0 &&
      length(google_project_iam_custom_role.state_bucket_policy_reader) == 0 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_read_access) == 0 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_access) == 0 &&
      !output.temporary_human_state_bucket_policy_access_enabled &&
      !output.temporary_human_state_bucket_policy_access_verified &&
      length(google_service_account.infrastructure) == 0 &&
      length(google_iam_workload_identity_pool_provider.infrastructure) == 0 &&
      length(output.infrastructure_workload_identity_providers) == 0 &&
      length(output.operations_workload_identity_providers) == 0 &&
      !var.github_infrastructure_environments_reviewed &&
      !var.github_operations_environments_reviewed &&
      !output.portfolio_gateway_exception_enabled
    )
    error_message = "Every production bootstrap surface must remain inert by default."
  }
}

run "infrastructure_review_does_not_substitute_for_operations_review" {
  command = plan

  variables {
    github_infrastructure_environments_reviewed = true
    project_id                                  = "seen-registry-prod-476219"
    organization_id                             = "567958019562"
    billing_account_id                          = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                  = ["user:yousef@felidai.com"]
    notification_email                          = "yousef@felidai.com"
  }

  assert {
    condition = (
      var.github_infrastructure_environments_reviewed &&
      !var.github_operations_environments_reviewed &&
      length(google_service_account.infrastructure) == 0 &&
      length(google_iam_workload_identity_pool_provider.infrastructure) == 0 &&
      length(google_service_account_iam_member.infrastructure_oidc) == 0 &&
      length(google_project_iam_member.material_key_versions) == 0 &&
      length(google_project_iam_member.material_secret_versions) == 0
    )
    error_message = "An infrastructure-only review must leave every operations identity, provider, trust, and material grant inert."
  }
}

run "project_creation_rejects_missing_operations_review" {
  command = plan

  variables {
    enable_control_project_apis                 = true
    enable_organization_guardrails              = true
    organization_guardrail_effective            = true
    github_infrastructure_environments_reviewed = true
    enable_production_project_creation          = true
    project_id                                  = "seen-registry-prod-476219"
    organization_id                             = "567958019562"
    billing_account_id                          = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                  = ["user:yousef@felidai.com"]
    notification_email                          = "yousef@felidai.com"
  }

  expect_failures = [var.enable_production_project_creation]
}

run "project_creation_is_an_exact_separate_phase" {
  command = plan

  variables {
    enable_control_project_apis                 = true
    enable_organization_guardrails              = true
    organization_guardrail_effective            = true
    github_infrastructure_environments_reviewed = true
    github_operations_environments_reviewed     = true
    enable_production_project_creation          = true
    production_project_creation_verified        = false
    project_id                                  = "seen-registry-prod-476219"
    organization_id                             = "567958019562"
    billing_account_id                          = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                  = ["user:yousef@felidai.com"]
    notification_email                          = "yousef@felidai.com"
  }

  assert {
    condition = (
      length(google_project.production) == 1 &&
      google_project.production[0].project_id == "seen-registry-prod-476219" &&
      google_project.production[0].name == "Seen Registry Production" &&
      google_project.production[0].org_id == "567958019562" &&
      google_project.production[0].billing_account == "ABCDEF-123456-ABCDEF" &&
      google_project.production[0].auto_create_network == false &&
      google_project.production[0].deletion_policy == "PREVENT" &&
      google_project.production[0].labels == tomap({
        application = "seen-registry"
        environment = "prod"
        managed_by  = "opentofu"
        purpose     = "package-registry"
      }) &&
      output.production_project_id == "seen-registry-prod-476219" &&
      output.production_project_number == "123456789012" &&
      output.enabled_control_project_services == toset([
        "cloudbilling.googleapis.com",
        "iam.googleapis.com",
        "orgpolicy.googleapis.com",
      ]) &&
      output.active_bootstrap_identity == "yousef@felidai.com"
    )
    error_message = "Project creation must create exactly the isolated, protected Seen Registry production project under the reviewed human identity."
  }

  assert {
    condition = (
      !var.production_project_creation_verified &&
      length(data.google_project.production_verified) == 0 &&
      length(google_organization_iam_member.policy_admin_lease) == 0 &&
      length(google_project_service.bootstrap) == 0 &&
      length(google_org_policy_policy.automatic_default_service_account_grants) == 0 &&
      length(google_service_account.infrastructure) == 0 &&
      length(google_iam_workload_identity_pool.infrastructure) == 0 &&
      length(google_iam_workload_identity_pool_provider.infrastructure) == 0 &&
      length(google_service_account_iam_member.infrastructure_oidc) == 0 &&
      length(google_project_iam_custom_role.infrastructure_plan) == 0 &&
      length(google_project_iam_custom_role.infrastructure_apply) == 0 &&
      length(google_project_iam_custom_role.project_iam_apply) == 0 &&
      length(google_project_iam_custom_role.resource_iam_setter) == 0 &&
      length(google_project_iam_custom_role.material_key_versions) == 0 &&
      length(google_project_iam_custom_role.material_secret_versions) == 0 &&
      length(google_project_iam_custom_role.job_operations_viewer) == 0 &&
      length(google_project_iam_custom_role.state_reader) == 0 &&
      length(google_project_iam_custom_role.state_bucket_policy_reader) == 0 &&
      length(google_project_iam_custom_role.state_locker) == 0 &&
      length(google_project_iam_custom_role.state_writer) == 0 &&
      length(google_project_iam_member.infrastructure_plan) == 0 &&
      length(google_project_iam_member.infrastructure_apply) == 0 &&
      length(google_project_iam_member.material_key_versions) == 0 &&
      length(google_project_iam_member.material_secret_versions) == 0 &&
      length(google_project_iam_member.infrastructure_project_iam) == 0 &&
      length(google_project_iam_member.infrastructure_kms_policy_setter) == 0 &&
      length(google_project_iam_member.infrastructure_secret_policy_setter) == 0 &&
      length(google_project_iam_member.infrastructure_registry_storage_policy_setter) == 0 &&
      length(google_project_iam_member.infrastructure_state_storage_policy_setter) == 0 &&
      length(google_project_iam_member.infrastructure_run_policy_setter) == 0 &&
      length(google_organization_iam_custom_role.bootstrap_refresh) == 0 &&
      length(google_organization_iam_custom_role.billing_refresh) == 0 &&
      length(google_organization_iam_custom_role.control_project_refresh) == 0 &&
      length(google_project_iam_custom_role.control_project_refresh) == 0 &&
      length(google_organization_iam_member.infrastructure_read) == 0 &&
      length(google_billing_account_iam_member.infrastructure_read) == 0 &&
      length(google_project_iam_member.infrastructure_control_project) == 0 &&
      length(google_project_iam_custom_role.state_bucket_policy_reader) == 0 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_read_access) == 0 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_access) == 0 &&
      length(google_project_iam_member.project_creator_owner) == 0 &&
      length(terraform_data.recovery_reconciliation_record) == 0 &&
      length(terraform_data.project_creator_owner_adoption_record) == 0 &&
      length(terraform_data.recovery_cleanup_record) == 0 &&
      length(terraform_data.project_creator_owner_removal_record) == 0 &&
      length(google_org_policy_policy.managed_allowed_policy_members) == 0 &&
      length(google_org_policy_policy.legacy_domain_restriction_override) == 0 &&
      length(google_storage_bucket.state) == 0 &&
      length(data.google_iam_policy.state) == 0 &&
      length(google_storage_bucket_iam_policy.state) == 0 &&
      length(google_project_iam_audit_config.storage) == 0 &&
      length(google_monitoring_notification_channel.operations) == 0 &&
      length(output.enabled_bootstrap_services) == 0 &&
      output.production_state_backend == null &&
      output.bootstrap_state_backend == null &&
      output.production_notification_channel_id == null &&
      output.infrastructure_plan_service_account == null &&
      output.project_executor_service_account == null &&
      output.materials_operations_service_account == null &&
      output.job_operations_service_account == null &&
      output.infrastructure_workload_identity_pool == null &&
      length(output.infrastructure_workload_identity_providers) == 0 &&
      length(output.operations_workload_identity_providers) == 0 &&
      output.infrastructure_custom_roles == null &&
      output.materials_operations_custom_roles == null &&
      output.job_operations_viewer_role == null &&
      output.organization_bootstrap_refresh_role == null &&
      output.billing_account_refresh_role == null &&
      output.control_project_refresh_role == null &&
      output.automatic_default_service_account_grants_policy == null &&
      !var.enable_project_policy_admin_lease &&
      var.policy_admin_lease_expiry == null &&
      !var.enable_temporary_human_state_bucket_policy_access &&
      var.temporary_human_state_bucket_policy_access_expiry == null &&
      !var.temporary_human_state_bucket_policy_access_verified &&
      !var.approve_temporary_human_state_bucket_policy_access_removal &&
      !var.temporary_human_state_bucket_policy_access_removed &&
      !var.enable_temporary_human_state_migration_access &&
      !var.adopt_project_creator_owner &&
      !var.approve_project_creator_owner_removal &&
      !var.project_creator_owner_removed &&
      !var.enable_portfolio_gateway_exception &&
      !var.portfolio_gateway_exception_effective &&
      !var.managed_member_policy_effective &&
      !var.automatic_default_service_account_grants_policy_effective &&
      !var.production_foundation_applied &&
      !var.production_image_publisher_foundation_applied &&
      !var.project_executor_handoff_complete
    )
    error_message = "Project creation must leave every project-configuration resource and later phase gate inactive until creation is separately verified."
  }
}

run "full_bootstrap_rejects_unverified_project_creation" {
  command = plan

  variables {
    enable_control_project_apis                 = true
    enable_organization_guardrails              = true
    organization_guardrail_effective            = true
    github_infrastructure_environments_reviewed = true
    github_operations_environments_reviewed     = true
    enable_production_project_creation          = true
    enable_production_project_bootstrap         = true
    production_project_creation_verified        = false
    project_id                                  = "seen-registry-prod-476219"
    organization_id                             = "567958019562"
    billing_account_id                          = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                  = ["user:yousef@felidai.com"]
    notification_email                          = "yousef@felidai.com"
  }

  expect_failures = [var.enable_production_project_bootstrap]
}

run "full_bootstrap_rejects_unverified_state_bucket_policy_access" {
  command = plan

  variables {
    enable_control_project_apis                 = true
    enable_organization_guardrails              = true
    organization_guardrail_effective            = true
    github_infrastructure_environments_reviewed = true
    github_operations_environments_reviewed     = true
    enable_production_project_creation          = true
    enable_production_project_bootstrap         = true
    production_project_creation_verified        = true
    enable_project_policy_admin_lease           = true
    policy_admin_lease_expiry                   = timeadd(timestamp(), "2h")
    enable_state_bucket_iam_reconciliation      = true
    project_id                                  = "seen-registry-prod-476219"
    organization_id                             = "567958019562"
    billing_account_id                          = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                  = ["user:yousef@felidai.com"]
    notification_email                          = "yousef@felidai.com"
  }

  expect_failures = [
    terraform_data.bootstrap_phase_contract,
    google_storage_bucket_iam_policy.state,
  ]
}

run "targeted_recovery_installs_exact_policy_bindings_before_verification" {
  command = plan

  plan_options {
    target = [
      google_project_iam_custom_role.state_bucket_policy_reader,
      google_project_iam_member.temporary_human_state_bucket_policy_read_access,
      google_project_iam_member.temporary_human_state_bucket_policy_access,
    ]
  }

  variables {
    enable_control_project_apis                         = true
    enable_organization_guardrails                      = true
    organization_guardrail_effective                    = true
    github_infrastructure_environments_reviewed         = true
    github_operations_environments_reviewed             = true
    enable_production_project_creation                  = true
    enable_production_project_bootstrap                 = true
    production_project_creation_verified                = true
    enable_temporary_human_state_bucket_policy_access   = true
    temporary_human_state_bucket_policy_access_expiry   = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified = false
    enable_state_bucket_iam_reconciliation              = true
    project_id                                          = "seen-registry-prod-476219"
    organization_id                                     = "567958019562"
    billing_account_id                                  = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                          = ["user:yousef@felidai.com"]
    notification_email                                  = "yousef@felidai.com"
  }

  assert {
    condition = (
      !var.temporary_human_state_bucket_policy_access_verified &&
      length(google_project_iam_custom_role.state_bucket_policy_reader) == 1 &&
      google_project_iam_custom_role.state_bucket_policy_reader[0].permissions == toset([
        "storage.buckets.get",
        "storage.buckets.getIamPolicy",
      ]) &&
      google_project_iam_custom_role.resource_iam_setter["storage"].permissions == toset(["storage.buckets.setIamPolicy"])
    )
    error_message = "Recovery must use the exact bucket-policy reader and set-only storage IAM role."
  }

  assert {
    condition = (
      toset(keys(google_project_iam_member.temporary_human_state_bucket_policy_read_access)) == toset(["bootstrap", "production"]) &&
      toset(keys(google_project_iam_member.temporary_human_state_bucket_policy_access)) == toset(["bootstrap", "production"])
    )
    error_message = "Recovery must create the four exact project-level bucket-policy bindings."
  }

  assert {
    condition = (
      strcontains(
        file("${path.root}/main.tf"),
        "expression  = \"resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/$${each.value.bucket}' && request.time < timestamp('$${var.temporary_human_state_bucket_policy_access_expiry}')\"",
      ) &&
      alltrue([
        for name, binding in google_project_iam_member.temporary_human_state_bucket_policy_read_access :
        binding.role == "projects/seen-registry-prod-476219/roles/seenRegistryStateBucketPolicyReader" &&
        binding.member == "user:yousef@felidai.com" &&
        startswith(
          binding.condition[0].expression,
          "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ",
        ) &&
        can(regex(
          "^request\\.time < timestamp\\('[^']+'\\)$",
          trimprefix(
            binding.condition[0].expression,
            "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ",
          ),
        ))
      ])
    )
    error_message = "Recovery readback must use the exact time-bounded bucket-policy-reader binding on both buckets."
  }

  assert {
    condition = (
      strcontains(
        file("${path.root}/main.tf"),
        "expression = format(\n      \"resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s && request.time < timestamp('%s')\",\n      each.value.bucket,\n      local.state_storage_modified_roles_condition,\n      var.temporary_human_state_bucket_policy_access_expiry,\n    )",
      ) &&
      alltrue([
        for name, binding in google_project_iam_member.temporary_human_state_bucket_policy_access :
        binding.role == "projects/seen-registry-prod-476219/roles/seenRegistryStorageIamApply" &&
        binding.member == "user:yousef@felidai.com" &&
        startswith(
          binding.condition[0].expression,
          "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ${local.state_storage_modified_roles_condition} && ",
        ) &&
        can(regex(
          "^request\\.time < timestamp\\('[^']+'\\)$",
          trimprefix(
            binding.condition[0].expression,
            "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ${local.state_storage_modified_roles_condition} && ",
          ),
        )) &&
        trimprefix(
          binding.condition[0].expression,
          "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ${local.state_storage_modified_roles_condition} && ",
          ) == trimprefix(
          google_project_iam_member.temporary_human_state_bucket_policy_read_access[name].condition[0].expression,
          "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ",
        )
      ])
    )
    error_message = "Recovery mutation must use the exact time-bounded state-role setter binding on both buckets."
  }

  assert {
    condition     = length(google_storage_bucket_iam_policy.state) == 0
    error_message = "The exceptional recovery target set must not reconcile authoritative state policies before verification."
  }
}

run "verified_recovery_access_cannot_be_silently_disabled" {
  command = plan

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    temporary_human_state_bucket_policy_access_verified       = true
    managed_member_policy_effective                           = true
    automatic_default_service_account_grants_policy_effective = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  expect_failures = [
    terraform_data.bootstrap_phase_contract,
    terraform_data.phase_evidence_contract,
  ]
}

run "recovery_cleanup_rejects_missing_owner_adoption" {
  command = plan

  plan_options {
    target = [
      terraform_data.bootstrap_phase_contract,
      terraform_data.phase_evidence_contract,
    ]
  }

  variables {
    enable_control_project_apis                                = true
    enable_organization_guardrails                             = true
    organization_guardrail_effective                           = true
    github_infrastructure_environments_reviewed                = true
    github_operations_environments_reviewed                    = true
    enable_production_project_creation                         = true
    enable_production_project_bootstrap                        = true
    production_project_creation_verified                       = true
    temporary_human_state_bucket_policy_access_verified        = true
    enable_state_bucket_iam_reconciliation                     = true
    approve_temporary_human_state_bucket_policy_access_removal = true
    production_foundation_applied                              = true
    automatic_default_service_account_grants_policy_effective  = true
    managed_member_policy_effective                            = true
    enable_portfolio_gateway_exception                         = true
    portfolio_gateway_exception_effective                      = true
    project_creator_owner_member                               = "user:yousef@felidai.com"
    project_id                                                 = "seen-registry-prod-476219"
    organization_id                                            = "567958019562"
    billing_account_id                                         = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                 = ["user:yousef@felidai.com"]
    notification_email                                         = "yousef@felidai.com"
  }

  expect_failures = [
    terraform_data.bootstrap_phase_contract,
    terraform_data.phase_evidence_contract,
  ]
}

run "recovery_cleanup_same_plan_adoption_is_state_guarded" {
  command = plan

  # OpenTofu's mocked test provider cannot execute import blocks. Keep this
  # source-contract test targeted; the saved-plan linter exercises prior-state
  # evidence and deletion rejection without invoking provider imports.
  plan_options {
    target = [google_project_service.control]
  }

  variables {
    enable_control_project_apis                                = true
    enable_organization_guardrails                             = true
    organization_guardrail_effective                           = true
    github_infrastructure_environments_reviewed                = true
    github_operations_environments_reviewed                    = true
    enable_production_project_creation                         = true
    enable_production_project_bootstrap                        = true
    production_project_creation_verified                       = true
    temporary_human_state_bucket_policy_access_verified        = true
    enable_state_bucket_iam_reconciliation                     = true
    approve_temporary_human_state_bucket_policy_access_removal = true
    production_foundation_applied                              = true
    automatic_default_service_account_grants_policy_effective  = true
    managed_member_policy_effective                            = true
    enable_portfolio_gateway_exception                         = true
    portfolio_gateway_exception_effective                      = true
    project_creator_owner_member                               = "user:yousef@felidai.com"
    adopt_project_creator_owner                                = true
    project_id                                                 = "seen-registry-prod-476219"
    organization_id                                            = "567958019562"
    billing_account_id                                         = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                 = ["user:yousef@felidai.com"]
    notification_email                                         = "yousef@felidai.com"
  }

  assert {
    condition = (
      strcontains(file("${path.root}/main.tf"), "resource \"terraform_data\" \"project_creator_owner_adoption_record\"") &&
      strcontains(file("${path.root}/main.tf"), "!var.approve_temporary_human_state_bucket_policy_access_removal &&") &&
      strcontains(file("${path.root}/main.tf"), "condition     = self.output == \"project-creator-owner-adopted\"") &&
      strcontains(file("${path.root}/main.tf"), "ignore_changes  = [input]") &&
      strcontains(file("${path.root}/main.tf"), "local.project_creator_owner_adoption_record_valid")
    )
    error_message = "Owner adoption evidence must be created only in a pure phase and retained immutably for later cleanup."
  }
}

run "owner_removal_rejects_missing_prior_phase_evidence" {
  command = plan

  plan_options {
    target = [terraform_data.phase_evidence_contract]
  }

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    temporary_human_state_bucket_policy_access_verified       = true
    temporary_human_state_bucket_policy_access_removed        = true
    enable_state_bucket_iam_reconciliation                    = true
    production_foundation_applied                             = true
    automatic_default_service_account_grants_policy_effective = true
    enable_temporary_human_state_migration_access             = true
    temporary_human_state_migration_access_expiry             = timeadd(plantimestamp(), "12h")
    managed_member_policy_effective                           = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    project_creator_owner_member                              = "user:yousef@felidai.com"
    approve_project_creator_owner_removal                     = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  expect_failures = [terraform_data.phase_evidence_contract]
}

run "recovery_access_rejects_non_rfc3339_expiry" {
  command = plan

  variables {
    enable_control_project_apis                         = true
    enable_organization_guardrails                      = true
    organization_guardrail_effective                    = true
    github_infrastructure_environments_reviewed         = true
    github_operations_environments_reviewed             = true
    enable_production_project_creation                  = true
    enable_production_project_bootstrap                 = true
    production_project_creation_verified                = true
    enable_project_policy_admin_lease                   = true
    policy_admin_lease_expiry                           = timeadd(timestamp(), "2h")
    enable_temporary_human_state_bucket_policy_access   = true
    temporary_human_state_bucket_policy_access_expiry   = "four-hours"
    temporary_human_state_bucket_policy_access_verified = false
    project_id                                          = "seen-registry-prod-476219"
    organization_id                                     = "567958019562"
    billing_account_id                                  = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                          = ["user:yousef@felidai.com"]
    notification_email                                  = "yousef@felidai.com"
  }

  expect_failures = [var.temporary_human_state_bucket_policy_access_expiry]
}

run "recovery_access_rejects_past_expiry" {
  command = plan

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    automatic_default_service_account_grants_policy_effective = true
    managed_member_policy_effective                           = true
    enable_temporary_human_state_bucket_policy_access         = true
    temporary_human_state_bucket_policy_access_expiry         = "2020-01-01T00:00:00Z"
    temporary_human_state_bucket_policy_access_verified       = false
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  expect_failures = [var.temporary_human_state_bucket_policy_access_expiry]
}

run "recovery_expiry_is_rejected_when_access_is_disabled" {
  command = plan

  variables {
    enable_control_project_apis                         = true
    enable_organization_guardrails                      = true
    organization_guardrail_effective                    = true
    github_infrastructure_environments_reviewed         = true
    github_operations_environments_reviewed             = true
    enable_production_project_creation                  = true
    enable_production_project_bootstrap                 = true
    production_project_creation_verified                = true
    enable_project_policy_admin_lease                   = true
    policy_admin_lease_expiry                           = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_expiry   = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified = false
    project_id                                          = "seen-registry-prod-476219"
    organization_id                                     = "567958019562"
    billing_account_id                                  = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                          = ["user:yousef@felidai.com"]
    notification_email                                  = "yousef@felidai.com"
  }

  expect_failures = [var.temporary_human_state_bucket_policy_access_expiry]
}

run "recovery_expiry_accepts_exact_four_hour_boundary" {
  command = plan

  plan_options {
    target = [terraform_data.bootstrap_phase_contract]
  }

  variables {
    enable_control_project_apis                       = true
    enable_organization_guardrails                    = true
    organization_guardrail_effective                  = true
    github_infrastructure_environments_reviewed       = true
    github_operations_environments_reviewed           = true
    enable_production_project_creation                = true
    enable_production_project_bootstrap               = true
    production_project_creation_verified              = true
    enable_temporary_human_state_bucket_policy_access = true
    temporary_human_state_bucket_policy_access_expiry = timeadd(plantimestamp(), "4h")
    project_id                                        = "seen-registry-prod-476219"
    organization_id                                   = "567958019562"
    billing_account_id                                = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                        = ["user:yousef@felidai.com"]
    notification_email                                = "yousef@felidai.com"
  }

}

run "recovery_expiry_rejects_distant_future" {
  command = plan

  plan_options {
    target = [terraform_data.bootstrap_phase_contract]
  }

  variables {
    enable_control_project_apis                       = true
    enable_organization_guardrails                    = true
    organization_guardrail_effective                  = true
    github_infrastructure_environments_reviewed       = true
    github_operations_environments_reviewed           = true
    enable_production_project_creation                = true
    enable_production_project_bootstrap               = true
    production_project_creation_verified              = true
    enable_temporary_human_state_bucket_policy_access = true
    temporary_human_state_bucket_policy_access_expiry = "9999-12-31T23:59:59Z"
    project_id                                        = "seen-registry-prod-476219"
    organization_id                                   = "567958019562"
    billing_account_id                                = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                        = ["user:yousef@felidai.com"]
    notification_email                                = "yousef@felidai.com"
  }

  expect_failures = [var.temporary_human_state_bucket_policy_access_expiry]
}

run "state_migration_expiry_accepts_exact_twenty_four_hour_boundary" {
  command = plan

  plan_options {
    target = [terraform_data.bootstrap_phase_contract]
  }

  variables {
    enable_control_project_apis                   = true
    enable_organization_guardrails                = true
    organization_guardrail_effective              = true
    github_infrastructure_environments_reviewed   = true
    github_operations_environments_reviewed       = true
    enable_production_project_creation            = true
    enable_production_project_bootstrap           = true
    production_project_creation_verified          = true
    enable_temporary_human_state_migration_access = true
    temporary_human_state_migration_access_expiry = timeadd(plantimestamp(), "24h")
    project_id                                    = "seen-registry-prod-476219"
    organization_id                               = "567958019562"
    billing_account_id                            = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                    = ["user:yousef@felidai.com"]
    notification_email                            = "yousef@felidai.com"
  }

}

run "state_migration_access_rejects_non_rfc3339_expiry" {
  command = plan

  variables {
    enable_control_project_apis                   = true
    enable_organization_guardrails                = true
    organization_guardrail_effective              = true
    github_infrastructure_environments_reviewed   = true
    github_operations_environments_reviewed       = true
    enable_production_project_creation            = true
    enable_production_project_bootstrap           = true
    production_project_creation_verified          = true
    enable_temporary_human_state_migration_access = true
    temporary_human_state_migration_access_expiry = "twenty-four-hours"
    project_id                                    = "seen-registry-prod-476219"
    organization_id                               = "567958019562"
    billing_account_id                            = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                    = ["user:yousef@felidai.com"]
    notification_email                            = "yousef@felidai.com"
  }

  expect_failures = [var.temporary_human_state_migration_access_expiry]
}

run "state_migration_access_rejects_past_expiry" {
  command = plan

  variables {
    enable_control_project_apis                   = true
    enable_organization_guardrails                = true
    organization_guardrail_effective              = true
    github_infrastructure_environments_reviewed   = true
    github_operations_environments_reviewed       = true
    enable_production_project_creation            = true
    enable_production_project_bootstrap           = true
    production_project_creation_verified          = true
    enable_temporary_human_state_migration_access = true
    temporary_human_state_migration_access_expiry = "2020-01-01T00:00:00Z"
    project_id                                    = "seen-registry-prod-476219"
    organization_id                               = "567958019562"
    billing_account_id                            = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                    = ["user:yousef@felidai.com"]
    notification_email                            = "yousef@felidai.com"
  }

  expect_failures = [var.temporary_human_state_migration_access_expiry]
}

run "state_migration_expiry_is_rejected_when_access_is_disabled" {
  command = plan

  variables {
    temporary_human_state_migration_access_expiry = "2020-01-01T00:00:00Z"
    project_id                                    = "seen-registry-prod-476219"
    organization_id                               = "567958019562"
    billing_account_id                            = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                    = ["user:yousef@felidai.com"]
    notification_email                            = "yousef@felidai.com"
  }

  expect_failures = [var.temporary_human_state_migration_access_expiry]
}

run "state_migration_expiry_rejects_distant_future" {
  command = plan

  plan_options {
    target = [terraform_data.bootstrap_phase_contract]
  }

  variables {
    enable_control_project_apis                   = true
    enable_organization_guardrails                = true
    organization_guardrail_effective              = true
    github_infrastructure_environments_reviewed   = true
    github_operations_environments_reviewed       = true
    enable_production_project_creation            = true
    enable_production_project_bootstrap           = true
    production_project_creation_verified          = true
    enable_temporary_human_state_migration_access = true
    temporary_human_state_migration_access_expiry = "9999-12-31T23:59:59Z"
    project_id                                    = "seen-registry-prod-476219"
    organization_id                               = "567958019562"
    billing_account_id                            = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                    = ["user:yousef@felidai.com"]
    notification_email                            = "yousef@felidai.com"
  }

  expect_failures = [var.temporary_human_state_migration_access_expiry]
}

run "control_project_apis_are_a_separate_first_phase" {
  command = plan

  variables {
    enable_control_project_apis = true
    project_id                  = "seen-registry-prod-476219"
    organization_id             = "567958019562"
    billing_account_id          = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members  = ["user:yousef@felidai.com"]
    notification_email          = "yousef@felidai.com"
  }

  assert {
    condition = (
      output.enabled_control_project_services == toset([
        "cloudbilling.googleapis.com",
        "iam.googleapis.com",
        "orgpolicy.googleapis.com",
      ]) &&
      output.organization_default_network_policy == null &&
      output.production_project_id == null
    )
    error_message = "The first phase must enable only the three reviewed control-project APIs."
  }
}

run "organization_guardrail_precedes_project_creation" {
  command = plan

  variables {
    enable_control_project_apis            = true
    enable_organization_guardrails         = true
    enable_organization_policy_admin_lease = true
    policy_admin_lease_expiry              = timeadd(timestamp(), "2h")
    project_id                             = "seen-registry-prod-476219"
    organization_id                        = "567958019562"
    billing_account_id                     = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members             = ["user:yousef@felidai.com"]
    notification_email                     = "yousef@felidai.com"
  }

  assert {
    condition = (
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].role == "roles/orgpolicy.policyAdmin" &&
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].org_id == "567958019562" &&
      length(split("request.time < timestamp('$${var.policy_admin_lease_expiry}')", file("${path.root}/main.tf"))) == 3 &&
      can(regex(
        "^request\\.time < timestamp\\('[^']+'\\)$",
        google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].condition[0].expression,
      )) &&
      google_org_policy_policy.skip_default_network[0].parent == "organizations/567958019562" &&
      google_org_policy_policy.skip_default_network[0].spec[0].rules[0].enforce == "TRUE" &&
      output.production_project_id == null
    )
    error_message = "The guardrail phase must use a time-bounded organization-scoped Policy Admin lease and enforce no-default-network creation."
  }
}

run "policy_admin_lease_rejects_non_rfc3339_expiry" {
  command = plan

  variables {
    enable_control_project_apis            = true
    enable_organization_guardrails         = true
    enable_organization_policy_admin_lease = true
    policy_admin_lease_expiry              = "four-hours"
    project_id                             = "seen-registry-prod-476219"
    organization_id                        = "567958019562"
    billing_account_id                     = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members             = ["user:yousef@felidai.com"]
    notification_email                     = "yousef@felidai.com"
  }

  expect_failures = [var.policy_admin_lease_expiry]
}

run "policy_admin_lease_rejects_past_expiry" {
  command = plan

  variables {
    enable_control_project_apis            = true
    enable_organization_guardrails         = true
    enable_organization_policy_admin_lease = true
    policy_admin_lease_expiry              = "2020-01-01T00:00:00Z"
    project_id                             = "seen-registry-prod-476219"
    organization_id                        = "567958019562"
    billing_account_id                     = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members             = ["user:yousef@felidai.com"]
    notification_email                     = "yousef@felidai.com"
  }

  expect_failures = [var.policy_admin_lease_expiry]
}

run "policy_admin_lease_accepts_exact_four_hour_boundary" {
  command = plan

  plan_options {
    target = [google_organization_iam_member.policy_admin_lease]
  }

  variables {
    enable_control_project_apis            = true
    enable_organization_guardrails         = true
    enable_organization_policy_admin_lease = true
    policy_admin_lease_expiry              = timeadd(plantimestamp(), "4h")
    project_id                             = "seen-registry-prod-476219"
    organization_id                        = "567958019562"
    billing_account_id                     = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members             = ["user:yousef@felidai.com"]
    notification_email                     = "yousef@felidai.com"
  }

}

run "policy_admin_lease_rejects_distant_future" {
  command = plan

  plan_options {
    target = [google_organization_iam_member.policy_admin_lease]
  }

  variables {
    enable_control_project_apis            = true
    enable_organization_guardrails         = true
    enable_organization_policy_admin_lease = true
    policy_admin_lease_expiry              = "9999-12-31T23:59:59Z"
    project_id                             = "seen-registry-prod-476219"
    organization_id                        = "567958019562"
    billing_account_id                     = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members             = ["user:yousef@felidai.com"]
    notification_email                     = "yousef@felidai.com"
  }

  expect_failures = [var.policy_admin_lease_expiry]
}

run "policy_admin_expiry_is_rejected_without_a_lease_purpose" {
  command = plan

  variables {
    policy_admin_lease_expiry  = timeadd(timestamp(), "2h")
    project_id                 = "seen-registry-prod-476219"
    organization_id            = "567958019562"
    billing_account_id         = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members = ["user:yousef@felidai.com"]
    notification_email         = "yousef@felidai.com"
  }

  expect_failures = [var.policy_admin_lease_expiry]
}

run "policy_admin_purposes_are_mutually_exclusive" {
  command = plan

  variables {
    enable_control_project_apis                         = true
    enable_organization_guardrails                      = true
    organization_guardrail_effective                    = true
    enable_organization_policy_admin_lease              = true
    enable_project_policy_admin_lease                   = true
    policy_admin_lease_expiry                           = timeadd(timestamp(), "2h")
    github_infrastructure_environments_reviewed         = true
    github_operations_environments_reviewed             = true
    enable_production_project_creation                  = true
    enable_production_project_bootstrap                 = true
    production_project_creation_verified                = true
    enable_temporary_human_state_bucket_policy_access   = true
    temporary_human_state_bucket_policy_access_expiry   = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified = true
    enable_state_bucket_iam_reconciliation              = true
    project_id                                          = "seen-registry-prod-476219"
    organization_id                                     = "567958019562"
    billing_account_id                                  = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                          = ["user:yousef@felidai.com"]
    notification_email                                  = "yousef@felidai.com"
  }

  expect_failures = [
    var.policy_admin_lease_expiry,
    terraform_data.bootstrap_phase_contract,
  ]
}

run "enabled_human_bootstrap_has_hardened_identity_contract" {
  command = plan

  variables {
    enable_control_project_apis                         = true
    enable_organization_guardrails                      = true
    organization_guardrail_effective                    = true
    github_infrastructure_environments_reviewed         = true
    github_operations_environments_reviewed             = true
    enable_production_project_creation                  = true
    enable_production_project_bootstrap                 = true
    production_project_creation_verified                = true
    enable_project_policy_admin_lease                   = true
    policy_admin_lease_expiry                           = timeadd(timestamp(), "2h")
    enable_temporary_human_state_bucket_policy_access   = true
    temporary_human_state_bucket_policy_access_expiry   = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified = true
    enable_state_bucket_iam_reconciliation              = true
    project_id                                          = "seen-registry-prod-476219"
    organization_id                                     = "567958019562"
    billing_account_id                                  = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                          = ["user:yousef@felidai.com"]
    notification_email                                  = "yousef@felidai.com"
  }

  assert {
    condition = (
      google_project.production[0].project_id == "seen-registry-prod-476219" &&
      google_project.production[0].org_id == "567958019562" &&
      google_project.production[0].billing_account == "ABCDEF-123456-ABCDEF" &&
      google_project.production[0].auto_create_network == false &&
      google_project.production[0].deletion_policy == "PREVENT" &&
      output.organization_guardrail_effective &&
      output.active_bootstrap_identity == "yousef@felidai.com" &&
      length(data.google_project.production_verified) == 1 &&
      data.google_project.production_verified[0].project_id == "seen-registry-prod-476219" &&
      data.google_project.production_verified[0].number == "123456789012" &&
      data.google_project.production_verified[0].org_id == "567958019562" &&
      data.google_project.production_verified[0].billing_account == "ABCDEF-123456-ABCDEF" &&
      strcontains(file("${path.root}/versions.tf"), "provider \"google\" {\n  alias                 = \"bootstrap_identity\"\n  user_project_override = false\n}") &&
      strcontains(file("${path.root}/main.tf"), "provider = google.bootstrap_identity") &&
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].org_id == "567958019562" &&
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].role == "roles/orgpolicy.policyAdmin" &&
      length(split("request.time < timestamp('$${var.policy_admin_lease_expiry}')", file("${path.root}/main.tf"))) == 3 &&
      startswith(
        google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].condition[0].expression,
        "resource.matchTag('567958019562/seen-registry-policy-scope', 'production') && ",
      ) &&
      can(regex(
        "^request\\.time < timestamp\\('[^']+'\\)$",
        trimprefix(
          google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].condition[0].expression,
          "resource.matchTag('567958019562/seen-registry-policy-scope', 'production') && ",
        ),
      )) &&
      !strcontains(file("${path.root}/main.tf"), "resource \"google_project_iam_member\" \"policy_admin_lease\"") &&
      length(google_project_iam_member.project_creator_owner) == 0
    )
    error_message = "The production project must be isolated, protected, and managed by the reviewed human through the identity-only provider before handoff."
  }

  assert {
    condition = (
      length(google_tags_tag_key.project_policy_scope) == 1 &&
      google_tags_tag_key.project_policy_scope[0].parent == "organizations/567958019562" &&
      google_tags_tag_key.project_policy_scope[0].short_name == "seen-registry-policy-scope" &&
      length(google_tags_tag_value.project_policy_scope) == 1 &&
      google_tags_tag_value.project_policy_scope[0].parent == google_tags_tag_key.project_policy_scope[0].id &&
      google_tags_tag_value.project_policy_scope[0].short_name == "production" &&
      length(google_tags_tag_binding.project_policy_scope) == 1 &&
      google_tags_tag_binding.project_policy_scope[0].parent == "//cloudresourcemanager.googleapis.com/projects/123456789012" &&
      google_tags_tag_binding.project_policy_scope[0].tag_value == google_tags_tag_value.project_policy_scope[0].id &&
      strcontains(file("${path.root}/main.tf"), "depends_on = [google_tags_tag_binding.project_policy_scope]") &&
      strcontains(file("${path.root}/main.tf"), "description = \"Permanently scopes delegated production-project Organization Policy administration\"\n\n  lifecycle {\n    prevent_destroy = true\n  }") &&
      strcontains(file("${path.root}/main.tf"), "description = \"Exact Seen registry production project policy-administration scope\"\n\n  lifecycle {\n    prevent_destroy = true\n  }") &&
      strcontains(file("${path.root}/main.tf"), "tag_value = google_tags_tag_value.project_policy_scope[0].id\n\n  lifecycle {\n    prevent_destroy = true\n  }")
    )
    error_message = "The project-policy lease must be permanently scoped by one exact organization tag key, value, and numeric-project binding."
  }

  assert {
    condition = (
      output.enabled_bootstrap_services == toset([
        "cloudresourcemanager.googleapis.com",
        "iam.googleapis.com",
        "iamcredentials.googleapis.com",
        "monitoring.googleapis.com",
        "orgpolicy.googleapis.com",
        "serviceusage.googleapis.com",
        "sts.googleapis.com",
        "storage.googleapis.com",
      ]) &&
      !contains(output.enabled_bootstrap_services, "compute.googleapis.com") &&
      google_org_policy_policy.automatic_default_service_account_grants[0].spec[0].rules[0].enforce == "TRUE" &&
      output.automatic_default_service_account_grants_policy == "projects/123456789012/policies/iam.automaticIamGrantsForDefaultServiceAccounts" &&
      !output.automatic_default_service_account_grants_policy_effective
    )
    error_message = "Bootstrap must own exactly its control APIs and enforce automatic-grant suppression before Compute can be enabled downstream."
  }

  assert {
    condition = (
      toset(keys(google_service_account.infrastructure)) == toset(["plan", "apply", "materials", "jobs"]) &&
      google_service_account.infrastructure["plan"].account_id == "seen-registry-prod-plan" &&
      google_service_account.infrastructure["apply"].account_id == "seen-registry-prod-iac" &&
      google_service_account.infrastructure["materials"].account_id == "seen-registry-prod-materials" &&
      google_service_account.infrastructure["jobs"].account_id == "seen-registry-prod-job-runner" &&
      google_iam_workload_identity_pool.infrastructure[0].workload_identity_pool_id == "seen-registry-prod-infra" &&
      google_iam_workload_identity_pool.infrastructure[0].disabled == false &&
      toset(keys(google_iam_workload_identity_pool_provider.infrastructure)) == toset(["plan", "apply", "materials", "jobs"]) &&
      google_iam_workload_identity_pool_provider.infrastructure["plan"].workload_identity_pool_provider_id == "infra-plan" &&
      google_iam_workload_identity_pool_provider.infrastructure["apply"].workload_identity_pool_provider_id == "infra-apply" &&
      google_iam_workload_identity_pool_provider.infrastructure["materials"].workload_identity_pool_provider_id == "material-ops" &&
      google_iam_workload_identity_pool_provider.infrastructure["jobs"].workload_identity_pool_provider_id == "job-ops" &&
      toset(keys(output.infrastructure_workload_identity_providers)) == toset(["plan", "apply"]) &&
      toset(keys(output.operations_workload_identity_providers)) == toset(["materials", "jobs"]) &&
      output.job_operations_viewer_role == "projects/seen-registry-prod-476219/roles/seenRegistryJobViewer"
    )
    error_message = "Bootstrap must create exact separate plan/apply/materials/jobs service accounts and providers in one dedicated pool."
  }

  assert {
    condition = alltrue([
      for identity, provider in google_iam_workload_identity_pool_provider.infrastructure :
      provider.oidc[0].issuer_uri == "https://token.actions.githubusercontent.com" &&
      provider.attribute_mapping == tomap({
        "google.subject"                = "'repo:codeyousef/summon-portfolio:environment:seen-registry-production-${identity}'"
        "attribute.environment"         = "assertion.environment"
        "attribute.event_name"          = "assertion.event_name"
        "attribute.ref"                 = "assertion.ref"
        "attribute.ref_type"            = "assertion.ref_type"
        "attribute.repository"          = "assertion.repository"
        "attribute.repository_id"       = "assertion.repository_id"
        "attribute.repository_owner_id" = "assertion.repository_owner_id"
        "attribute.sha"                 = "assertion.sha"
        "attribute.workflow_ref"        = "assertion.workflow_ref"
        "attribute.workflow_sha"        = "assertion.workflow_sha"
      }) &&
      provider.attribute_condition == join(" && ", [
        "assertion.sub == 'repo:codeyousef/summon-portfolio:environment:seen-registry-production-${identity}'",
        "assertion.repository == 'codeyousef/summon-portfolio'",
        "assertion.repository_id == '1091564909'",
        "assertion.repository_owner_id == '10247142'",
        "assertion.ref == 'refs/heads/master'",
        "assertion.ref_type == 'branch'",
        "assertion.workflow_ref == '${contains(["materials", "jobs"], identity) ? "codeyousef/summon-portfolio/.github/workflows/operate-seen-registry-production.yml@refs/heads/master" : "codeyousef/summon-portfolio/.github/workflows/deploy-seen-registry-infrastructure.yml@refs/heads/master"}'",
        "assertion.event_name == 'workflow_dispatch'",
        "assertion.environment == 'seen-registry-production-${identity}'",
        "assertion.workflow_sha == assertion.sha",
      ])
    ])
    error_message = "Each OIDC provider must map and condition every immutable repository, workflow, branch, environment, event, and commit claim."
  }

  assert {
    condition     = length(google_service_account_iam_member.infrastructure_oidc) == 0
    error_message = "Infrastructure federation grants must wait until the legacy DRS gateway exception is verified effective."
  }

  assert {
    condition = (
      google_project_iam_custom_role.infrastructure_plan[0].permissions == local.infrastructure_plan_permissions &&
      google_project_iam_custom_role.infrastructure_apply[0].permissions == local.infrastructure_apply_permissions &&
      toset([
        for permission in local.infrastructure_plan_permissions : permission
        if startswith(permission, "monitoring.")
        ]) == toset([
        "monitoring.alertPolicies.get",
        "monitoring.notificationChannels.get",
        "monitoring.notificationChannels.list",
        "monitoring.uptimeCheckConfigs.get",
      ]) &&
      !strcontains(file("${path.root}/main.tf"), "roles/monitoring.viewer") &&
      !contains(local.infrastructure_apply_permissions, "monitoring.notificationChannels.create") &&
      !contains(local.infrastructure_apply_permissions, "monitoring.notificationChannels.delete") &&
      !contains(local.infrastructure_apply_permissions, "monitoring.notificationChannels.update") &&
      !contains(local.infrastructure_apply_permissions, "secretmanager.versions.access") &&
      !contains(local.infrastructure_apply_permissions, "storage.objects.create") &&
      !contains(local.infrastructure_apply_permissions, "run.routes.invoke") &&
      !contains(local.infrastructure_apply_permissions, "run.jobs.setIamPolicy") &&
      !contains(local.infrastructure_apply_permissions, "cloudkms.cryptoKeyVersions.destroy") &&
      !contains(local.infrastructure_apply_permissions, "cloudkms.cryptoKeyVersions.useToSign") &&
      !contains(local.infrastructure_apply_permissions, "artifactregistry.repositories.setIamPolicy") &&
      !contains(local.infrastructure_apply_permissions, "cloudkms.cryptoKeys.setIamPolicy") &&
      !contains(local.infrastructure_apply_permissions, "compute.networks.updatePolicy") &&
      !contains(local.infrastructure_apply_permissions, "iam.serviceAccounts.setIamPolicy") &&
      !contains(local.infrastructure_apply_permissions, "resourcemanager.projects.setIamPolicy") &&
      !contains(local.infrastructure_apply_permissions, "run.services.setIamPolicy") &&
      !contains(local.infrastructure_apply_permissions, "secretmanager.secrets.setIamPolicy") &&
      !contains(local.infrastructure_apply_permissions, "storage.buckets.setIamPolicy") &&
      !contains(local.infrastructure_apply_permissions, "iam.serviceAccounts.create") &&
      !contains(local.infrastructure_apply_permissions, "iam.serviceAccounts.update") &&
      !contains(local.infrastructure_apply_permissions, "serviceusage.services.enable") &&
      google_project_iam_member.infrastructure_plan[0].role == "projects/seen-registry-prod-476219/roles/seenRegistryInfrastructurePlan" &&
      google_project_iam_member.infrastructure_apply[0].role == "projects/seen-registry-prod-476219/roles/seenRegistryInfrastructureApply" &&
      length(google_project_iam_member.infrastructure_apply[0].condition) == 0 &&
      google_project_iam_custom_role.project_iam_apply[0].permissions == toset(["resourcemanager.projects.setIamPolicy"]) &&
      google_project_iam_member.infrastructure_project_iam[0].condition[0].expression == "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/datastore.user', 'roles/datastore.viewer'])" &&
      !strcontains(file("${path.root}/main.tf"), ".size()") &&
      !strcontains(file("${path.root}/main.tf"), ".size(") &&
      length(split("modifiedGrantsByRole", file("${path.root}/main.tf"))) == 8 &&
      length(split(".hasOnly(", file("${path.root}/main.tf"))) == 8 &&
      alltrue([
        for name, role in google_project_iam_custom_role.resource_iam_setter :
        role.permissions == toset(local.resource_iam_setter_roles[name].permissions) &&
        length(role.permissions) == 1
      ]) &&
      google_project_iam_custom_role.resource_iam_setter["storage"].permissions == toset(["storage.buckets.setIamPolicy"]) &&
      google_project_iam_custom_role.state_reader[0].permissions == toset([
        "storage.buckets.get",
        "storage.objects.get",
        "storage.objects.list",
      ]) &&
      google_project_iam_custom_role.state_bucket_policy_reader[0].permissions == toset([
        "storage.buckets.get",
        "storage.buckets.getIamPolicy",
      ]) &&
      toset(keys(google_project_iam_custom_role.resource_iam_setter)) == toset(["kms", "run_job", "run_service", "secret", "storage"])
    )
    error_message = "CI identities must use least-privilege custom roles, supported single-hasOnly IAM conditions, exact monitoring reads, and separate get-only recovery and set-only storage-policy roles."
  }

  assert {
    condition = (
      1 +
      length(google_project_iam_member.infrastructure_kms_policy_setter) +
      length(google_project_iam_member.infrastructure_run_policy_setter) +
      length(google_project_iam_member.infrastructure_secret_policy_setter) +
      length(google_project_iam_member.infrastructure_registry_storage_policy_setter) +
      length(google_project_iam_member.infrastructure_state_storage_policy_setter) == 27 &&
      length(google_project_iam_member.infrastructure_project_iam[0].condition) == 1 &&
      alltrue([
        for binding in concat(
          values(google_project_iam_member.infrastructure_kms_policy_setter),
          values(google_project_iam_member.infrastructure_run_policy_setter),
          values(google_project_iam_member.infrastructure_secret_policy_setter),
          values(google_project_iam_member.infrastructure_registry_storage_policy_setter),
          values(google_project_iam_member.infrastructure_state_storage_policy_setter),
        ) :
        length(binding.condition) == 1 &&
        length(binding.condition[0].expression) > 0 &&
        !strcontains(binding.condition[0].expression, ".size(")
      ])
    )
    error_message = "All 27 steady IAM-policy setter grants must retain one non-empty supported condition."
  }

  assert {
    condition = alltrue([
      for forbidden in [
        "allUsers",
        "allAuthenticatedUsers",
        "domain:",
        "group:",
        "roles/billing.admin",
        "roles/billing.creator",
        "roles/billing.user",
        "roles/iam.serviceAccountTokenCreator",
        "roles/resourcemanager.projectIamAdmin",
        "roles/storage.admin",
        "roles/storage.objectAdmin",
      ] : !strcontains(file("${path.root}/main.tf"), forbidden)
    ])
    error_message = "Bootstrap source must reject public, group/domain, and broad predefined administration grants."
  }

  assert {
    condition = (
      toset([for binding in values(google_organization_iam_member.infrastructure_read) : binding.role]) == toset([
        "organizations/567958019562/roles/seenRegistryBootstrapRefresh",
        "roles/orgpolicy.policyViewer",
      ]) &&
      google_organization_iam_custom_role.bootstrap_refresh[0].permissions == toset([
        "iam.roles.get",
        "resourcemanager.organizations.get",
        "resourcemanager.organizations.getIamPolicy",
        "resourcemanager.hierarchyNodes.listTagBindings",
        "resourcemanager.tagKeys.get",
        "resourcemanager.tagValues.get",
      ]) &&
      google_organization_iam_custom_role.billing_refresh[0].permissions == toset([
        "billing.accounts.getIamPolicy",
        "billing.budgets.get",
        "billing.resourceAssociations.list",
      ]) &&
      google_organization_iam_custom_role.control_project_refresh[0].permissions == toset([
        "resourcemanager.projects.get",
        "resourcemanager.projects.getIamPolicy",
      ]) &&
      google_project_iam_custom_role.control_project_refresh[0].project == "portfolio-476219" &&
      google_project_iam_custom_role.control_project_refresh[0].role_id == "seenRegistryControlProjectRefresh" &&
      google_project_iam_custom_role.control_project_refresh[0].permissions == toset([
        "resourcemanager.projects.get",
        "resourcemanager.projects.getIamPolicy",
      ]) &&
      output.billing_account_refresh_role == "roles/billing.viewer" &&
      output.control_project_refresh_role == "projects/portfolio-476219/roles/seenRegistryControlProjectRefresh" &&
      length(google_organization_iam_member.infrastructure_read) == 4 &&
      toset([for binding in values(google_organization_iam_member.infrastructure_read) : binding.member]) == toset([
        "serviceAccount:seen-registry-prod-plan@seen-registry-prod-476219.iam.gserviceaccount.com",
        "serviceAccount:seen-registry-prod-iac@seen-registry-prod-476219.iam.gserviceaccount.com",
      ]) &&
      toset(keys(google_billing_account_iam_member.infrastructure_read)) == toset(["plan", "apply"]) &&
      alltrue([
        for binding in values(google_billing_account_iam_member.infrastructure_read) :
        binding.billing_account_id == "ABCDEF-123456-ABCDEF" &&
        binding.role == "roles/billing.viewer"
      ]) &&
      toset([for binding in values(google_billing_account_iam_member.infrastructure_read) : binding.member]) == toset([
        "serviceAccount:seen-registry-prod-plan@seen-registry-prod-476219.iam.gserviceaccount.com",
        "serviceAccount:seen-registry-prod-iac@seen-registry-prod-476219.iam.gserviceaccount.com",
      ]) &&
      length(google_project_iam_member.infrastructure_control_project) == 6 &&
      toset([for binding in values(google_project_iam_member.infrastructure_control_project) : binding.role]) == toset([
        "projects/portfolio-476219/roles/seenRegistryControlProjectRefresh",
        "roles/serviceusage.serviceUsageConsumer",
        "roles/serviceusage.serviceUsageViewer",
      ]) &&
      alltrue([
        for binding in values(google_billing_account_iam_member.infrastructure_read) :
        binding.role != "organizations/567958019562/roles/seenRegistryBillingRefresh"
      ]) &&
      alltrue([
        for binding in values(google_project_iam_member.infrastructure_control_project) :
        binding.role != "organizations/567958019562/roles/seenRegistryControlProjectRefresh"
      ]) &&
      toset([for binding in values(google_project_iam_member.infrastructure_control_project) : binding.member]) == toset([
        "serviceAccount:seen-registry-prod-plan@seen-registry-prod-476219.iam.gserviceaccount.com",
        "serviceAccount:seen-registry-prod-iac@seen-registry-prod-476219.iam.gserviceaccount.com",
      ])
    )
    error_message = "Both CI identities need grantable read-only billing access, a control-project-local refresh role, and no binding to either retained cross-hierarchy organization role."
  }

  assert {
    condition = (
      output.bootstrap_state_backend.bucket == "seen-registry-prod-476219-bootstrap-tofu-state" &&
      output.bootstrap_state_backend.prefix == "seen-registry/bootstrap/prod" &&
      output.production_state_backend.bucket == "seen-registry-prod-476219-prod-tofu-state" &&
      output.production_state_backend.prefix == "seen-registry/prod" &&
      output.bootstrap_state_backend.bucket != output.production_state_backend.bucket &&
      google_storage_bucket.state["bootstrap"].name == output.bootstrap_state_backend.bucket &&
      google_storage_bucket.state["production"].name == output.production_state_backend.bucket
    )
    error_message = "Bootstrap and production roots must use distinct dedicated buckets and exact prefixes."
  }

  assert {
    condition = alltrue([
      for bucket in values(google_storage_bucket.state) :
      bucket.project == "seen-registry-prod-476219" &&
      bucket.uniform_bucket_level_access == true &&
      bucket.public_access_prevention == "enforced" &&
      bucket.force_destroy == false &&
      bucket.versioning[0].enabled == true &&
      bucket.soft_delete_policy[0].retention_duration_seconds == 2592000 &&
      length(bucket.retention_policy) == 0
    ])
    error_message = "Each state bucket must be non-public, versioned, soft-deleted, and free of lock-breaking retention."
  }

  assert {
    condition = (
      alltrue([
        for name, policy in data.google_iam_policy.state :
        toset([for binding in policy.binding : binding.role]) == toset([
          "projects/seen-registry-prod-476219/roles/seenRegistryStateReader",
          "projects/seen-registry-prod-476219/roles/seenRegistryStateLocker",
          "projects/seen-registry-prod-476219/roles/seenRegistryStateWriter",
        ]) &&
        alltrue(flatten([
          for binding in policy.binding : [
            for member in binding.members : !startswith(member, "user:")
          ]
        ])) &&
        toset(one([for binding in policy.binding : binding if endswith(binding.role, "/seenRegistryStateReader")]).members) == toset([
          "serviceAccount:seen-registry-prod-plan@seen-registry-prod-476219.iam.gserviceaccount.com",
          "serviceAccount:seen-registry-prod-iac@seen-registry-prod-476219.iam.gserviceaccount.com",
        ]) &&
        toset(one([for binding in policy.binding : binding if endswith(binding.role, "/seenRegistryStateLocker")]).members) == toset([
          "serviceAccount:seen-registry-prod-plan@seen-registry-prod-476219.iam.gserviceaccount.com",
        ]) &&
        toset(one([for binding in policy.binding : binding if endswith(binding.role, "/seenRegistryStateWriter")]).members) == toset([
          "serviceAccount:seen-registry-prod-iac@seen-registry-prod-476219.iam.gserviceaccount.com",
        ]) &&
        alltrue([
          for binding in policy.binding :
          !startswith(binding.role, "roles/storage.legacy") && binding.role != "roles/storage.objectAdmin"
        ])
      ]) &&
      length(google_storage_bucket_iam_policy.state) == 2
    )
    error_message = "Authoritative bucket policies must contain only exact custom state roles and no human or legacy convenience binding."
  }

  assert {
    condition = alltrue([
      for name, policy in data.google_iam_policy.state :
      length(one([for binding in policy.binding : binding if endswith(binding.role, "/seenRegistryStateReader")]).condition) == 0 &&
      one([for binding in policy.binding : binding if endswith(binding.role, "/seenRegistryStateLocker")]).condition[0].expression == "resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}/objects/${local.state_backends[name].prefix}/default.tflock'" &&
      strcontains(one([for binding in policy.binding : binding if endswith(binding.role, "/seenRegistryStateWriter")]).condition[0].expression, "projects/_/buckets/${local.state_backends[name].bucket}/objects/${local.state_backends[name].prefix}/default.tfstate") &&
      strcontains(one([for binding in policy.binding : binding if endswith(binding.role, "/seenRegistryStateWriter")]).condition[0].expression, "projects/_/buckets/${local.state_backends[name].bucket}/objects/${local.state_backends[name].prefix}/default.tflock")
    ])
    error_message = "Plan locking and apply mutation must be conditioned to each root's exact state objects."
  }

  assert {
    condition = (
      strcontains(
        file("${path.root}/main.tf"),
        "expression  = \"resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/$${each.value.bucket}' && request.time < timestamp('$${var.temporary_human_state_bucket_policy_access_expiry}')\"",
      ) &&
      strcontains(
        file("${path.root}/main.tf"),
        "expression = format(\n      \"resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s && request.time < timestamp('%s')\",\n      each.value.bucket,\n      local.state_storage_modified_roles_condition,\n      var.temporary_human_state_bucket_policy_access_expiry,\n    )",
      ) &&
      toset(keys(google_project_iam_member.temporary_human_state_bucket_policy_read_access)) == toset(["bootstrap", "production"]) &&
      alltrue([
        for name, binding in google_project_iam_member.temporary_human_state_bucket_policy_read_access :
        binding.project == "seen-registry-prod-476219" &&
        binding.role == "projects/seen-registry-prod-476219/roles/seenRegistryStateBucketPolicyReader" &&
        binding.member == "user:yousef@felidai.com" &&
        startswith(
          binding.condition[0].expression,
          "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ",
        ) &&
        can(regex(
          "^request\\.time < timestamp\\('[^']+'\\)$",
          trimprefix(
            binding.condition[0].expression,
            "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ",
          ),
        ))
      ]) &&
      toset(keys(google_project_iam_member.temporary_human_state_bucket_policy_access)) == toset(["bootstrap", "production"]) &&
      alltrue([
        for name, binding in google_project_iam_member.temporary_human_state_bucket_policy_access :
        binding.project == "seen-registry-prod-476219" &&
        binding.role == "projects/seen-registry-prod-476219/roles/seenRegistryStorageIamApply" &&
        binding.member == "user:yousef@felidai.com" &&
        startswith(
          binding.condition[0].expression,
          "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ${local.state_storage_modified_roles_condition} && ",
        ) &&
        can(regex(
          "^request\\.time < timestamp\\('[^']+'\\)$",
          trimprefix(
            binding.condition[0].expression,
            "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ${local.state_storage_modified_roles_condition} && ",
          ),
        )) &&
        trimprefix(
          binding.condition[0].expression,
          "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ${local.state_storage_modified_roles_condition} && ",
          ) == trimprefix(
          google_project_iam_member.temporary_human_state_bucket_policy_read_access[name].condition[0].expression,
          "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ",
        ) &&
        !strcontains(binding.condition[0].expression, ".size(")
      ]) &&
      var.enable_temporary_human_state_bucket_policy_access &&
      var.temporary_human_state_bucket_policy_access_expiry != null &&
      var.temporary_human_state_bucket_policy_access_verified
    )
    error_message = "Recovery access must give only the reviewed operator separate exact-bucket get-only and set-only roles, with mutation constrained to the three exact state roles."
  }

  assert {
    condition = (
      toset(jsondecode(google_org_policy_policy.managed_allowed_policy_members[0].spec[0].rules[0].parameters).allowedMemberSubjects) == toset([
        "principal://iam.googleapis.com/projects/123456789012/locations/global/workloadIdentityPools/seen-registry-prod-infra/subject/repo:codeyousef/summon-portfolio:environment:seen-registry-production-apply",
        "principal://iam.googleapis.com/projects/123456789012/locations/global/workloadIdentityPools/seen-registry-prod-infra/subject/repo:codeyousef/summon-portfolio:environment:seen-registry-production-jobs",
        "principal://iam.googleapis.com/projects/123456789012/locations/global/workloadIdentityPools/seen-registry-prod-infra/subject/repo:codeyousef/summon-portfolio:environment:seen-registry-production-materials",
        "principal://iam.googleapis.com/projects/123456789012/locations/global/workloadIdentityPools/seen-registry-prod-infra/subject/repo:codeyousef/summon-portfolio:environment:seen-registry-production-plan",
        "serviceAccount:portfolio-prod-runtime@portfolio-476219.iam.gserviceaccount.com",
        "user:yousef@felidai.com",
      ]) &&
      jsondecode(google_org_policy_policy.managed_allowed_policy_members[0].spec[0].rules[0].parameters).allowedPrincipalSets == [
        "//cloudresourcemanager.googleapis.com/organizations/567958019562",
      ] &&
      length(google_org_policy_policy.legacy_domain_restriction_override) == 0
    )
    error_message = "Managed member policy must allow only stable runtime/OIDC subjects, the exact reviewed operator as grant-eligible (not granted), and the organization principal set."
  }

  assert {
    condition = (
      google_monitoring_notification_channel.operations[0].type == "email" &&
      google_monitoring_notification_channel.operations[0].labels.email_address == "yousef@felidai.com" &&
      google_monitoring_notification_channel.operations[0].enabled == true &&
      google_monitoring_notification_channel.operations[0].force_delete == false &&
      toset([for log in google_project_iam_audit_config.storage[0].audit_log_config : log.log_type]) == toset(["DATA_READ", "DATA_WRITE"])
    )
    error_message = "The foundation must create the reviewed alert destination and audit all state reads and writes."
  }
}

run "temporary_human_state_access_covers_both_exact_roots" {
  command = plan

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    managed_member_policy_effective                           = true
    automatic_default_service_account_grants_policy_effective = true
    enable_temporary_human_state_bucket_policy_access         = true
    temporary_human_state_bucket_policy_access_expiry         = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified       = true
    enable_state_bucket_iam_reconciliation                    = true
    enable_temporary_human_state_migration_access             = true
    temporary_human_state_migration_access_expiry             = timeadd(timestamp(), "12h")
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  assert {
    condition = (
      alltrue(flatten([
        for policy in values(data.google_iam_policy.state) : [
          length([
            for binding in policy.binding : binding
            if endswith(binding.role, "/seenRegistryStateReader") && contains(binding.members, "user:yousef@felidai.com")
          ]) == 1,
          length([
            for binding in policy.binding : binding
            if endswith(binding.role, "/seenRegistryStateWriter") && contains(binding.members, "user:yousef@felidai.com")
          ]) == 1,
          alltrue([
            for binding in policy.binding : !contains(binding.members, "user:yousef@felidai.com")
            if endswith(binding.role, "/seenRegistryStateLocker")
          ]),
        ]
      ])) &&
      output.temporary_human_state_migration_access_enabled &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_read_access) == 2 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_access) == 2 &&
      output.temporary_human_state_bucket_policy_access_enabled &&
      output.temporary_human_state_bucket_policy_access_verified
    )
    error_message = "The temporary human must have bucket reader plus exact state-and-lock writer access on both dedicated roots, without the named locker role or bucket-policy authority."
  }

  assert {
    condition = (
      length(split("request.time < timestamp('$${var.temporary_human_state_migration_access_expiry}')", file("${path.root}/main.tf"))) == 3 &&
      alltrue([
        for name, policy in data.google_iam_policy.state :
        can(regex("^request\\.time < timestamp\\('[^']+'\\)$", one([
          for binding in policy.binding : binding
          if endswith(binding.role, "/seenRegistryStateReader") && contains(binding.members, "user:yousef@felidai.com")
        ]).condition[0].expression)) &&
        one([
          for binding in policy.binding : binding
          if endswith(binding.role, "/seenRegistryStateWriter") && contains(binding.members, "user:yousef@felidai.com")
          ]).condition[0].expression == format(
          "(resource.name == '${local.state_backend_resources[name].state_resource}' || resource.name == '${local.state_backend_resources[name].lock_resource}') && %s",
          one([
            for binding in policy.binding : binding
            if endswith(binding.role, "/seenRegistryStateReader") && contains(binding.members, "user:yousef@felidai.com")
          ]).condition[0].expression,
        )
      ])
    )
    error_message = "Temporary migration IAM conditions must use the supplied expiry and each root's exact state and lock objects."
  }
}

run "next_project_bootstrap_phase_is_exact" {
  command = plan

  variables {
    enable_control_project_apis                         = true
    enable_organization_guardrails                      = true
    organization_guardrail_effective                    = true
    github_infrastructure_environments_reviewed         = true
    github_operations_environments_reviewed             = true
    enable_production_project_creation                  = true
    enable_production_project_bootstrap                 = true
    production_project_creation_verified                = true
    enable_project_policy_admin_lease                   = true
    policy_admin_lease_expiry                           = timeadd(timestamp(), "2h")
    enable_temporary_human_state_bucket_policy_access   = true
    temporary_human_state_bucket_policy_access_expiry   = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified = true
    enable_state_bucket_iam_reconciliation              = true
    enable_temporary_human_state_migration_access       = true
    temporary_human_state_migration_access_expiry       = timeadd(timestamp(), "12h")
    project_id                                          = "seen-registry-prod-476219"
    organization_id                                     = "567958019562"
    billing_account_id                                  = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                          = ["user:yousef@felidai.com"]
    notification_email                                  = "yousef@felidai.com"
  }

  assert {
    condition = (
      toset(keys(google_organization_iam_member.policy_admin_lease)) == toset(["user:yousef@felidai.com"]) &&
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].org_id == "567958019562" &&
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].role == "roles/orgpolicy.policyAdmin" &&
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].member == "user:yousef@felidai.com" &&
      length(split("request.time < timestamp('$${var.policy_admin_lease_expiry}')", file("${path.root}/main.tf"))) == 3 &&
      startswith(
        google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].condition[0].expression,
        "resource.matchTag('567958019562/seen-registry-policy-scope', 'production') && ",
      ) &&
      can(regex(
        "^request\\.time < timestamp\\('[^']+'\\)$",
        trimprefix(
          google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].condition[0].expression,
          "resource.matchTag('567958019562/seen-registry-policy-scope', 'production') && ",
        ),
      )) &&
      !strcontains(file("${path.root}/main.tf"), "resource \"google_project_iam_member\" \"policy_admin_lease\"")
    )
    error_message = "The project-policy phase must grant exactly one time-bounded organization-scoped Policy Admin lease to the reviewed human because that predefined role is not project-grantable."
  }

  assert {
    condition = (
      toset(keys(data.google_iam_policy.state)) == toset(["bootstrap", "production"]) &&
      alltrue([
        for policy in values(data.google_iam_policy.state) :
        length([
          for binding in policy.binding : binding
          if endswith(binding.role, "/seenRegistryStateReader") && contains(binding.members, "user:yousef@felidai.com")
          ]) == 1 && length([
          for binding in policy.binding : binding
          if endswith(binding.role, "/seenRegistryStateWriter") && contains(binding.members, "user:yousef@felidai.com")
          ]) == 1 && alltrue([
          for binding in policy.binding : !contains(binding.members, "user:yousef@felidai.com")
          if endswith(binding.role, "/seenRegistryStateLocker")
        ])
      ])
    )
    error_message = "The next project-bootstrap phase must give the reviewed human reader and writer membership, but no lock membership, on both exact state policies."
  }

  assert {
    condition = (
      !var.enable_organization_policy_admin_lease &&
      var.enable_project_policy_admin_lease &&
      var.policy_admin_lease_expiry != null &&
      var.enable_temporary_human_state_bucket_policy_access &&
      var.temporary_human_state_bucket_policy_access_expiry != null &&
      var.temporary_human_state_bucket_policy_access_verified &&
      !var.project_executor_handoff_complete &&
      !var.production_foundation_applied &&
      !var.production_image_publisher_foundation_applied &&
      var.project_creator_owner_member == null &&
      !var.adopt_project_creator_owner &&
      !var.approve_project_creator_owner_removal &&
      !var.project_creator_owner_removed &&
      !var.enable_portfolio_gateway_exception &&
      !var.portfolio_gateway_exception_effective &&
      !var.managed_member_policy_effective &&
      !var.automatic_default_service_account_grants_policy_effective &&
      length(google_service_account_iam_member.infrastructure_oidc) == 0 &&
      length(google_org_policy_policy.legacy_domain_restriction_override) == 0 &&
      length(google_project_iam_member.project_creator_owner) == 0
    )
    error_message = "The next project-bootstrap phase must leave every later handoff, foundation, policy exception, OIDC trust, and Owner-management gate inactive."
  }
}

run "production_foundation_installs_exact_resource_policy_setters" {
  command = plan

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    enable_temporary_human_state_bucket_policy_access         = true
    temporary_human_state_bucket_policy_access_expiry         = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified       = true
    enable_state_bucket_iam_reconciliation                    = true
    production_foundation_applied                             = true
    production_image_publisher_foundation_applied             = true
    managed_member_policy_effective                           = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    automatic_default_service_account_grants_policy_effective = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  assert {
    condition = (
      output.production_foundation_applied &&
      output.production_image_publisher_foundation_applied &&
      output.automatic_default_service_account_grants_policy_effective &&
      output.portfolio_gateway_exception_effective &&
      length(google_organization_iam_member.policy_admin_lease) == 0 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_read_access) == 2 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_access) == 2 &&
      output.temporary_human_state_bucket_policy_access_enabled &&
      output.temporary_human_state_bucket_policy_access_verified &&
      toset(keys(google_service_account_iam_member.infrastructure_oidc)) == toset(["plan", "apply", "materials", "jobs"]) &&
      alltrue([
        for identity, binding in google_service_account_iam_member.infrastructure_oidc :
        binding.role == "roles/iam.workloadIdentityUser" &&
        binding.member == "principal://iam.googleapis.com/projects/123456789012/locations/global/workloadIdentityPools/seen-registry-prod-infra/subject/repo:codeyousef/summon-portfolio:environment:seen-registry-production-${identity}"
      ]) &&
      toset(keys(google_project_iam_custom_role.resource_iam_setter)) == toset(["kms", "run_job", "run_service", "secret", "storage"]) &&
      google_project_iam_custom_role.resource_iam_setter["run_job"].permissions == toset(["run.jobs.setIamPolicy"]) &&
      !strcontains(file("${path.root}/main.tf"), "artifactregistry.repositories.setIamPolicy") &&
      !strcontains(file("${path.root}/main.tf"), "iam.serviceAccounts.setIamPolicy")
    )
    error_message = "Artifact Registry and service-account IAM must be fully pre-provisioned before handoff and expose no steady setter permission."
  }

  assert {
    condition = (
      google_project_iam_custom_role.material_key_versions[0].permissions == toset([
        "cloudkms.cryptoKeyVersions.create",
        "cloudkms.cryptoKeyVersions.get",
        "cloudkms.cryptoKeyVersions.list",
      ]) &&
      google_project_iam_custom_role.material_secret_versions[0].permissions == toset([
        "secretmanager.secrets.get",
        "secretmanager.versions.add",
        "secretmanager.versions.get",
        "secretmanager.versions.list",
      ]) &&
      google_project_iam_custom_role.job_operations_viewer[0].permissions == toset(["run.jobs.get"]) &&
      output.job_operations_viewer_role == "projects/seen-registry-prod-476219/roles/seenRegistryJobViewer" &&
      toset(keys(google_project_iam_member.material_key_versions)) == local.production_kms_keys &&
      toset(keys(google_project_iam_member.material_secret_versions)) == local.production_ceremony_secret_names &&
      alltrue([
        for key, binding in google_project_iam_member.material_key_versions :
        binding.role == local.material_role_names.key_versions &&
        binding.member == "serviceAccount:${local.operations_identities.materials.email}" &&
        binding.condition[0].title == "limit_material_versions_${replace(key, "-", "_")}" &&
        binding.condition[0].expression == "(resource.type == 'cloudkms.googleapis.com/CryptoKey' && resource.name == 'projects/seen-registry-prod-476219/locations/us-central1/keyRings/seen-registry-prod/cryptoKeys/${key}') || (resource.type == 'cloudkms.googleapis.com/CryptoKeyVersion' && resource.name.startsWith('projects/seen-registry-prod-476219/locations/us-central1/keyRings/seen-registry-prod/cryptoKeys/${key}/cryptoKeyVersions/'))"
      ]) &&
      alltrue([
        for secret, binding in google_project_iam_member.material_secret_versions :
        binding.role == local.material_role_names.secret_versions &&
        binding.member == "serviceAccount:${local.operations_identities.materials.email}" &&
        binding.condition[0].title == "limit_material_versions_${replace(secret, "-", "_")}" &&
        binding.condition[0].expression == "(resource.type == 'secretmanager.googleapis.com/Secret' && resource.name == 'projects/123456789012/secrets/${secret}') || (resource.type == 'secretmanager.googleapis.com/SecretVersion' && resource.name.startsWith('projects/123456789012/secrets/${secret}/versions/'))"
      ]) &&
      !contains(google_project_iam_custom_role.material_key_versions[0].permissions, "cloudkms.cryptoKeyVersions.useToSign") &&
      !contains(google_project_iam_custom_role.material_key_versions[0].permissions, "cloudkms.cryptoKeyVersions.destroy") &&
      !contains(google_project_iam_custom_role.material_secret_versions[0].permissions, "secretmanager.versions.access") &&
      !contains(google_project_iam_custom_role.material_secret_versions[0].permissions, "secretmanager.versions.destroy")
    )
    error_message = "Materials operations must be limited to creating/verifying versions on four exact keys and six exact envelope secrets, without signing or payload-read authority."
  }

  assert {
    condition = (
      google_project_iam_member.infrastructure_plan[0].member == "serviceAccount:${local.infrastructure_identities.plan.email}" &&
      google_project_iam_member.infrastructure_apply[0].member == "serviceAccount:${local.infrastructure_identities.apply.email}" &&
      google_project_iam_member.infrastructure_project_iam[0].member == "serviceAccount:${local.infrastructure_identities.apply.email}" &&
      alltrue([
        for binding in values(google_project_iam_member.infrastructure_kms_policy_setter) :
        binding.member == "serviceAccount:${local.infrastructure_identities.apply.email}"
      ]) &&
      alltrue([
        for binding in values(google_project_iam_member.infrastructure_secret_policy_setter) :
        binding.member == "serviceAccount:${local.infrastructure_identities.apply.email}"
      ]) &&
      alltrue([
        for binding in values(google_project_iam_member.infrastructure_registry_storage_policy_setter) :
        binding.member == "serviceAccount:${local.infrastructure_identities.apply.email}"
      ]) &&
      alltrue([
        for binding in values(google_project_iam_member.infrastructure_state_storage_policy_setter) :
        binding.member == "serviceAccount:${local.infrastructure_identities.apply.email}"
      ]) &&
      alltrue([
        for binding in values(google_project_iam_member.infrastructure_run_policy_setter) :
        binding.member == "serviceAccount:${local.infrastructure_identities.apply.email}"
      ])
    )
    error_message = "Infrastructure roles must remain exclusive to the plan/apply identities and must never be inherited by either operations identity."
  }

  assert {
    condition = (
      toset(keys(google_project_iam_member.infrastructure_kms_policy_setter)) == local.production_kms_keys &&
      alltrue([
        for key, binding in google_project_iam_member.infrastructure_kms_policy_setter :
        binding.project == "seen-registry-prod-476219" &&
        binding.role == "projects/seen-registry-prod-476219/roles/seenRegistryKmsIamApply" &&
        binding.condition[0].expression == "resource.type == 'cloudkms.googleapis.com/CryptoKey' && resource.name == 'projects/seen-registry-prod-476219/locations/us-central1/keyRings/seen-registry-prod/cryptoKeys/${key}' && ${local.kms_modified_roles_condition}" &&
        !strcontains(binding.condition[0].expression, ".size(")
      ])
    )
    error_message = "KMS IAM mutation must be inherited from project policy but limited to each exact online key and reviewed signer/viewer roles."
  }

  assert {
    condition = (
      toset(keys(google_project_iam_member.infrastructure_secret_policy_setter)) == local.production_secret_names &&
      alltrue([
        for secret, binding in google_project_iam_member.infrastructure_secret_policy_setter :
        binding.role == "projects/seen-registry-prod-476219/roles/seenRegistrySecretIamApply" &&
        binding.condition[0].expression == "resource.type == 'secretmanager.googleapis.com/Secret' && resource.name == 'projects/123456789012/secrets/${secret}' && ${local.secret_modified_roles_condition}" &&
        !strcontains(binding.condition[0].expression, ".size(")
      ])
    )
    error_message = "Secret IAM mutation must be inherited from project policy but limited to each exact numeric-project secret and secret-accessor grants."
  }

  assert {
    condition = (
      toset(keys(google_project_iam_member.infrastructure_registry_storage_policy_setter)) == local.production_registry_buckets &&
      alltrue([
        for bucket, binding in google_project_iam_member.infrastructure_registry_storage_policy_setter :
        binding.role == "projects/seen-registry-prod-476219/roles/seenRegistryStorageIamApply" &&
        binding.condition[0].expression == "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${bucket}' && ${local.registry_storage_modified_roles_condition}" &&
        !strcontains(binding.condition[0].expression, ".size(")
      ]) &&
      toset(keys(google_project_iam_member.infrastructure_state_storage_policy_setter)) == toset(["bootstrap", "production"]) &&
      alltrue([
        for name, binding in google_project_iam_member.infrastructure_state_storage_policy_setter :
        binding.role == "projects/seen-registry-prod-476219/roles/seenRegistryStorageIamApply" &&
        binding.condition[0].expression == "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/${local.state_backends[name].bucket}' && ${local.state_storage_modified_roles_condition}" &&
        !strcontains(binding.condition[0].expression, ".size(")
      ])
    )
    error_message = "Registry and state bucket IAM setters must be exact-name scoped with disjoint modified-role allowlists."
  }

  assert {
    condition = (
      toset(keys(google_project_iam_member.infrastructure_run_policy_setter)) == toset(["job", "service"]) &&
      google_project_iam_member.infrastructure_run_policy_setter["job"].role == "projects/seen-registry-prod-476219/roles/seenRegistryRunJobIamApply" &&
      google_project_iam_member.infrastructure_run_policy_setter["service"].role == "projects/seen-registry-prod-476219/roles/seenRegistryRunServiceIamApply" &&
      google_project_iam_member.infrastructure_run_policy_setter["service"].condition[0].expression == "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/run.invoker'])" &&
      google_project_iam_member.infrastructure_run_policy_setter["job"].condition[0].expression == "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/run.invoker', 'projects/seen-registry-prod-476219/roles/seenRegistryJobViewer'])" &&
      alltrue([
        for binding in values(google_project_iam_member.infrastructure_run_policy_setter) :
        !strcontains(binding.condition[0].expression, "resource.name") &&
        !strcontains(binding.condition[0].expression, "resource.type")
      ])
    )
    error_message = "Run IAM mutation must use exact one-permission setters and permit only service invoker or job invoker plus the one-permission viewer role."
  }
}

run "owner_adoption_contract_is_exact_and_human_only" {
  command = plan

  plan_options {
    target = [terraform_data.bootstrap_phase_contract]
  }

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    enable_temporary_human_state_bucket_policy_access         = true
    temporary_human_state_bucket_policy_access_expiry         = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified       = true
    enable_state_bucket_iam_reconciliation                    = true
    automatic_default_service_account_grants_policy_effective = true
    managed_member_policy_effective                           = true
    project_creator_owner_member                              = "user:yousef@felidai.com"
    adopt_project_creator_owner                               = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  assert {
    condition = (
      length(google_project_iam_member.project_creator_owner) == 0 &&
      var.adopt_project_creator_owner &&
      var.project_creator_owner_member == "user:yousef@felidai.com" &&
      strcontains(file("${path.root}/main.tf"), "project_creator_owner_members = var.adopt_project_creator_owner ? toset([var.project_creator_owner_member]) : toset([])") &&
      strcontains(file("${path.root}/main.tf"), "project_creator_owner_adoption_record_members = (") &&
      strcontains(file("${path.root}/main.tf"), "resource \"terraform_data\" \"project_creator_owner_adoption_record\"") &&
      strcontains(file("${path.root}/main.tf"), "depends_on = [google_project_iam_member.project_creator_owner]") &&
      strcontains(file("${path.root}/main.tf"), "each.value == \"user:$${data.google_client_openid_userinfo.bootstrap[0].email}\"") &&
      strcontains(file("${path.root}/main.tf"), "id = \"$${var.project_id} roles/owner $${each.value}\"")
    )
    error_message = "Owner adoption must remain an explicit import of only the configured human project creator; OpenTofu's mock provider cannot execute imports or evaluate the omitted identity lookup in this targeted test."
  }
}

run "owner_removal_is_a_separate_human_phase" {
  command = plan

  # Prior-state values for terraform_data.input cannot be mocked. Exercise the
  # pure phase contract here; the offline saved-plan linter fixtures exercise
  # all valid/invalid prior evidence transitions.
  plan_options {
    target = [
      terraform_data.bootstrap_phase_contract,
      data.google_iam_policy.state,
      google_project_iam_member.temporary_human_state_bucket_policy_read_access,
      google_project_iam_member.temporary_human_state_bucket_policy_access,
    ]
  }

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    temporary_human_state_bucket_policy_access_verified       = true
    temporary_human_state_bucket_policy_access_removed        = true
    enable_state_bucket_iam_reconciliation                    = true
    production_foundation_applied                             = true
    automatic_default_service_account_grants_policy_effective = true
    enable_temporary_human_state_migration_access             = true
    temporary_human_state_migration_access_expiry             = timeadd(plantimestamp(), "12h")
    managed_member_policy_effective                           = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    project_creator_owner_member                              = "user:yousef@felidai.com"
    approve_project_creator_owner_removal                     = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  assert {
    condition = (
      var.approve_project_creator_owner_removal &&
      !var.adopt_project_creator_owner &&
      !var.project_creator_owner_removed &&
      var.project_creator_owner_member == "user:yousef@felidai.com" &&
      var.production_foundation_applied &&
      var.enable_temporary_human_state_migration_access &&
      !var.enable_temporary_human_state_bucket_policy_access &&
      var.temporary_human_state_bucket_policy_access_verified &&
      var.temporary_human_state_bucket_policy_access_removed &&
      var.portfolio_gateway_exception_effective &&
      !var.project_executor_handoff_complete &&
      strcontains(file("${path.root}/main.tf"), "local.project_creator_owner_removal_record_valid")
    )
    error_message = "Owner removal must be explicit, human-authenticated, and complete before CI handoff."
  }

  assert {
    condition = (
      length(data.google_iam_policy.state) == 2 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_read_access) == 0 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_access) == 0 &&
      strcontains(file("${path.root}/main.tf"), "for_each = var.enable_temporary_human_state_migration_access ? var.bootstrap_operator_members : toset([])") &&
      strcontains(file("${path.root}/main.tf"), "role    = local.state_role_names.reader") &&
      strcontains(file("${path.root}/main.tf"), "role    = local.state_role_names.writer") &&
      strcontains(file("${path.root}/main.tf"), "request.time < timestamp('$${var.temporary_human_state_migration_access_expiry}')") &&
      strcontains(file("${path.root}/main.tf"), "each.value.state_resource") &&
      strcontains(file("${path.root}/main.tf"), "each.value.lock_resource")
    )
    error_message = "Owner removal must retain the time-bounded migration-policy definitions while all project-level recovery bindings remain absent; the saved-plan linter proves exact prior and planned policy values."
  }
}

run "handoff_rejects_temporary_bucket_policy_recovery_access" {
  command = plan

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    production_foundation_applied                             = true
    automatic_default_service_account_grants_policy_effective = true
    managed_member_policy_effective                           = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    project_executor_handoff_complete                         = true
    enable_temporary_human_state_bucket_policy_access         = true
    temporary_human_state_bucket_policy_access_expiry         = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified       = true
    enable_state_bucket_iam_reconciliation                    = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  expect_failures = [var.project_executor_handoff_complete]
}

run "steady_handoff_has_no_human_grants_or_identity_lookup" {
  command = plan

  # The protected linter's synthetic prior_state fixtures provide the stateful
  # evidence coverage that OpenTofu's mock overrides cannot express.
  plan_options {
    target = [
      terraform_data.bootstrap_phase_contract,
      data.google_iam_policy.state,
      data.google_client_openid_userinfo.bootstrap,
      google_organization_iam_member.policy_admin_lease,
      google_project_iam_member.project_creator_owner,
      google_project_iam_member.temporary_human_state_bucket_policy_read_access,
      google_project_iam_member.temporary_human_state_bucket_policy_access,
    ]
  }

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    production_foundation_applied                             = true
    automatic_default_service_account_grants_policy_effective = true
    managed_member_policy_effective                           = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    project_creator_owner_removed                             = true
    project_executor_handoff_complete                         = true
    temporary_human_state_bucket_policy_access_verified       = true
    temporary_human_state_bucket_policy_access_removed        = true
    enable_state_bucket_iam_reconciliation                    = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  assert {
    condition = (
      var.project_executor_handoff_complete &&
      var.project_creator_owner_removed &&
      var.project_creator_owner_member == null &&
      !var.adopt_project_creator_owner &&
      !var.approve_project_creator_owner_removal &&
      !var.enable_organization_policy_admin_lease &&
      !var.enable_project_policy_admin_lease &&
      !var.enable_temporary_human_state_migration_access &&
      var.temporary_human_state_migration_access_expiry == null &&
      !var.enable_temporary_human_state_bucket_policy_access &&
      var.temporary_human_state_bucket_policy_access_expiry == null &&
      var.temporary_human_state_bucket_policy_access_verified &&
      var.temporary_human_state_bucket_policy_access_removed &&
      var.production_foundation_applied &&
      var.enable_state_bucket_iam_reconciliation &&
      var.portfolio_gateway_exception_effective &&
      strcontains(file("${path.root}/main.tf"), "count    = var.enable_production_project_creation && !var.project_executor_handoff_complete ? 1 : 0") &&
      strcontains(file("${path.root}/main.tf"), "resource \"terraform_data\" \"phase_evidence_contract\"")
    )
    error_message = "Steady WIF refresh must not evaluate human ADC or retain human Owner, policy, or backend access."
  }

  assert {
    condition = (
      length(data.google_client_openid_userinfo.bootstrap) == 0 &&
      length(google_organization_iam_member.policy_admin_lease) == 0 &&
      length(google_project_iam_member.project_creator_owner) == 0 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_read_access) == 0 &&
      length(google_project_iam_member.temporary_human_state_bucket_policy_access) == 0 &&
      length(data.google_iam_policy.state) == 2 &&
      alltrue(flatten([
        for policy in values(data.google_iam_policy.state) : [
          for binding in policy.binding : alltrue([
            for member in binding.members :
            startswith(member, "serviceAccount:") &&
            !startswith(member, "user:") &&
            !startswith(member, "group:")
          ])
        ]
      ]))
    )
    error_message = "Steady policy definitions must retain only service-account state access and no human IAM resource or identity lookup."
  }
}

run "gateway_exception_is_created_in_separate_human_policy_phase" {
  command = plan

  variables {
    enable_control_project_apis                         = true
    enable_organization_guardrails                      = true
    organization_guardrail_effective                    = true
    github_infrastructure_environments_reviewed         = true
    github_operations_environments_reviewed             = true
    enable_production_project_creation                  = true
    enable_production_project_bootstrap                 = true
    production_project_creation_verified                = true
    enable_project_policy_admin_lease                   = true
    policy_admin_lease_expiry                           = timeadd(timestamp(), "2h")
    enable_temporary_human_state_bucket_policy_access   = true
    temporary_human_state_bucket_policy_access_expiry   = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified = true
    enable_state_bucket_iam_reconciliation              = true
    enable_portfolio_gateway_exception                  = true
    managed_member_policy_effective                     = true
    project_id                                          = "seen-registry-prod-476219"
    organization_id                                     = "567958019562"
    billing_account_id                                  = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                          = ["user:yousef@felidai.com"]
    notification_email                                  = "yousef@felidai.com"
  }

  assert {
    condition = (
      toset(keys(google_organization_iam_member.policy_admin_lease)) == toset(["user:yousef@felidai.com"]) &&
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].org_id == "567958019562" &&
      google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].role == "roles/orgpolicy.policyAdmin" &&
      length(split("request.time < timestamp('$${var.policy_admin_lease_expiry}')", file("${path.root}/main.tf"))) == 3 &&
      startswith(
        google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].condition[0].expression,
        "resource.matchTag('567958019562/seen-registry-policy-scope', 'production') && ",
      ) &&
      can(regex(
        "^request\\.time < timestamp\\('[^']+'\\)$",
        trimprefix(
          google_organization_iam_member.policy_admin_lease["user:yousef@felidai.com"].condition[0].expression,
          "resource.matchTag('567958019562/seen-registry-policy-scope', 'production') && ",
        ),
      )) &&
      !strcontains(file("${path.root}/main.tf"), "resource \"google_project_iam_member\" \"policy_admin_lease\"") &&
      output.active_bootstrap_identity == "yousef@felidai.com" &&
      google_org_policy_policy.legacy_domain_restriction_override[0].spec[0].inherit_from_parent == false &&
      google_org_policy_policy.legacy_domain_restriction_override[0].spec[0].rules[0].allow_all == "TRUE" &&
      length(google_service_account_iam_member.infrastructure_oidc) == 0 &&
      output.temporary_human_state_bucket_policy_access_enabled &&
      output.temporary_human_state_bucket_policy_access_verified &&
      output.portfolio_gateway_exception_enabled &&
      !output.portfolio_gateway_exception_effective &&
      !output.project_executor_handoff_complete
    )
    error_message = "The legacy override must be created only in an explicit pre-handoff human phase with a time-bounded organization-scoped Policy Admin lease after the managed policy is effective."
  }

  assert {
    condition = strcontains(file("${path.root}/main.tf"), <<-EOT
resource "google_org_policy_policy" "legacy_domain_restriction_override" {
  count = var.enable_production_project_bootstrap && var.enable_portfolio_gateway_exception ? 1 : 0
    EOT
      ) && strcontains(file("${path.root}/main.tf"), <<-EOT
  depends_on = [
    google_org_policy_policy.managed_allowed_policy_members,
    google_organization_iam_member.policy_admin_lease,
  ]

  lifecycle {
    prevent_destroy = true

    precondition {
      condition     = var.portfolio_gateway_exception_effective || var.enable_project_policy_admin_lease
      error_message = "Creating the gateway exception requires the time-bounded organization-scoped project-policy lease; later refreshes require its effective-policy attestation."
    }
  }
}
    EOT
    )
    error_message = "The required steady gateway exception must remain protected from routine destruction."
  }
}

run "rejects_project_creation_before_github_environment_review" {
  command = plan

  variables {
    enable_control_project_apis        = true
    enable_organization_guardrails     = true
    organization_guardrail_effective   = true
    enable_production_project_creation = true
    project_id                         = "seen-registry-prod-476219"
    organization_id                    = "567958019562"
    billing_account_id                 = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members         = ["user:yousef@felidai.com"]
    notification_email                 = "yousef@felidai.com"
  }

  expect_failures = [var.enable_production_project_creation]
}

run "rejects_handoff_before_owner_removal" {
  command = plan

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    production_foundation_applied                             = true
    automatic_default_service_account_grants_policy_effective = true
    managed_member_policy_effective                           = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    project_executor_handoff_complete                         = true
    temporary_human_state_bucket_policy_access_verified       = true
    temporary_human_state_bucket_policy_access_removed        = true
    enable_state_bucket_iam_reconciliation                    = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  expect_failures = [
    terraform_data.bootstrap_phase_contract,
    terraform_data.phase_evidence_contract,
  ]
}

run "combined_owner_phase_contract_is_rejected_before_import" {
  command = plan

  plan_options {
    target = [terraform_data.bootstrap_phase_contract]
  }

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    temporary_human_state_bucket_policy_access_verified       = true
    temporary_human_state_bucket_policy_access_removed        = true
    enable_state_bucket_iam_reconciliation                    = true
    automatic_default_service_account_grants_policy_effective = true
    managed_member_policy_effective                           = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    production_foundation_applied                             = true
    enable_temporary_human_state_migration_access             = true
    temporary_human_state_migration_access_expiry             = timeadd(timestamp(), "12h")
    project_creator_owner_member                              = "user:yousef@felidai.com"
    adopt_project_creator_owner                               = true
    approve_project_creator_owner_removal                     = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  expect_failures = [terraform_data.bootstrap_phase_contract]
}

run "rejects_owner_removal_with_project_policy_admin_lease" {
  command = plan

  plan_options {
    target = [terraform_data.bootstrap_phase_contract]
  }

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    enable_project_policy_admin_lease                         = true
    policy_admin_lease_expiry                                 = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified       = true
    temporary_human_state_bucket_policy_access_removed        = true
    enable_state_bucket_iam_reconciliation                    = true
    enable_temporary_human_state_migration_access             = true
    temporary_human_state_migration_access_expiry             = timeadd(timestamp(), "12h")
    managed_member_policy_effective                           = true
    automatic_default_service_account_grants_policy_effective = true
    production_foundation_applied                             = true
    production_image_publisher_foundation_applied             = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    project_creator_owner_member                              = "user:yousef@felidai.com"
    approve_project_creator_owner_removal                     = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  expect_failures = [var.production_foundation_applied, var.approve_project_creator_owner_removal]
}

run "rejects_owner_removal_with_organization_policy_admin_lease" {
  command = plan

  plan_options {
    target = [terraform_data.bootstrap_phase_contract]
  }

  variables {
    enable_control_project_apis                               = true
    enable_organization_guardrails                            = true
    organization_guardrail_effective                          = true
    enable_organization_policy_admin_lease                    = true
    policy_admin_lease_expiry                                 = timeadd(timestamp(), "2h")
    github_infrastructure_environments_reviewed               = true
    github_operations_environments_reviewed                   = true
    enable_production_project_creation                        = true
    enable_production_project_bootstrap                       = true
    production_project_creation_verified                      = true
    temporary_human_state_bucket_policy_access_verified       = true
    temporary_human_state_bucket_policy_access_removed        = true
    enable_state_bucket_iam_reconciliation                    = true
    enable_temporary_human_state_migration_access             = true
    temporary_human_state_migration_access_expiry             = timeadd(timestamp(), "12h")
    managed_member_policy_effective                           = true
    automatic_default_service_account_grants_policy_effective = true
    production_foundation_applied                             = true
    production_image_publisher_foundation_applied             = true
    enable_portfolio_gateway_exception                        = true
    portfolio_gateway_exception_effective                     = true
    project_creator_owner_member                              = "user:yousef@felidai.com"
    approve_project_creator_owner_removal                     = true
    project_id                                                = "seen-registry-prod-476219"
    organization_id                                           = "567958019562"
    billing_account_id                                        = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                                = ["user:yousef@felidai.com"]
    notification_email                                        = "yousef@felidai.com"
  }

  expect_failures = [
    var.production_foundation_applied,
    var.approve_project_creator_owner_removal,
  ]
}

run "rejects_project_creation_before_guardrail_effective" {
  command = plan

  variables {
    enable_control_project_apis                 = true
    enable_organization_guardrails              = true
    github_infrastructure_environments_reviewed = true
    github_operations_environments_reviewed     = true
    enable_production_project_creation          = true
    project_id                                  = "seen-registry-prod-476219"
    organization_id                             = "567958019562"
    billing_account_id                          = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                  = ["user:yousef@felidai.com"]
    notification_email                          = "yousef@felidai.com"
  }

  expect_failures = [var.enable_production_project_creation]
}

run "rejects_project_creation_with_organization_policy_admin_lease" {
  command = plan

  variables {
    enable_control_project_apis                 = true
    enable_organization_guardrails              = true
    organization_guardrail_effective            = true
    enable_organization_policy_admin_lease      = true
    policy_admin_lease_expiry                   = timeadd(timestamp(), "2h")
    github_infrastructure_environments_reviewed = true
    github_operations_environments_reviewed     = true
    enable_production_project_creation          = true
    project_id                                  = "seen-registry-prod-476219"
    organization_id                             = "567958019562"
    billing_account_id                          = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                  = ["user:yousef@felidai.com"]
    notification_email                          = "yousef@felidai.com"
  }

  expect_failures = [google_project.production]
}

run "rejects_foundation_before_default_service_account_policy_effective" {
  command = plan

  variables {
    enable_control_project_apis                         = true
    enable_organization_guardrails                      = true
    organization_guardrail_effective                    = true
    github_infrastructure_environments_reviewed         = true
    github_operations_environments_reviewed             = true
    enable_production_project_creation                  = true
    enable_production_project_bootstrap                 = true
    production_project_creation_verified                = true
    enable_project_policy_admin_lease                   = true
    policy_admin_lease_expiry                           = timeadd(timestamp(), "2h")
    enable_temporary_human_state_bucket_policy_access   = true
    temporary_human_state_bucket_policy_access_expiry   = timeadd(timestamp(), "2h")
    temporary_human_state_bucket_policy_access_verified = true
    enable_state_bucket_iam_reconciliation              = true
    managed_member_policy_effective                     = true
    enable_portfolio_gateway_exception                  = true
    portfolio_gateway_exception_effective               = true
    production_foundation_applied                       = true
    project_id                                          = "seen-registry-prod-476219"
    organization_id                                     = "567958019562"
    billing_account_id                                  = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members                          = ["user:yousef@felidai.com"]
    notification_email                                  = "yousef@felidai.com"
  }

  expect_failures = [var.production_foundation_applied]
}

run "rejects_the_portfolio_project_as_production" {
  command = plan

  variables {
    project_id                 = "portfolio-476219"
    organization_id            = "567958019562"
    billing_account_id         = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members = ["user:yousef@felidai.com"]
    notification_email         = "yousef@felidai.com"
  }

  expect_failures = [var.project_id]
}

run "rejects_an_unreviewed_organization" {
  command = plan

  variables {
    project_id                 = "seen-registry-prod-476219"
    organization_id            = "123456789012"
    billing_account_id         = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members = ["user:yousef@felidai.com"]
    notification_email         = "yousef@felidai.com"
  }

  expect_failures = [var.organization_id]
}

run "rejects_guardrails_before_control_project_apis" {
  command = plan

  variables {
    enable_organization_guardrails = true
    project_id                     = "seen-registry-prod-476219"
    organization_id                = "567958019562"
    billing_account_id             = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members     = ["user:yousef@felidai.com"]
    notification_email             = "yousef@felidai.com"
  }

  expect_failures = [var.enable_organization_guardrails]
}

run "rejects_gateway_exception_without_handoff" {
  command = plan

  variables {
    enable_portfolio_gateway_exception = true
    project_id                         = "seen-registry-prod-476219"
    organization_id                    = "567958019562"
    billing_account_id                 = "ABCDEF-123456-ABCDEF"
    bootstrap_operator_members         = ["user:yousef@felidai.com"]
    notification_email                 = "yousef@felidai.com"
  }

  expect_failures = [var.enable_portfolio_gateway_exception]
}
