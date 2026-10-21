module "platform" {
  source = "../../modules/platform"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  aws_account_id    = var.aws_account_id
  environment       = "prod"
  region            = var.region
  resources_enabled = var.resources_enabled
  images            = var.images
  schedules_enabled = var.schedules_enabled
  app_environment   = var.app_environment

  service_sizes = {
    ssr_desired_count = 2
  }
}
