output "authoritative_database" {
  value = local.platform_contract.authoritative_database
}

output "ownership" {
  value = local.platform_contract.ownership
}

output "r2_buckets" {
  value = {
    for key, spec in local.r2_bucket_specs : key => {
      name           = spec.name
      binding        = spec.binding
      location       = spec.location
      storage_class  = spec.storage_class
      lock_rules     = try(spec.lock_rules, [])
      provisioned    = var.resources_enabled
      resource_owner = "opentofu"
    }
  }
}

output "queues" {
  value = {
    for key, spec in local.queue_specs : key => {
      name                  = spec.name
      binding               = spec.binding
      dead_letter_queue_key = spec.dead_letter_queue_key
      provisioned           = var.resources_enabled
      resource_owner        = "opentofu"
      consumer_owner        = "wrangler"
    }
  }
}

output "worker_release_contracts" {
  description = "Names and bindings consumed by pinned Wrangler releases; OpenTofu does not deploy these Workers."
  value       = local.worker_release_contracts
}

output "zone_import_contracts" {
  description = "Read-only handoff for the separately reviewed DNS import phase."
  value       = var.zones
}

output "resources_enabled" {
  value = var.resources_enabled
}

output "finops_access" {
  value = {
    enabled = var.resources_enabled && var.access_enabled
    applications = {
      for key, application in cloudflare_zero_trust_access_application.finops : key => {
        id     = application.id
        domain = application.domain
      }
    }
    wif_broker = {
      enabled = var.resources_enabled && var.access_enabled && var.finops_wif_access_enabled
      application = try({
        id       = cloudflare_zero_trust_access_application.finops_wif_broker[0].id
        domain   = cloudflare_zero_trust_access_application.finops_wif_broker[0].domain
        audience = cloudflare_zero_trust_access_application.finops_wif_broker[0].aud
      }, null)
      service_token = try({
        id         = cloudflare_zero_trust_access_service_token.finops_wif_broker[0].id
        client_id  = cloudflare_zero_trust_access_service_token.finops_wif_broker[0].client_id
        expires_at = cloudflare_zero_trust_access_service_token.finops_wif_broker[0].expires_at
      }, null)
    }
  }
}

output "finops_wif_access_client_secret" {
  description = "Sensitive bootstrap value to pipe directly into the dev Worker secret; never render it in plans or artifacts."
  value       = try(cloudflare_zero_trust_access_service_token.finops_wif_broker[0].client_secret, null)
  sensitive   = true
}
