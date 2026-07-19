locals {
  ceremony_identities = toset([
    "offline_bootstrap_importer",
    "online_bootstrap",
    "targets_renewal",
    "targets_releases_rotation",
    "targets_security_rotation",
    "root_importer",
  ])
  inactive_ceremony_identities = setsubtract(local.ceremony_identities, var.ceremony_operations)

  bootstrap_authority_split_contract = (
    !contains([for binding in local.signer_authorizations : binding.identity], "offline_bootstrap_importer") &&
    (
      contains(var.ceremony_operations, "online_bootstrap") ==
      contains([for binding in local.signer_authorizations : binding.identity], "online_bootstrap")
    ) &&
    toset([for binding in values(local.bootstrap_metadata_objects) : binding.filename if binding.identity == "offline_bootstrap_importer"]) == toset(["1.root.json", "1.targets.json"]) &&
    !contains([for binding in values(local.bootstrap_metadata_objects) : binding.filename if binding.identity == "online_bootstrap"], "root.json") &&
    !contains(local.firestore_users, "offline_bootstrap_importer")
  )

  inactive_ceremonies_have_no_authority_contract = alltrue([
    for identity in local.inactive_ceremony_identities : (
      !contains(local.firestore_users, identity) &&
      !contains(local.firestore_readers, identity) &&
      !contains(local.metadata_readers, identity) &&
      !contains(keys(local.metadata_creator_suffixes), identity) &&
      !contains([for binding in local.signer_authorizations : binding.identity], identity) &&
      !contains([for request in values(local.secret_access_requests) : request.identity], identity)
    )
  ])

  signers_use_exact_versions_contract = !var.enabled || !local.signers_enabled || alltrue([
    for role in local.selected_signer_roles : try(endswith(
      local.online_key_versions[role],
      "/cryptoKeyVersions/${var.online_key_version_numbers[role]}",
    ), false)
  ])

  signer_oidc_egress_shape_contract = !var.enabled || !var.signer_jwks_all_apis_enabled || (
    try(google_dns_record_set.oidc_certificates_private[0].name, null) == "www.googleapis.com." &&
    toset(try(google_dns_record_set.oidc_certificates_private[0].rrdatas, [])) == toset(["private.googleapis.com."]) &&
    toset(try(google_compute_firewall.oidc_certificates_private_egress[0].destination_ranges, [])) == toset(["199.36.153.8/30"]) &&
    toset(try(google_compute_firewall.oidc_certificates_private_egress[0].target_tags, [])) == toset(["${var.name_prefix}-signer"]) &&
    try(one(google_compute_firewall.oidc_certificates_private_egress[0].allow).protocol, null) == "tcp" &&
    toset(try(one(google_compute_firewall.oidc_certificates_private_egress[0].allow).ports, [])) == toset(["443"]) &&
    toset(try(google_compute_firewall.restricted_egress_deny[0].destination_ranges, [])) == toset(["0.0.0.0/0"]) &&
    toset(try(google_compute_firewall.restricted_egress_deny[0].target_tags, [])) == toset(["${var.name_prefix}-restricted-egress"])
  )

  read_only_api_contract = !local.read_only_api_enabled || (
    local.api_service.api.argument == "serve-read-only-public-api" &&
    local.api_service.api.server_mode == "read-only-public-api" &&
    length(local.api_service.api.secrets) == 0 &&
    toset(keys(local.read_only_api_environment)) == toset([
      "GOOGLE_CLOUD_PROJECT",
      "REGISTRY_ENVIRONMENT",
      "REGISTRY_FIRESTORE_DATABASE",
      "REGISTRY_METADATA_BUCKET",
      "REGISTRY_OBJECT_PREFIX",
      "REGISTRY_ORIGIN",
      "REGISTRY_PUBLIC_BUCKET",
      "REGISTRY_REPOSITORY_ID",
      "REGISTRY_STORAGE_MODE",
    ]) &&
    local.firestore_readers == toset(["api"]) &&
    !contains(local.firestore_users, "api") &&
    contains(local.metadata_readers, "api") &&
    !contains(keys(local.metadata_creator_suffixes), "api") &&
    !contains([for request in values(local.secret_access_requests) : request.identity], "api")
  )

  github_ci_claim_order = [
    "repository",
    "repository_id",
    "repository_owner_id",
    "ref",
    "workflow_ref",
    "event_name",
    "environment",
  ]
  github_ci_claim_names = toset(local.github_ci_claim_order)
  github_ci_claim_values = {
    repository          = var.github_repository == null ? "" : var.github_repository
    repository_id       = var.github_repository_id == null ? "" : var.github_repository_id
    repository_owner_id = var.github_repository_owner_id == null ? "" : var.github_repository_owner_id
    ref                 = var.github_ref == null ? "" : var.github_ref
    workflow_ref        = var.github_workflow_ref == null ? "" : var.github_workflow_ref
    event_name          = var.github_event_name == null ? "" : var.github_event_name
    environment         = var.github_environment == null ? "" : var.github_environment
  }
  github_ci_attribute_mapping = merge(
    { "google.subject" = "assertion.sub" },
    { for claim in local.github_ci_claim_names : "attribute.${claim}" => "assertion.${claim}" },
  )
  github_ci_attribute_condition_terms = [
    for claim in local.github_ci_claim_order :
    "assertion.${claim} == '${local.github_ci_claim_values[claim]}'"
  ]
  github_ci_attribute_condition = join(" && ", local.github_ci_attribute_condition_terms)
  production_ci_claim_pinning_contract = !var.github_ci_enabled || (
    toset(keys(local.github_ci_claim_values)) == local.github_ci_claim_names &&
    toset(keys(local.github_ci_attribute_mapping)) == setunion(
      toset(["google.subject"]),
      toset([for claim in local.github_ci_claim_names : "attribute.${claim}"]),
    ) &&
    local.github_ci_attribute_mapping["google.subject"] == "assertion.sub" &&
    alltrue([
      for claim in local.github_ci_claim_names :
      local.github_ci_attribute_mapping["attribute.${claim}"] == "assertion.${claim}" &&
      contains(local.github_ci_attribute_condition_terms, "assertion.${claim} == '${local.github_ci_claim_values[claim]}'")
    ]) &&
    (
      var.environment != "prod" || (
        startswith(local.github_ci_claim_values.ref, "refs/heads/") &&
        local.github_ci_claim_values.event_name == "push"
      )
    )
  )
}

check "bootstrap_authority_split" {
  assert {
    condition     = local.bootstrap_authority_split_contract
    error_message = "Offline bootstrap import and online signing authority must remain disjoint."
  }
}

check "inactive_ceremonies_have_no_authority" {
  assert {
    condition     = local.inactive_ceremonies_have_no_authority_contract
    error_message = "An unselected ceremony identity retained data, secret, or signer-invocation authority."
  }
}

check "signers_use_exact_versions" {
  assert {
    condition     = local.signers_use_exact_versions_contract
    error_message = "Every signer must use a reviewed concrete KMS key version."
  }
}

check "distinct_online_keys" {
  assert {
    condition = (
      length(var.online_key_names) == length(toset(values(var.online_key_names))) &&
      length(var.online_public_keys_hex) == length(toset(values(var.online_public_keys_hex)))
    )
    error_message = "Every online role must have a distinct key resource and public key."
  }
}

check "feature_dependencies" {
  assert {
    condition     = !var.schedules_enabled || var.workloads_enabled
    error_message = "Schedules remain available only with the complete writer-capable workload stack. Refresh and ceremony jobs are selected independently and never imply schedules."
  }
}

check "signer_oidc_egress_requires_signers" {
  assert {
    condition     = !var.signer_jwks_all_apis_enabled || (var.enabled && local.signers_enabled)
    error_message = "The explicitly gated all-APIs OIDC JWKS route may exist only while selected signer services require it."
  }
}

check "signers_require_oidc_jwks_egress" {
  assert {
    condition     = !var.enabled || !local.signers_enabled || var.signer_jwks_all_apis_enabled
    error_message = "Selected signer services require the explicitly reviewed OIDC JWKS private-VIP route in this network design."
  }
}

check "signer_oidc_egress_shape" {
  assert {
    condition     = local.signer_oidc_egress_shape_contract
    error_message = "Signer OIDC egress must retain its exact DNS, private VIP, signer tag, TCP port, and fallback deny shape."
  }
}

check "workload_secret_inputs" {
  assert {
    condition = !var.enabled || !var.workloads_enabled || (
      alltrue([
        for secret in ["publisher_token", "trust_and_safety_token", "security_token"] :
        contains(keys(var.secret_versions), secret)
      ]) &&
      (
        contains(keys(var.secret_versions), "gitlab_forge_token") ||
        (
          contains(keys(var.secret_versions), "github_app_id") &&
          contains(keys(var.secret_versions), "github_app_private_key")
        )
      ) &&
      (var.edge_cutover_enabled || var.portfolio_gateway_service_account != null)
    )
    error_message = "Writer workloads require API/action token versions, one complete source credential, and the existing gateway service account."
  }
}

check "api_gateway_input" {
  assert {
    condition = !var.enabled || !local.api_enabled || (
      var.edge_cutover_enabled || var.portfolio_gateway_service_account != null
    )
    error_message = "An enabled API requires the reviewed portfolio gateway service account unless an approved edge cutover owns invocation."
  }
}

check "read_only_api_contract" {
  assert {
    condition     = local.read_only_api_contract
    error_message = "The read-only API must retain the exact credential-free environment and read-only Firestore/metadata/public authority shape."
  }
}

check "edge_inputs" {
  assert {
    condition = !var.edge_provisioned || (
      var.workloads_enabled &&
      var.portfolio_fallback_service != null &&
      length(var.edge_certificate_domains) > 0 &&
      contains(var.edge_certificate_domains, var.uptime_host)
    )
    error_message = "The edge requires workloads, a portfolio fallback service, and a certificate containing uptime_host."
  }
}

check "edge_cutover_requires_edge" {
  assert {
    condition     = !var.edge_cutover_enabled || var.edge_provisioned
    error_message = "edge_cutover_enabled requires the edge to be provisioned first."
  }
}

check "ceremony_secret_inputs" {
  assert {
    condition = !var.enabled || alltrue([
      for secret in local.ceremony_required_secret_keys :
      contains(keys(var.secret_versions), secret)
    ])
    error_message = "Enabled ceremony jobs require reviewed numeric versions for every offline envelope secret."
  }
}

check "production_notifications" {
  assert {
    condition     = var.environment != "prod" || !var.enabled || !var.monitoring_enabled || length(var.notification_channel_ids) > 0
    error_message = "An enabled production stack must route alerts to at least one reviewed notification channel."
  }
}

check "budget_inputs" {
  assert {
    condition     = (var.billing_account_id == null) == (var.monthly_budget_usd == null)
    error_message = "billing_account_id and monthly_budget_usd must be set together."
  }
}

check "github_ci_inputs" {
  assert {
    condition = !var.github_ci_enabled || (
      var.enabled &&
      var.create_artifact_repository &&
      var.github_repository != null &&
      can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository)) &&
      can(regex("^[1-9][0-9]*$", var.github_repository_id)) &&
      can(regex("^[1-9][0-9]*$", var.github_repository_owner_id)) &&
      var.github_ref != null &&
      can(regex("^refs/heads/[A-Za-z0-9._/-]+$", var.github_ref)) &&
      var.github_workflow_ref != null &&
      can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/\\.github/workflows/[A-Za-z0-9_./-]+\\.ya?ml@refs/heads/[A-Za-z0-9._/-]+$", var.github_workflow_ref)) &&
      try(startswith(var.github_workflow_ref, "${var.github_repository}/"), false) &&
      try(endswith(var.github_workflow_ref, "@${var.github_ref}"), false) &&
      var.github_event_name != null &&
      can(regex("^[a-z_]+$", var.github_event_name)) &&
      var.github_environment != null &&
      can(regex("^[A-Za-z0-9._-]+$", var.github_environment))
    )
    error_message = "GitHub CI requires an enabled Artifact Registry plus exact repository/owner IDs, repository, ref, workflow, event, and protected environment."
  }
}

check "production_ci_claim_pinning" {
  assert {
    condition     = local.production_ci_claim_pinning_contract
    error_message = "Production image-publisher federation must retain the complete repository, immutable-ID, ref, workflow, event, and protected-environment claim set."
  }
}

check "infrastructure_executor_act_as_scope" {
  assert {
    condition = (
      !var.enabled || var.infrastructure_executor_service_account == null
      ) ? length(google_service_account_iam_member.infrastructure_executor_act_as) == 0 : (
      toset(keys(google_service_account_iam_member.infrastructure_executor_act_as)) == var.infrastructure_executor_act_as_identities &&
      length(setsubtract(local.executor_required_act_as_identities, var.infrastructure_executor_act_as_identities)) == 0 &&
      alltrue([
        for binding in values(google_service_account_iam_member.infrastructure_executor_act_as) :
        binding.role == "roles/iam.serviceAccountUser" &&
        binding.member == "serviceAccount:${var.infrastructure_executor_service_account}"
      ])
    )
    error_message = "The infrastructure executor may receive serviceAccountUser only through the explicit finite identity set, which must cover every selected deployable workload."
  }
}

check "mutable_bucket_retention" {
  assert {
    condition = (
      local.bucket_configuration.quarantine.retention_seconds == 0 &&
      local.bucket_configuration.metadata.retention_seconds == 0
    )
    error_message = "Quarantine promotion and metadata pointer CAS require no bucket retention policy; use versioning and soft-delete instead."
  }
}

check "secret_replica_keys" {
  assert {
    condition     = length(setsubtract(toset(keys(var.secret_replica_locations)), toset(keys(var.secret_names)))) == 0
    error_message = "secret_replica_locations may reference only configured secret role keys."
  }
}
