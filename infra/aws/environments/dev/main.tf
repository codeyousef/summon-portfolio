module "platform" {
  source = "../../modules/platform"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  aws_account_id       = var.aws_account_id
  environment          = "dev"
  region               = var.region
  resources_enabled    = var.resources_enabled
  dev_gate_owner_email = var.dev_gate_owner_email
  images               = var.images
  use_fargate_spot     = true
  schedules_enabled    = var.schedules_enabled
  app_environment      = merge(var.app_environment, module.registry.gateway_environment)
}

module "registry" {
  source = "../../modules/registry"

  aws_account_id            = var.aws_account_id
  environment               = "dev"
  region                    = var.region
  enabled                   = var.registry.enabled
  kms_keys_enabled          = var.registry.kms_keys_enabled
  network                   = module.platform.network
  image                     = var.registry.image
  signer_image              = var.registry.signer_image
  online_public_keys_hex    = var.registry.online_public_keys_hex
  trusted_root_v1_sha256    = var.registry.trusted_root_v1_sha256
  schedules_enabled         = var.registry.schedules_enabled
  refresh_schedules_enabled = var.registry.refresh_schedules_enabled
}
