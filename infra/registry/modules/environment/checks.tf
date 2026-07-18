check "bootstrap_authority_split" {
  assert {
    condition = (
      !contains([for binding in local.signer_authorizations : binding.identity], "offline_bootstrap_importer") &&
      (
        contains(var.ceremony_operations, "online_bootstrap") ==
        contains([for binding in local.signer_authorizations : binding.identity], "online_bootstrap")
      ) &&
      toset([for binding in values(local.bootstrap_metadata_objects) : binding.filename if binding.identity == "offline_bootstrap_importer"]) == toset(["1.root.json", "1.targets.json"]) &&
      !contains([for binding in values(local.bootstrap_metadata_objects) : binding.filename if binding.identity == "online_bootstrap"], "root.json") &&
      !contains(local.firestore_users, "offline_bootstrap_importer")
    )
    error_message = "Offline bootstrap import and online signing authority must remain disjoint."
  }
}

check "inactive_ceremonies_have_no_authority" {
  assert {
    condition = alltrue([
      for identity in setsubtract(toset([
        "offline_bootstrap_importer",
        "online_bootstrap",
        "targets_renewal",
        "targets_releases_rotation",
        "targets_security_rotation",
        "root_importer",
        ]), var.ceremony_operations) : (
        !contains(local.firestore_users, identity) &&
        !contains(local.metadata_readers, identity) &&
        !contains(keys(local.metadata_creator_suffixes), identity) &&
        !contains([for binding in local.signer_authorizations : binding.identity], identity) &&
        !contains([for request in values(local.secret_access_requests) : request.identity], identity)
      )
    ])
    error_message = "An unselected ceremony identity retained data, secret, or signer-invocation authority."
  }
}

check "signers_use_exact_versions" {
  assert {
    condition = !var.enabled || !var.workloads_enabled || alltrue([
      for role in local.roles : endswith(
        local.online_key_versions[role],
        "/cryptoKeyVersions/${var.online_key_version_numbers[role]}",
      )
    ])
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
    condition = (
      (!var.schedules_enabled || var.workloads_enabled) &&
      (!var.refresh_jobs_enabled || var.workloads_enabled) &&
      (length(var.ceremony_operations) == 0 || var.workloads_enabled)
    )
    error_message = "Schedules, refresh jobs, and ceremonies require workloads_enabled."
  }
}

check "signer_oidc_egress_is_dev_only" {
  assert {
    condition     = !var.signer_jwks_all_apis_enabled || var.environment == "dev"
    error_message = "The all-APIs signer OIDC egress exception is limited to the development canary."
  }
}

check "signer_oidc_egress_shape" {
  assert {
    condition = !var.enabled || !var.signer_jwks_all_apis_enabled || (
      try(google_dns_record_set.oidc_certificates_private[0].name, null) == "www.googleapis.com." &&
      toset(try(google_dns_record_set.oidc_certificates_private[0].rrdatas, [])) == toset(["private.googleapis.com."]) &&
      toset(try(google_compute_firewall.oidc_certificates_private_egress[0].destination_ranges, [])) == toset(["199.36.153.8/30"]) &&
      toset(try(google_compute_firewall.oidc_certificates_private_egress[0].target_tags, [])) == toset(["${var.name_prefix}-signer"]) &&
      try(one(google_compute_firewall.oidc_certificates_private_egress[0].allow).protocol, null) == "tcp" &&
      toset(try(one(google_compute_firewall.oidc_certificates_private_egress[0].allow).ports, [])) == toset(["443"]) &&
      toset(try(google_compute_firewall.restricted_egress_deny[0].destination_ranges, [])) == toset(["0.0.0.0/0"]) &&
      toset(try(google_compute_firewall.restricted_egress_deny[0].target_tags, [])) == toset(["${var.name_prefix}-restricted-egress"])
    )
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
    error_message = "Workloads require API/action token versions, one complete source credential, and the existing gateway service account."
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
      can(regex("^[^/]+/[^/]+$", var.github_repository)) &&
      can(regex("^[1-9][0-9]*$", var.github_repository_id)) &&
      can(regex("^[1-9][0-9]*$", var.github_repository_owner_id)) &&
      var.github_ref != null &&
      try(startswith(var.github_ref, "refs/"), false) &&
      var.github_workflow_ref != null &&
      try(endswith(var.github_workflow_ref, "@${var.github_ref}"), false) &&
      var.github_event_name != null &&
      can(regex("^[a-z_]+$", var.github_event_name)) &&
      var.github_environment != null &&
      can(regex("^[A-Za-z0-9._-]+$", var.github_environment))
    )
    error_message = "GitHub CI requires an enabled Artifact Registry plus exact repository/owner IDs, repository, ref, workflow, event, and protected environment."
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
