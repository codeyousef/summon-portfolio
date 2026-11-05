locals {
  # Non-secret FinOps settings carried over from the Cloudflare dev deployment.
  # Account-specific values (CLOUDFLARE_ACCOUNT_ID, SAMURAI_FINOPS_ACCESS_CLIENT_ID,
  # GCP_FINOPS_WIF_CREDENTIAL_CONFIG_JSON) come from the ignored tfvars.
  finops_environment = {
    FINOPS_COVERAGE_START_DATE           = "2026-08-24"
    FINOPS_SHARDED_ROLLUP_WRITES_ENABLED = "true"
    GCP_FINOPS_BILLING_PROJECT_ID        = "felidai-dev"
    GCP_FINOPS_BILLING_DATASET           = "felidai_billing_export"
    GCP_FINOPS_BILLING_TABLE             = "gcp_billing_export_v1_01D152_BA0DA4_4CB0D6"
    GCP_FINOPS_BILLING_LOCATION          = "US"
    GCP_FINOPS_MAX_BYTES_BILLED          = "50000000"
    SAMURAI_FINOPS_IDENTITY_URL          = "https://samurai.dev.felidai.com/internal/portfolio/finops/identities"
  }
}

module "platform" {
  source = "../../modules/platform"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  aws_account_id         = var.aws_account_id
  environment            = "dev"
  region                 = var.region
  resources_enabled      = var.resources_enabled
  certificates_validated = var.certificates_validated
  dev_gate_owner_email   = var.dev_gate_owner_email
  images                 = var.images
  load_balancer_enabled  = var.load_balancer_enabled
  service_sizes          = { ssr_desired_count = var.ssr_running ? 1 : 0 }
  use_fargate_spot       = true
  schedules_enabled      = var.schedules_enabled
  app_environment        = merge(local.finops_environment, var.app_environment, module.registry.gateway_environment)
}

module "registry" {
  source = "../../modules/registry"

  aws_account_id            = var.aws_account_id
  environment               = "dev"
  region                    = var.region
  enabled                   = var.registry.enabled
  kms_keys_enabled          = var.registry.kms_keys_enabled
  running                   = var.registry.running
  certificates_validated    = var.registry.certificates_validated
  network                   = module.platform.network
  image                     = var.registry.image
  signer_image              = var.registry.signer_image
  online_public_keys_hex    = var.registry.online_public_keys_hex
  trusted_root_v1_sha256    = var.registry.trusted_root_v1_sha256
  schedules_enabled         = var.registry.schedules_enabled
  refresh_schedules_enabled = var.registry.refresh_schedules_enabled
  data_copy                 = var.registry.data_copy
}
