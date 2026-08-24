module "platform" {
  source = "../../modules/platform"

  cloudflare_account_id = var.cloudflare_account_id
  environment           = "prod"
  name_prefix           = var.name_prefix
  resources_enabled     = var.resources_enabled
  portfolio_r2_location = var.portfolio_r2_location
  seen_r2_location      = var.seen_r2_location
  zones                 = var.zones
}
