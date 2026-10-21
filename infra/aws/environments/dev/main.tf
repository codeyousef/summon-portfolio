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
  app_environment      = var.app_environment
}
