resource "cloudflare_r2_bucket" "data" {
  for_each = {
    for key, spec in local.r2_bucket_specs : key => spec
    if var.resources_enabled
  }

  account_id    = var.cloudflare_account_id
  name          = each.value.name
  location      = each.value.location
  storage_class = each.value.storage_class

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_r2_bucket_lock" "immutable_prefixes" {
  for_each = {
    for key, spec in local.r2_bucket_specs : key => spec
    if var.resources_enabled && length(try(spec.lock_rules, [])) > 0
  }

  account_id  = var.cloudflare_account_id
  bucket_name = cloudflare_r2_bucket.data[each.key].name
  rules       = each.value.lock_rules

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_queue" "work" {
  for_each = {
    for key, spec in local.queue_specs : key => spec
    if var.resources_enabled
  }

  account_id = var.cloudflare_account_id
  queue_name = each.value.name

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_zero_trust_organization" "studio" {
  count = var.resources_enabled && var.access_enabled ? 1 : 0

  account_id                         = var.cloudflare_account_id
  name                               = "Felidai Studio"
  auth_domain                        = var.access_auth_domain
  session_duration                   = "12h"
  user_seat_expiration_inactive_time = "730h"
  deny_unmatched_requests            = false
  allow_authenticate_via_warp        = false
  auto_redirect_to_identity          = true

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_zero_trust_access_policy" "dev_owner" {
  count = var.resources_enabled && var.access_enabled ? 1 : 0

  account_id       = var.cloudflare_account_id
  name             = "Felidai Studio development owner"
  decision         = "allow"
  session_duration = "12h"
  include = [{
    email = {
      email = var.access_owner_email
    }
  }]

  depends_on = [cloudflare_zero_trust_organization.studio]

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_zero_trust_access_application" "dev_sites" {
  for_each = var.resources_enabled && var.access_enabled ? var.access_dev_domains : {}

  account_id                 = var.cloudflare_account_id
  type                       = "self_hosted"
  name                       = "Felidai Studio dev ${replace(each.key, "_", " ")}"
  domain                     = each.value
  session_duration           = "12h"
  app_launcher_visible       = false
  http_only_cookie_attribute = true
  path_cookie_attribute      = false
  same_site_cookie_attribute = "lax"
  allowed_idps               = [var.access_identity_provider_id]
  auto_redirect_to_identity  = true
  policies = [{
    id         = cloudflare_zero_trust_access_policy.dev_owner[0].id
    precedence = 1
  }]

  lifecycle {
    prevent_destroy = true
  }
}

locals {
  dev_machine_tokens = var.resources_enabled && var.access_enabled ? {
    portfolio_samurai_identity = "Portfolio to Samurai identity projection"
  } : {}

  dev_machine_routes = var.resources_enabled && var.access_enabled ? {
    portfolio_samurai_identity = {
      domain    = "${var.access_dev_domains["samurai"]}/internal/portfolio/finops/identities"
      token_key = "portfolio_samurai_identity"
    }
  } : {}
}

resource "cloudflare_zero_trust_access_service_token" "dev_machine" {
  for_each = local.dev_machine_tokens

  account_id = var.cloudflare_account_id
  name       = "Felidai Studio ${each.value} (${var.environment})"
  duration   = "2160h"

  lifecycle {
    create_before_destroy = true
    prevent_destroy       = true
  }
}

resource "cloudflare_zero_trust_access_policy" "dev_machine" {
  for_each = local.dev_machine_tokens

  account_id       = var.cloudflare_account_id
  name             = "Felidai Studio ${each.value} (${var.environment})"
  decision         = "non_identity"
  session_duration = "1h"
  include = [{
    service_token = {
      token_id = cloudflare_zero_trust_access_service_token.dev_machine[each.key].id
    }
  }]

  depends_on = [cloudflare_zero_trust_organization.studio]

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_zero_trust_access_application" "dev_machine" {
  for_each = local.dev_machine_routes

  account_id                 = var.cloudflare_account_id
  type                       = "self_hosted"
  name                       = "Felidai Studio ${replace(each.key, "_", " ")} (${var.environment})"
  domain                     = each.value.domain
  session_duration           = "1h"
  app_launcher_visible       = false
  http_only_cookie_attribute = true
  path_cookie_attribute      = true
  same_site_cookie_attribute = "strict"
  policies = [{
    id         = cloudflare_zero_trust_access_policy.dev_machine[each.value.token_key].id
    precedence = 1
  }]

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_zero_trust_access_service_token" "finops_wif_broker" {
  count = var.resources_enabled && var.access_enabled && var.finops_wif_access_enabled ? 1 : 0

  account_id = var.cloudflare_account_id
  name       = "Felidai Studio FinOps WIF broker (${var.environment})"
  duration   = "2160h"

  lifecycle {
    create_before_destroy = true
    prevent_destroy       = true
  }
}

resource "cloudflare_zero_trust_access_policy" "finops_wif_broker" {
  count = var.resources_enabled && var.access_enabled && var.finops_wif_access_enabled ? 1 : 0

  account_id       = var.cloudflare_account_id
  name             = "Felidai Studio FinOps WIF broker (${var.environment})"
  decision         = "non_identity"
  session_duration = "1h"
  include = [{
    service_token = {
      token_id = cloudflare_zero_trust_access_service_token.finops_wif_broker[0].id
    }
  }]

  depends_on = [cloudflare_zero_trust_organization.studio]

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_zero_trust_access_application" "finops_wif_broker" {
  count = var.resources_enabled && var.access_enabled && var.finops_wif_access_enabled ? 1 : 0

  account_id                 = var.cloudflare_account_id
  type                       = "self_hosted"
  name                       = "Felidai Studio FinOps WIF broker (${var.environment})"
  domain                     = "${var.access_application_domain}/internal/gcp-wif/assertion"
  session_duration           = "1h"
  app_launcher_visible       = false
  http_only_cookie_attribute = true
  same_site_cookie_attribute = "strict"
  policies = [{
    id         = cloudflare_zero_trust_access_policy.finops_wif_broker[0].id
    precedence = 1
  }]

  lifecycle {
    prevent_destroy = true
  }
}
