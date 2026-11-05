# The shared foundation owns the CI identities, so it is applied only by the
# operator's local session, never by a CI role.
module "shared" {
  source = "../../modules/shared"

  aws_account_id    = var.aws_account_id
  region            = var.region
  resources_enabled = var.resources_enabled
  dns_records       = var.dns_records
  github_repository = var.github_repository
}
