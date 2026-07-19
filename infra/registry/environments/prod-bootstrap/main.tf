locals {
  project_name = "Seen Registry Production"

  github_repository          = "codeyousef/summon-portfolio"
  github_repository_id       = "1091564909"
  github_repository_owner_id = "10247142"
  github_ref                 = "refs/heads/master"
  github_ref_type            = "branch"
  github_event_name          = "workflow_dispatch"

  infrastructure_workflow_ref = "codeyousef/summon-portfolio/.github/workflows/deploy-seen-registry-infrastructure.yml@refs/heads/master"
  operations_workflow_ref     = "codeyousef/summon-portfolio/.github/workflows/operate-seen-registry-production.yml@refs/heads/master"

  infrastructure_wif_pool_id = "seen-registry-prod-infra"
  infrastructure_identities = {
    plan = {
      account_id   = "seen-registry-prod-plan"
      email        = "seen-registry-prod-plan@${var.project_id}.iam.gserviceaccount.com"
      display_name = "Seen Registry Production Planner"
      description  = "Read-only production infrastructure planner with exact state-lock access"
      provider_id  = "infra-plan"
      environment  = "seen-registry-production-plan"
      subject      = "repo:codeyousef/summon-portfolio:environment:seen-registry-production-plan"
      workflow_ref = local.infrastructure_workflow_ref
    }
    apply = {
      account_id   = "seen-registry-prod-iac"
      email        = "seen-registry-prod-iac@${var.project_id}.iam.gserviceaccount.com"
      display_name = "Seen Registry Production Applier"
      description  = "Protected saved-plan production infrastructure applier"
      provider_id  = "infra-apply"
      environment  = "seen-registry-production-apply"
      subject      = "repo:codeyousef/summon-portfolio:environment:seen-registry-production-apply"
      workflow_ref = local.infrastructure_workflow_ref
    }
  }
  operations_identities = {
    materials = {
      account_id   = "seen-registry-prod-materials"
      email        = "seen-registry-prod-materials@${var.project_id}.iam.gserviceaccount.com"
      display_name = "Seen Registry Production Materials Operator"
      description  = "Creates exact production KMS and Secret Manager material versions without reading secret payloads or signing"
      provider_id  = "material-ops"
      environment  = "seen-registry-production-materials"
      subject      = "repo:codeyousef/summon-portfolio:environment:seen-registry-production-materials"
      workflow_ref = local.operations_workflow_ref
    }
    jobs = {
      account_id   = "seen-registry-prod-job-runner"
      email        = "seen-registry-prod-job-runner@${var.project_id}.iam.gserviceaccount.com"
      display_name = "Seen Registry Production Job Runner"
      description  = "Invokes only production Cloud Run jobs selected by the reviewed production root"
      provider_id  = "job-ops"
      environment  = "seen-registry-production-jobs"
      subject      = "repo:codeyousef/summon-portfolio:environment:seen-registry-production-jobs"
      workflow_ref = local.operations_workflow_ref
    }
  }
  github_identities = merge(local.infrastructure_identities, local.operations_identities)

  portfolio_gateway_member = (
    "serviceAccount:portfolio-prod-runtime@portfolio-476219.iam.gserviceaccount.com"
  )
  organization_principal_set = (
    "//cloudresourcemanager.googleapis.com/organizations/${var.organization_id}"
  )

  common_labels = {
    application = "seen-registry"
    environment = "prod"
    managed_by  = "opentofu"
  }

  bootstrap_services = toset([
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "monitoring.googleapis.com",
    "orgpolicy.googleapis.com",
    "serviceusage.googleapis.com",
    "sts.googleapis.com",
    "storage.googleapis.com",
  ])
  control_project_services = toset([
    "cloudbilling.googleapis.com",
    "orgpolicy.googleapis.com",
  ])

  infrastructure_plan_permissions = toset([
    "artifactregistry.locations.get",
    "artifactregistry.repositories.get",
    "artifactregistry.repositories.getIamPolicy",
    "cloudkms.cryptoKeys.get",
    "cloudkms.cryptoKeys.getIamPolicy",
    "cloudkms.keyRings.get",
    "cloudkms.locations.get",
    "compute.firewalls.get",
    "compute.networks.get",
    "compute.projects.get",
    "compute.routers.get",
    "compute.subnetworks.get",
    "datastore.databases.get",
    "datastore.locations.get",
    "dns.changes.get",
    "dns.changes.list",
    "dns.managedZones.get",
    "dns.projects.get",
    "dns.resourceRecordSets.get",
    "dns.resourceRecordSets.list",
    "iam.googleapis.com/workloadIdentityPoolProviders.get",
    "iam.googleapis.com/workloadIdentityPoolProviders.list",
    "iam.googleapis.com/workloadIdentityPools.get",
    "iam.googleapis.com/workloadIdentityPools.list",
    "iam.roles.get",
    "iam.serviceAccounts.get",
    "iam.serviceAccounts.getIamPolicy",
    "logging.logMetrics.get",
    "monitoring.alertPolicies.get",
    "monitoring.notificationChannels.get",
    "monitoring.notificationChannels.list",
    "monitoring.uptimeCheckConfigs.get",
    "resourcemanager.projects.get",
    "resourcemanager.projects.getIamPolicy",
    "run.jobs.get",
    "run.jobs.getIamPolicy",
    "run.locations.list",
    "run.operations.get",
    "run.services.get",
    "run.services.getIamPolicy",
    "secretmanager.locations.get",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "serviceusage.services.get",
    "serviceusage.services.use",
    "storage.buckets.get",
    "storage.buckets.getIamPolicy",
  ])
  infrastructure_apply_permissions = setunion(local.infrastructure_plan_permissions, toset([
    "compute.firewalls.create",
    "compute.firewalls.delete",
    "compute.firewalls.update",
    "compute.globalOperations.get",
    "compute.networks.use",
    "compute.subnetworks.use",
    "dns.changes.create",
    "dns.resourceRecordSets.create",
    "dns.resourceRecordSets.delete",
    "dns.resourceRecordSets.update",
    "logging.logMetrics.update",
    "monitoring.alertPolicies.create",
    "monitoring.alertPolicies.delete",
    "monitoring.alertPolicies.update",
    "monitoring.uptimeCheckConfigs.create",
    "monitoring.uptimeCheckConfigs.delete",
    "monitoring.uptimeCheckConfigs.update",
    "run.jobs.create",
    "run.jobs.delete",
    "run.jobs.update",
    "run.services.create",
    "run.services.delete",
    "run.services.update",
  ]))

  infrastructure_role_names = {
    plan        = "projects/${var.project_id}/roles/seenRegistryInfrastructurePlan"
    apply       = "projects/${var.project_id}/roles/seenRegistryInfrastructureApply"
    project_iam = "projects/${var.project_id}/roles/seenRegistryProjectIamApply"
  }
  resource_iam_setter_roles = {
    kms = {
      role_id    = "seenRegistryKmsIamApply"
      title      = "Seen Registry KMS IAM Applier"
      permission = "cloudkms.cryptoKeys.setIamPolicy"
    }
    run_service = {
      role_id    = "seenRegistryRunServiceIamApply"
      title      = "Seen Registry Run Service IAM Applier"
      permission = "run.services.setIamPolicy"
    }
    run_job = {
      role_id    = "seenRegistryRunJobIamApply"
      title      = "Seen Registry Run Job IAM Applier"
      permission = "run.jobs.setIamPolicy"
    }
    secret = {
      role_id    = "seenRegistrySecretIamApply"
      title      = "Seen Registry Secret IAM Applier"
      permission = "secretmanager.secrets.setIamPolicy"
    }
    storage = {
      role_id    = "seenRegistryStorageIamApply"
      title      = "Seen Registry Storage IAM Applier"
      permission = "storage.buckets.setIamPolicy"
    }
  }
  resource_iam_setter_role_names = {
    for name, role in local.resource_iam_setter_roles :
    name => "projects/${var.project_id}/roles/${role.role_id}"
  }
  state_role_names = {
    reader = "projects/${var.project_id}/roles/seenRegistryStateReader"
    locker = "projects/${var.project_id}/roles/seenRegistryStateLocker"
    writer = "projects/${var.project_id}/roles/seenRegistryStateWriter"
  }

  iam_modified_role_allowlist = sort([
    "roles/datastore.user",
    "roles/datastore.viewer",
  ])
  iam_modified_roles_condition = format(
    "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).size() > 0 && api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly([%s])",
    join(", ", [for role in local.iam_modified_role_allowlist : "'${role}'"]),
  )

  production_region       = "us-central1"
  production_kms_key_ring = "seen-registry-prod"
  production_kms_keys = toset([
    "seen-registry-prod-releases",
    "seen-registry-prod-security",
    "seen-registry-prod-snapshot",
    "seen-registry-prod-timestamp",
  ])
  production_secret_names = toset([
    "seen-registry-prod-github-app-id",
    "seen-registry-prod-github-app-private-key",
    "seen-registry-prod-gitlab-forge-token",
    "seen-registry-prod-publisher-token",
    "seen-registry-prod-root-envelope",
    "seen-registry-prod-root-rotation-envelope",
    "seen-registry-prod-security-token",
    "seen-registry-prod-targets-envelope",
    "seen-registry-prod-targets-releases-rotation-envelope",
    "seen-registry-prod-targets-renewal-envelope",
    "seen-registry-prod-targets-security-rotation-envelope",
    "seen-registry-prod-trust-and-safety-token",
  ])
  production_ceremony_secret_names = toset([
    "seen-registry-prod-root-envelope",
    "seen-registry-prod-root-rotation-envelope",
    "seen-registry-prod-targets-envelope",
    "seen-registry-prod-targets-releases-rotation-envelope",
    "seen-registry-prod-targets-renewal-envelope",
    "seen-registry-prod-targets-security-rotation-envelope",
  ])
  material_role_names = {
    key_versions    = "projects/${var.project_id}/roles/seenRegistryMaterialKeyVersions"
    secret_versions = "projects/${var.project_id}/roles/seenRegistryMaterialSecretVersions"
  }
  job_operations_viewer_role_name = "projects/${var.project_id}/roles/seenRegistryJobViewer"
  production_registry_buckets = toset([
    "${var.project_id}-seen-registry-prod-backup",
    "${var.project_id}-seen-registry-prod-blobs",
    "${var.project_id}-seen-registry-prod-evidence",
    "${var.project_id}-seen-registry-prod-metadata",
    "${var.project_id}-seen-registry-prod-private",
    "${var.project_id}-seen-registry-prod-quarantine",
  ])
  kms_modified_roles_condition = format(
    "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).size() > 0 && api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['projects/%s/roles/seen_registry_prod_kms_signer', 'roles/cloudkms.publicKeyViewer'])",
    var.project_id,
  )
  run_service_modified_roles_condition = "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).size() > 0 && api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/run.invoker'])"
  run_job_modified_roles_condition = format(
    "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).size() > 0 && api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/run.invoker', '%s'])",
    local.job_operations_viewer_role_name,
  )
  secret_modified_roles_condition = "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).size() > 0 && api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(['roles/secretmanager.secretAccessor'])"
  registry_storage_modified_roles_condition = format(
    "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).size() > 0 && api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly([%s])",
    join(", ", [for role in sort([
      "projects/${var.project_id}/roles/seen_registry_prod_blob_creator",
      "projects/${var.project_id}/roles/seen_registry_prod_metadata_creator",
      "projects/${var.project_id}/roles/seen_registry_prod_pointer_replacer",
      "projects/${var.project_id}/roles/seen_registry_prod_quarantine_promoter",
      "projects/${var.project_id}/roles/seen_registry_prod_quarantine_writer",
      "roles/storage.objectViewer",
    ]) : "'${role}'"]),
  )
  state_storage_modified_roles_condition = format(
    "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).size() > 0 && api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly([%s])",
    join(", ", [for role in sort([
      local.state_role_names.locker,
      local.state_role_names.reader,
      local.state_role_names.writer,
    ]) : "'${role}'"]),
  )

  state_backends = {
    bootstrap = {
      bucket = "${var.project_id}-bootstrap-tofu-state"
      prefix = "seen-registry/bootstrap/prod"
    }
    production = {
      bucket = "${var.project_id}-prod-tofu-state"
      prefix = "seen-registry/prod"
    }
  }
  state_backend_resources = {
    for name, backend in local.state_backends : name => merge(backend, {
      bucket_resource = "projects/_/buckets/${backend.bucket}"
      state_resource  = "projects/_/buckets/${backend.bucket}/objects/${backend.prefix}/default.tfstate"
      lock_resource   = "projects/_/buckets/${backend.bucket}/objects/${backend.prefix}/default.tflock"
    })
  }

  project_run_iam_setter_bindings = {
    job = {
      role                     = local.resource_iam_setter_role_names.run_job
      title                    = "limit_registry_run_job_iam_roles"
      description              = "Allows only exact Cloud Run job invoker and one-permission viewer grants in the isolated registry project"
      modified_roles_condition = local.run_job_modified_roles_condition
    }
    service = {
      role                     = local.resource_iam_setter_role_names.run_service
      title                    = "limit_registry_run_service_iam_roles"
      description              = "Allows only Cloud Run service invoker grants in the isolated registry project"
      modified_roles_condition = local.run_service_modified_roles_condition
    }
  }

  project_creator_owner_members = var.adopt_project_creator_owner ? toset([var.project_creator_owner_member]) : toset([])
}

resource "terraform_data" "bootstrap_phase_contract" {
  input = "seen-registry-production-bootstrap-phase-contract"

  lifecycle {
    precondition {
      condition = !var.project_executor_handoff_complete || (
        var.github_infrastructure_environments_reviewed &&
        var.github_operations_environments_reviewed &&
        var.production_foundation_applied &&
        var.project_creator_owner_removed &&
        var.project_creator_owner_member == null &&
        !var.adopt_project_creator_owner &&
        !var.approve_project_creator_owner_removal &&
        !var.enable_organization_policy_admin_lease &&
        !var.enable_project_policy_admin_lease &&
        !var.enable_temporary_human_state_migration_access &&
        var.enable_portfolio_gateway_exception &&
        var.portfolio_gateway_exception_effective
      )
      error_message = "CI handoff requires confirmed Owner removal and no human Owner, policy-admin, or state access configuration."
    }

    precondition {
      condition = !var.enable_portfolio_gateway_exception || (
        var.portfolio_gateway_exception_effective ||
        (
          !var.project_executor_handoff_complete &&
          var.enable_project_policy_admin_lease
        )
      )
      error_message = "The gateway exception must first be created under the temporary direct-human project Policy Admin lease, then attested effective before that lease is removed."
    }

    precondition {
      condition = !(
        var.adopt_project_creator_owner &&
        var.approve_project_creator_owner_removal
      )
      error_message = "Creator Owner adoption and removal must be separate reviewed phases."
    }

    precondition {
      condition = !(
        var.enable_production_project_bootstrap &&
        !var.project_executor_handoff_complete &&
        var.project_creator_owner_member != null
        ) || (
        var.adopt_project_creator_owner ||
        var.approve_project_creator_owner_removal
      )
      error_message = "A configured creator Owner must be in exactly the explicit human adoption or removal phase."
    }

    precondition {
      condition = !var.project_creator_owner_removed || (
        !var.adopt_project_creator_owner &&
        !var.approve_project_creator_owner_removal &&
        var.project_creator_owner_member == null &&
        !var.enable_organization_policy_admin_lease &&
        !var.enable_project_policy_admin_lease &&
        var.production_foundation_applied &&
        var.enable_portfolio_gateway_exception &&
        var.portfolio_gateway_exception_effective
      )
      error_message = "The Owner-removed attestation is valid only after all creator Owner management inputs are absent."
    }


    precondition {
      condition = !var.approve_project_creator_owner_removal || (
        var.production_foundation_applied &&
        var.enable_portfolio_gateway_exception &&
        var.portfolio_gateway_exception_effective &&
        !var.enable_organization_policy_admin_lease &&
        !var.enable_project_policy_admin_lease &&
        var.enable_temporary_human_state_migration_access
      )
      error_message = "Owner removal requires the completed production foundation, effective gateway exception, and temporary exact state access retained for both roots."
    }
  }
}

data "google_client_openid_userinfo" "bootstrap" {
  provider = google.bootstrap_identity
  count    = var.enable_production_project_bootstrap && !var.project_executor_handoff_complete ? 1 : 0
}

resource "google_project_service" "control" {
  for_each = var.enable_control_project_apis ? local.control_project_services : toset([])

  project                    = var.control_project_id
  service                    = each.value
  disable_on_destroy         = false
  disable_dependent_services = false
}

resource "google_organization_iam_member" "policy_admin_lease" {
  for_each = var.enable_organization_policy_admin_lease ? var.bootstrap_operator_members : toset([])

  org_id = var.organization_id
  role   = "roles/orgpolicy.policyAdmin"
  member = each.value
}

resource "google_org_policy_policy" "skip_default_network" {
  count = var.enable_organization_guardrails ? 1 : 0

  name   = "organizations/${var.organization_id}/policies/compute.skipDefaultNetworkCreation"
  parent = "organizations/${var.organization_id}"

  spec {
    rules {
      enforce = "TRUE"
    }
  }

  depends_on = [
    google_organization_iam_member.policy_admin_lease,
    google_project_service.control,
  ]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project" "production" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  project_id          = var.project_id
  name                = local.project_name
  org_id              = var.organization_id
  billing_account     = var.billing_account_id
  auto_create_network = false
  deletion_policy     = "PREVENT"
  labels              = merge(local.common_labels, { purpose = "package-registry" })

  depends_on = [google_org_policy_policy.skip_default_network]

  lifecycle {
    prevent_destroy = true

    precondition {
      condition = (
        var.github_infrastructure_environments_reviewed &&
        var.github_operations_environments_reviewed
      )
      error_message = "All four protected production infrastructure and operations GitHub environments must be reviewed before project creation."
    }

    precondition {
      condition     = var.organization_guardrail_effective
      error_message = "The separately applied organization no-default-network guardrail must be verified effective before project creation."
    }

    precondition {
      condition     = !var.enable_organization_policy_admin_lease
      error_message = "The temporary organization Policy Admin lease must be removed before production project creation."
    }

    precondition {
      condition = var.project_executor_handoff_complete || try(
        data.google_client_openid_userinfo.bootstrap[0].email == "yousef@felidai.com",
        false,
      )
      error_message = "Before CI handoff, the production project must be managed by the reviewed bootstrap identity yousef@felidai.com."
    }
  }
}

resource "google_project_service" "bootstrap" {
  for_each = var.enable_production_project_bootstrap ? local.bootstrap_services : toset([])

  provider = google.project

  project                    = google_project.production[0].project_id
  service                    = each.value
  disable_on_destroy         = false
  disable_dependent_services = false

  lifecycle {
    precondition {
      condition     = each.value != "compute.googleapis.com"
      error_message = "Compute cannot be enabled by bootstrap before the automatic default-service-account grant policy is enforced."
    }
  }
}

resource "google_project_iam_member" "policy_admin_lease" {
  for_each = var.enable_production_project_bootstrap && var.enable_project_policy_admin_lease ? var.bootstrap_operator_members : toset([])

  provider = google.project

  project = google_project.production[0].project_id
  role    = "roles/orgpolicy.policyAdmin"
  member  = each.value
}

resource "google_org_policy_policy" "automatic_default_service_account_grants" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  name   = "projects/${google_project.production[0].number}/policies/iam.automaticIamGrantsForDefaultServiceAccounts"
  parent = "projects/${google_project.production[0].number}"

  spec {
    rules {
      enforce = "TRUE"
    }
  }

  depends_on = [
    google_project_iam_member.policy_admin_lease,
    google_project_service.bootstrap,
  ]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account" "infrastructure" {
  for_each = var.enable_production_project_bootstrap ? local.github_identities : {}

  provider = google.project

  project      = google_project.production[0].project_id
  account_id   = each.value.account_id
  display_name = each.value.display_name
  description  = each.value.description

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_iam_workload_identity_pool" "infrastructure" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project                   = google_project.production[0].project_id
  workload_identity_pool_id = local.infrastructure_wif_pool_id
  display_name              = "Seen registry prod control"
  description               = "GitHub OIDC identities restricted to exact protected production infrastructure and operations environments"
  disabled                  = false

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_iam_workload_identity_pool_provider" "infrastructure" {
  for_each = var.enable_production_project_bootstrap ? local.github_identities : {}

  provider = google.project

  project                            = google_project.production[0].project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.infrastructure[0].workload_identity_pool_id
  workload_identity_pool_provider_id = each.value.provider_id
  display_name                       = "Production ${each.key}"
  description                        = "Exact protected GitHub ${each.key} environment identity"
  disabled                           = false

  attribute_mapping = {
    "google.subject"                = "'${each.value.subject}'"
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
  }
  attribute_condition = join(" && ", [
    "assertion.sub == '${each.value.subject}'",
    "assertion.repository == '${local.github_repository}'",
    "assertion.repository_id == '${local.github_repository_id}'",
    "assertion.repository_owner_id == '${local.github_repository_owner_id}'",
    "assertion.ref == '${local.github_ref}'",
    "assertion.ref_type == '${local.github_ref_type}'",
    "assertion.workflow_ref == '${each.value.workflow_ref}'",
    "assertion.event_name == '${local.github_event_name}'",
    "assertion.environment == '${each.value.environment}'",
    "assertion.workflow_sha == assertion.sha",
  ])

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account_iam_member" "infrastructure_oidc" {
  for_each = var.enable_production_project_bootstrap && var.portfolio_gateway_exception_effective ? local.github_identities : {}

  provider = google.project

  service_account_id = google_service_account.infrastructure[each.key].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principal://iam.googleapis.com/${google_iam_workload_identity_pool.infrastructure[0].name}/subject/${each.value.subject}"

  depends_on = [google_iam_workload_identity_pool_provider.infrastructure]
}

resource "google_project_iam_custom_role" "infrastructure_plan" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryInfrastructurePlan"
  title       = "Seen Registry Infrastructure Planner"
  description = "Read-only production registry infrastructure planning and refresh permissions"
  permissions = local.infrastructure_plan_permissions
  stage       = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "infrastructure_apply" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryInfrastructureApply"
  title       = "Seen Registry Infrastructure Applier"
  description = "Saved-plan production registry infrastructure mutation permissions"
  permissions = local.infrastructure_apply_permissions
  stage       = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "project_iam_apply" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryProjectIamApply"
  title       = "Seen Registry Project IAM Applier"
  description = "Changes only the exact conditioned Firestore runtime roles in the project IAM policy"
  permissions = ["resourcemanager.projects.setIamPolicy"]
  stage       = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "resource_iam_setter" {
  for_each = var.enable_production_project_bootstrap ? local.resource_iam_setter_roles : {}

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = each.value.role_id
  title       = each.value.title
  description = startswith(each.key, "run_") ? "Sets only Cloud Run IAM policies under a project binding limited to invoker-role changes" : "Sets IAM only through an inherited project binding limited to an exact reviewed ${replace(each.key, "_", " ")} resource"
  permissions = [each.value.permission]
  stage       = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "material_key_versions" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryMaterialKeyVersions"
  title       = "Seen Registry Material Key Versions"
  description = "Creates and verifies versions under only the exact production online signing keys"
  permissions = [
    "cloudkms.cryptoKeyVersions.create",
    "cloudkms.cryptoKeyVersions.get",
    "cloudkms.cryptoKeyVersions.list",
  ]
  stage = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "material_secret_versions" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryMaterialSecretVersions"
  title       = "Seen Registry Material Secret Versions"
  description = "Adds and verifies metadata for versions under only exact production ceremony secrets without payload access"
  permissions = [
    "secretmanager.secrets.get",
    "secretmanager.versions.add",
    "secretmanager.versions.get",
    "secretmanager.versions.list",
  ]
  stage = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "job_operations_viewer" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryJobViewer"
  title       = "Seen Registry Exact Job Viewer"
  description = "Reads an exact production Cloud Run job before protected invocation"
  permissions = ["run.jobs.get"]
  stage       = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "state_reader" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryStateReader"
  title       = "Seen Registry State Reader"
  description = "Reads backend metadata and objects in one conditionally bound dedicated state bucket"
  permissions = [
    "storage.buckets.get",
    "storage.objects.get",
    "storage.objects.list",
  ]
  stage = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "state_locker" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryStateLocker"
  title       = "Seen Registry State Locker"
  description = "Creates and deletes only the conditionally bound exact OpenTofu lock object"
  permissions = [
    "storage.objects.create",
    "storage.objects.delete",
  ]
  stage = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_custom_role" "state_writer" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project     = google_project.production[0].project_id
  role_id     = "seenRegistryStateWriter"
  title       = "Seen Registry State Writer"
  description = "Mutates only the conditionally bound exact OpenTofu state and lock objects"
  permissions = [
    "storage.objects.create",
    "storage.objects.delete",
    "storage.objects.update",
  ]
  stage = "GA"

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_member" "infrastructure_plan" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.infrastructure_role_names.plan
  member  = "serviceAccount:${local.infrastructure_identities.plan.email}"

  depends_on = [
    google_project_iam_custom_role.infrastructure_plan,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "infrastructure_apply" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.infrastructure_role_names.apply
  member  = "serviceAccount:${local.infrastructure_identities.apply.email}"

  depends_on = [
    google_project_iam_custom_role.infrastructure_apply,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "material_key_versions" {
  for_each = var.enable_production_project_bootstrap ? local.production_kms_keys : toset([])

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.material_role_names.key_versions
  member  = "serviceAccount:${local.operations_identities.materials.email}"

  condition {
    title       = "limit_material_versions_${replace(each.value, "-", "_")}"
    description = "Allows key-version operations only on this exact production signing key"
    expression = format(
      "(resource.type == 'cloudkms.googleapis.com/CryptoKey' && resource.name == 'projects/%s/locations/%s/keyRings/%s/cryptoKeys/%s') || (resource.type == 'cloudkms.googleapis.com/CryptoKeyVersion' && resource.name.startsWith('projects/%s/locations/%s/keyRings/%s/cryptoKeys/%s/cryptoKeyVersions/'))",
      var.project_id,
      local.production_region,
      local.production_kms_key_ring,
      each.value,
      var.project_id,
      local.production_region,
      local.production_kms_key_ring,
      each.value,
    )
  }

  depends_on = [
    google_project_iam_custom_role.material_key_versions,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "material_secret_versions" {
  for_each = var.enable_production_project_bootstrap ? local.production_ceremony_secret_names : toset([])

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.material_role_names.secret_versions
  member  = "serviceAccount:${local.operations_identities.materials.email}"

  condition {
    title       = "limit_material_versions_${replace(each.value, "-", "_")}"
    description = "Allows secret-version metadata operations only on this exact production ceremony secret"
    expression = format(
      "(resource.type == 'secretmanager.googleapis.com/Secret' && resource.name == 'projects/%s/secrets/%s') || (resource.type == 'secretmanager.googleapis.com/SecretVersion' && resource.name.startsWith('projects/%s/secrets/%s/versions/'))",
      google_project.production[0].number,
      each.value,
      google_project.production[0].number,
      each.value,
    )
  }

  depends_on = [
    google_project_iam_custom_role.material_secret_versions,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "infrastructure_project_iam" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.infrastructure_role_names.project_iam
  member  = "serviceAccount:${local.infrastructure_identities.apply.email}"

  condition {
    title       = "limit_registry_project_iam_role_mutations"
    description = "Allows project IAM changes only for the two reviewed Firestore runtime roles"
    expression  = local.iam_modified_roles_condition
  }

  depends_on = [
    google_project_iam_custom_role.project_iam_apply,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "infrastructure_kms_policy_setter" {
  for_each = var.enable_production_project_bootstrap ? local.production_kms_keys : toset([])

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.resource_iam_setter_role_names.kms
  member  = "serviceAccount:${local.infrastructure_identities.apply.email}"

  condition {
    title       = "limit_kms_${replace(each.value, "-", "_")}"
    description = "Allows only reviewed grants on this exact production signing key"
    expression = format(
      "resource.type == 'cloudkms.googleapis.com/CryptoKey' && resource.name == 'projects/%s/locations/%s/keyRings/%s/cryptoKeys/%s' && %s",
      var.project_id,
      local.production_region,
      local.production_kms_key_ring,
      each.value,
      local.kms_modified_roles_condition,
    )
  }

  depends_on = [
    google_project_iam_custom_role.resource_iam_setter,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "infrastructure_secret_policy_setter" {
  for_each = var.enable_production_project_bootstrap ? local.production_secret_names : toset([])

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.resource_iam_setter_role_names.secret
  member  = "serviceAccount:${local.infrastructure_identities.apply.email}"

  condition {
    title       = "limit_secret_${replace(each.value, "-", "_")}"
    description = "Allows only secret-accessor grants on this exact production secret"
    expression = format(
      "resource.type == 'secretmanager.googleapis.com/Secret' && resource.name == 'projects/%s/secrets/%s' && %s",
      google_project.production[0].number,
      each.value,
      local.secret_modified_roles_condition,
    )
  }

  depends_on = [
    google_project_iam_custom_role.resource_iam_setter,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "infrastructure_registry_storage_policy_setter" {
  for_each = var.enable_production_project_bootstrap ? local.production_registry_buckets : toset([])

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.resource_iam_setter_role_names.storage
  member  = "serviceAccount:${local.infrastructure_identities.apply.email}"

  condition {
    title       = "limit_bucket_${replace(each.value, "-", "_")}"
    description = "Allows only reviewed registry data grants on this exact production bucket"
    expression = format(
      "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s",
      each.value,
      local.registry_storage_modified_roles_condition,
    )
  }

  depends_on = [
    google_project_iam_custom_role.resource_iam_setter,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "infrastructure_state_storage_policy_setter" {
  for_each = var.enable_production_project_bootstrap ? local.state_backends : {}

  provider = google.project

  project = google_project.production[0].project_id
  role    = local.resource_iam_setter_role_names.storage
  member  = "serviceAccount:${local.infrastructure_identities.apply.email}"

  condition {
    title       = "limit_${each.key}_state_bucket_roles"
    description = "Allows only exact backend-state role changes on this dedicated state bucket"
    expression = format(
      "resource.type == 'storage.googleapis.com/Bucket' && resource.name == 'projects/_/buckets/%s' && %s",
      each.value.bucket,
      local.state_storage_modified_roles_condition,
    )
  }

  depends_on = [
    google_project_iam_custom_role.resource_iam_setter,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "infrastructure_run_policy_setter" {
  for_each = var.enable_production_project_bootstrap ? local.project_run_iam_setter_bindings : {}

  provider = google.project

  project = google_project.production[0].project_id
  role    = each.value.role
  member  = "serviceAccount:${local.infrastructure_identities.apply.email}"

  condition {
    title       = each.value.title
    description = each.value.description
    expression  = each.value.modified_roles_condition
  }

  depends_on = [
    google_project_iam_custom_role.resource_iam_setter,
    google_service_account.infrastructure,
  ]
}

locals {
  organization_refresh_role_name    = "organizations/${var.organization_id}/roles/seenRegistryBootstrapRefresh"
  billing_refresh_role_name         = "organizations/${var.organization_id}/roles/seenRegistryBillingRefresh"
  control_project_refresh_role_name = "organizations/${var.organization_id}/roles/seenRegistryControlProjectRefresh"
  ci_organization_read_roles = toset([
    local.organization_refresh_role_name,
    "roles/orgpolicy.policyViewer",
  ])
  ci_control_project_roles = toset([
    local.control_project_refresh_role_name,
    "roles/serviceusage.serviceUsageConsumer",
    "roles/serviceusage.serviceUsageViewer",
  ])
  ci_organization_read_bindings = {
    for pair in setproduct(keys(local.infrastructure_identities), local.ci_organization_read_roles) :
    "${pair[0]}:${pair[1]}" => {
      identity = pair[0]
      role     = pair[1]
    }
  }
  ci_control_project_bindings = {
    for pair in setproduct(keys(local.infrastructure_identities), local.ci_control_project_roles) :
    "${pair[0]}:${pair[1]}" => {
      identity = pair[0]
      role     = pair[1]
    }
  }
}

resource "google_organization_iam_custom_role" "bootstrap_refresh" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  org_id      = var.organization_id
  role_id     = "seenRegistryBootstrapRefresh"
  title       = "Seen Registry Bootstrap Refresh"
  description = "Read-only IAM policy and custom-role metadata required to refresh the bootstrap root"
  permissions = [
    "iam.roles.get",
    "resourcemanager.organizations.get",
    "resourcemanager.organizations.getIamPolicy",
  ]
  stage = "GA"

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_organization_iam_custom_role" "billing_refresh" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  org_id      = var.organization_id
  role_id     = "seenRegistryBillingRefresh"
  title       = "Seen Registry Billing Association Refresh"
  description = "Reads only the exact billing account IAM policy and project associations needed for steady project refresh"
  permissions = [
    "billing.accounts.getIamPolicy",
    "billing.budgets.get",
    "billing.resourceAssociations.list",
  ]
  stage = "GA"

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_organization_iam_custom_role" "control_project_refresh" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  org_id      = var.organization_id
  role_id     = "seenRegistryControlProjectRefresh"
  title       = "Seen Registry Control Project Refresh"
  description = "Reads only project metadata and IAM policy when bound on the exact bootstrap control project"
  permissions = [
    "resourcemanager.projects.get",
    "resourcemanager.projects.getIamPolicy",
  ]
  stage = "GA"

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_organization_iam_member" "infrastructure_read" {
  for_each = var.enable_production_project_bootstrap ? local.ci_organization_read_bindings : {}

  org_id = var.organization_id
  role   = each.value.role
  member = "serviceAccount:${local.infrastructure_identities[each.value.identity].email}"

  depends_on = [
    google_organization_iam_custom_role.bootstrap_refresh,
    google_service_account.infrastructure,
  ]
}

resource "google_billing_account_iam_member" "infrastructure_read" {
  for_each = var.enable_production_project_bootstrap ? local.infrastructure_identities : {}

  billing_account_id = var.billing_account_id
  role               = local.billing_refresh_role_name
  member             = "serviceAccount:${each.value.email}"

  depends_on = [
    google_organization_iam_custom_role.billing_refresh,
    google_service_account.infrastructure,
  ]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_member" "infrastructure_control_project" {
  for_each = var.enable_production_project_bootstrap ? local.ci_control_project_bindings : {}

  project = var.control_project_id
  role    = each.value.role
  member  = "serviceAccount:${local.infrastructure_identities[each.value.identity].email}"

  depends_on = [
    google_organization_iam_custom_role.control_project_refresh,
    google_project_service.control,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_member" "project_creator_owner" {
  for_each = local.project_creator_owner_members

  provider = google.project

  project = google_project.production[0].project_id
  role    = "roles/owner"
  member  = each.value

  lifecycle {
    precondition {
      condition = try(
        each.value == "user:${data.google_client_openid_userinfo.bootstrap[0].email}",
        false,
      )
      error_message = "The imported Owner member must be the active reviewed project creator."
    }
  }
}

import {
  for_each = local.project_creator_owner_members

  to = google_project_iam_member.project_creator_owner[each.value]
  id = "${var.project_id} roles/owner ${each.value}"
}

resource "google_org_policy_policy" "managed_allowed_policy_members" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  name   = "projects/${google_project.production[0].number}/policies/iam.managed.allowedPolicyMembers"
  parent = "projects/${google_project.production[0].number}"

  spec {
    rules {
      enforce = "TRUE"
      parameters = jsonencode({
        allowedMemberSubjects = sort(concat(
          concat(
            [local.portfolio_gateway_member],
            sort(tolist(var.bootstrap_operator_members)),
          ),
          [
            for identity, config in local.github_identities :
            "principal://iam.googleapis.com/${google_iam_workload_identity_pool.infrastructure[0].name}/subject/${config.subject}"
          ],
        ))
        allowedPrincipalSets = [local.organization_principal_set]
      })
    }
  }

  depends_on = [
    google_project_iam_member.policy_admin_lease,
    google_project_service.bootstrap,
  ]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_org_policy_policy" "legacy_domain_restriction_override" {
  count = var.enable_production_project_bootstrap && var.enable_portfolio_gateway_exception ? 1 : 0

  provider = google.project

  name   = "projects/${google_project.production[0].number}/policies/iam.allowedPolicyMemberDomains"
  parent = "projects/${google_project.production[0].number}"

  spec {
    inherit_from_parent = false
    rules {
      allow_all = "TRUE"
    }
  }

  depends_on = [google_org_policy_policy.managed_allowed_policy_members]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_storage_bucket" "state" {
  for_each = var.enable_production_project_bootstrap ? local.state_backends : {}

  provider = google.project

  project                     = google_project.production[0].project_id
  name                        = each.value.bucket
  location                    = "US-CENTRAL1"
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false
  labels = merge(local.common_labels, {
    purpose = "registry-${each.key}-state"
  })

  versioning {
    enabled = true
  }

  soft_delete_policy {
    retention_duration_seconds = 2592000
  }

  lifecycle_rule {
    condition {
      days_since_noncurrent_time = 90
      with_state                 = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}

data "google_iam_policy" "state" {
  for_each = var.enable_production_project_bootstrap ? local.state_backend_resources : {}

  binding {
    role = local.state_role_names.reader
    members = concat(
      [
        "serviceAccount:${local.infrastructure_identities.apply.email}",
        "serviceAccount:${local.infrastructure_identities.plan.email}",
      ],
      var.enable_temporary_human_state_migration_access ? sort(tolist(var.bootstrap_operator_members)) : [],
    )
  }

  binding {
    role    = local.state_role_names.locker
    members = ["serviceAccount:${local.infrastructure_identities.plan.email}"]

    condition {
      title       = "exact_${each.key}_plan_lock"
      description = "Allows the planner to create and delete only this root's exact lock object"
      expression  = "resource.name == '${each.value.lock_resource}'"
    }
  }

  binding {
    role = local.state_role_names.writer
    members = concat(
      ["serviceAccount:${local.infrastructure_identities.apply.email}"],
      var.enable_temporary_human_state_migration_access ? sort(tolist(var.bootstrap_operator_members)) : [],
    )

    condition {
      title       = "exact_${each.key}_state_mutations"
      description = "Allows mutation of only this root's exact state and lock objects"
      expression = join(" || ", [
        "resource.name == '${each.value.state_resource}'",
        "resource.name == '${each.value.lock_resource}'",
      ])
    }
  }

}

resource "google_storage_bucket_iam_policy" "state" {
  for_each = var.enable_production_project_bootstrap ? local.state_backends : {}

  provider = google.project

  bucket      = google_storage_bucket.state[each.key].name
  policy_data = data.google_iam_policy.state[each.key].policy_data

  depends_on = [
    google_project_iam_custom_role.state_locker,
    google_project_iam_custom_role.state_reader,
    google_project_iam_custom_role.state_writer,
    google_project_iam_custom_role.resource_iam_setter,
    google_service_account.infrastructure,
  ]
}

resource "google_project_iam_audit_config" "storage" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project = google_project.production[0].project_id
  service = "storage.googleapis.com"

  audit_log_config {
    log_type = "DATA_READ"
  }

  audit_log_config {
    log_type = "DATA_WRITE"
  }
}

resource "google_monitoring_notification_channel" "operations" {
  count = var.enable_production_project_bootstrap ? 1 : 0

  provider = google.project

  project      = google_project.production[0].project_id
  display_name = "Seen Registry Production Operations"
  description  = "Primary production registry alert destination"
  type         = "email"
  enabled      = true
  force_delete = false
  labels = {
    email_address = var.notification_email
  }
  user_labels = local.common_labels

  depends_on = [google_project_service.bootstrap]

  lifecycle {
    prevent_destroy = true
  }
}
