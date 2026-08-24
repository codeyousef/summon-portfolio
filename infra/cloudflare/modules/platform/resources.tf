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

resource "cloudflare_zero_trust_access_policy" "finops_owner" {
  count = var.resources_enabled && var.access_enabled ? 1 : 0

  account_id       = var.cloudflare_account_id
  name             = "Felidai Studio FinOps owner"
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

resource "cloudflare_zero_trust_access_application" "finops" {
  for_each = var.resources_enabled && var.access_enabled ? {
    dashboard = "/admin/spending*"
    api       = "/api/admin/finops/*"
  } : {}

  account_id                 = var.cloudflare_account_id
  type                       = "self_hosted"
  name                       = "Felidai Studio FinOps ${each.key} (${var.environment})"
  domain                     = "${var.access_application_domain}${each.value}"
  session_duration           = "12h"
  app_launcher_visible       = false
  http_only_cookie_attribute = true
  # The dashboard and API are distinct Access applications on one hostname.
  # Scope each JWT to its application path so their different audiences do not
  # overwrite the same hostname-wide CF_Authorization cookie and cause a login
  # redirect loop.
  path_cookie_attribute = true
  # Access authentication returns from the Cloudflare identity domain via a
  # top-level navigation. Strict prevents the application token from being
  # sent on that navigation and Cloudflare documents the resulting symptom as
  # ERR_TOO_MANY_REDIRECTS. Lax preserves CSRF protection for subrequests while
  # allowing the authenticated top-level GET callback flow.
  same_site_cookie_attribute = "lax"
  policies = [{
    id         = cloudflare_zero_trust_access_policy.finops_owner[0].id
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
