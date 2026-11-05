module "platform" {
  source = "../../modules/platform"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  aws_account_id         = var.aws_account_id
  environment            = "prod"
  region                 = var.region
  resources_enabled      = var.resources_enabled
  certificates_validated = var.certificates_validated
  images                 = var.images
  schedules_enabled      = var.schedules_enabled
  app_environment        = merge(var.app_environment, module.registry.gateway_environment)

  # FinOps billing ingestion is development-only; production needs only the
  # docs token.
  app_secret_names = ["GITHUB_TOKEN"]

  site_lambda               = var.site_lambda
  site_lambda_origin_secret = var.site_lambda_origin_secret

  service_sizes = {
    ssr_desired_count = 1
  }
}

module "registry" {
  source = "../../modules/registry"

  aws_account_id            = var.aws_account_id
  environment               = "prod"
  region                    = var.region
  enabled                   = var.registry.enabled
  kms_keys_enabled          = var.registry.kms_keys_enabled
  certificates_validated    = var.registry.certificates_validated
  network                   = module.platform.network
  image                     = var.registry.image
  signer_image              = var.registry.signer_image
  online_public_keys_hex    = var.registry.online_public_keys_hex
  trusted_root_v1_sha256    = var.registry.trusted_root_v1_sha256
  schedules_enabled         = var.registry.schedules_enabled
  refresh_schedules_enabled = var.registry.refresh_schedules_enabled
  api_lambda_enabled        = var.registry.api_lambda_enabled
  signer_lambda_enabled     = var.registry.signer_lambda_enabled
  signer_lambda_serving     = var.registry.signer_lambda_serving
  api_lambda_serving        = var.registry.api_lambda_serving
}
