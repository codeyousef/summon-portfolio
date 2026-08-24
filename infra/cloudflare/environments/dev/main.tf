module "platform" {
  source = "../../modules/platform"

  cloudflare_account_id     = var.cloudflare_account_id
  environment               = "dev"
  name_prefix               = var.name_prefix
  resources_enabled         = var.resources_enabled
  access_enabled            = var.access_enabled
  access_auth_domain        = var.access_auth_domain
  access_owner_email        = var.access_owner_email
  access_application_domain = var.access_application_domain
  finops_wif_access_enabled = var.finops_wif_access_enabled
  portfolio_r2_location     = var.portfolio_r2_location
  seen_r2_location          = var.seen_r2_location
  zones                     = var.zones
}
