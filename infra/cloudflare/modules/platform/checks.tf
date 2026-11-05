check "resource_creation_has_an_account" {
  assert {
    condition     = !var.resources_enabled || var.cloudflare_account_id != null
    error_message = "resources_enabled requires cloudflare_account_id."
  }
}

check "access_configuration_is_complete_and_dev_only" {
  assert {
    condition = !var.access_enabled || (
      var.resources_enabled &&
      var.environment == "dev" &&
      var.cloudflare_account_id != null &&
      var.access_auth_domain != null &&
      var.access_owner_email != null &&
      var.access_identity_provider_id != null &&
      var.access_application_domain != null &&
      length(var.access_dev_domains) > 0
    )
    error_message = "Access requires enabled dev resources plus an account, team domain, owner email, existing identity provider, and complete dev hostname set."
  }
}

check "finops_wif_access_is_complete_and_dev_only" {
  assert {
    condition = !var.finops_wif_access_enabled || (
      var.resources_enabled &&
      var.access_enabled &&
      var.environment == "dev" &&
      var.cloudflare_account_id != null &&
      var.access_auth_domain != null &&
      var.access_application_domain != null
    )
    error_message = "The FinOps WIF broker requires the complete enabled dev Access boundary."
  }
}

check "dev_access_has_required_route_keys" {
  assert {
    condition = !var.access_enabled || alltrue([
      for key in ["portfolio_apex", "portfolio_worker", "samurai", "samurai_worker"] : contains(keys(var.access_dev_domains), key)
    ])
    error_message = "Dev Access requires canonical and direct Worker domains for Portfolio and Samurai."
  }
}

check "resource_names_are_valid" {
  assert {
    condition = alltrue(concat(
      [
        for spec in values(local.r2_bucket_specs) :
        length(spec.name) <= 63 && can(regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$", spec.name))
      ],
      [
        for spec in values(local.queue_specs) :
        length(spec.name) <= 63 && can(regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$", spec.name))
      ]
    ))
    error_message = "Generated R2 bucket and Queue names must be valid and at most 63 characters."
  }
}

check "contract_references_exist" {
  assert {
    condition = alltrue(flatten([
      for worker in values(local.platform_contract.workers) : concat(
        [for key in worker.r2_bucket_keys : contains(keys(local.r2_bucket_specs), key)],
        [for key in worker.queue_keys : contains(keys(local.queue_specs), key)]
      )
    ]))
    error_message = "Every Worker binding reference must name a declared R2 bucket or Queue."
  }
}

check "r2_lock_rules_are_content_addressed" {
  assert {
    condition = alltrue(flatten([
      for spec in values(local.r2_bucket_specs) : [
        for rule in try(spec.lock_rules, []) :
        rule.enabled && endswith(rule.prefix, "/sha256/") &&
        contains(["Age", "Indefinite"], rule.condition.type) &&
        (rule.condition.type != "Age" || try(rule.condition.max_age_seconds, 0) >= 2592000)
      ]
    ]))
    error_message = "R2 Bucket Locks may cover only content-addressed prefixes and age locks must retain objects for at least 30 days."
  }
}

check "dns_is_inventory_only" {
  assert {
    condition = alltrue([
      for zone in values(var.zones) :
      length(zone.nameservers) == 2 && length(zone.dns_inventory_sha256) == 64
    ])
    error_message = "DNS contracts require a verified inventory digest and both assigned nameservers."
  }
}
