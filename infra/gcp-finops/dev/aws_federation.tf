# Keyless BigQuery billing reads for the Portfolio dev SSR task on AWS. A
# separate pool keeps this trust boundary independent of the Cloudflare
# Access federation above.
resource "google_iam_workload_identity_pool" "aws_portfolio" {
  count = var.resources_enabled && var.aws_federation_enabled ? 1 : 0

  project                   = var.project_id
  workload_identity_pool_id = "aws-portfolio-dev"
  display_name              = "AWS Portfolio dev"
  description               = "Trust boundary for the exact Portfolio dev SSR ECS task role."
  disabled                  = false

  depends_on = [google_project_service.finops]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_iam_workload_identity_pool_provider" "aws_portfolio" {
  count = var.resources_enabled && var.aws_federation_enabled ? 1 : 0

  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.aws_portfolio[0].workload_identity_pool_id
  workload_identity_pool_provider_id = "ssr-task"
  display_name                       = "Portfolio dev SSR task"
  description                        = "Accepts only the Portfolio dev SSR ECS task role."
  disabled                           = false
  attribute_mapping = {
    "google.subject"     = "assertion.arn"
    "attribute.aws_role" = "assertion.arn.extract('assumed-role/{role}/')"
  }
  attribute_condition = join(" && ", [
    "assertion.account == '${var.aws_account_id}'",
    "assertion.arn.startsWith('arn:aws:sts::${var.aws_account_id}:assumed-role/${var.aws_role_name}/')",
  ])

  aws {
    account_id = var.aws_account_id
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account_iam_member" "aws_wif_user" {
  count = var.resources_enabled && var.aws_federation_enabled ? 1 : 0

  service_account_id = "projects/${var.project_id}/serviceAccounts/${local.finops_reader_email}"
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.aws_portfolio[0].name}/attribute.aws_role/${var.aws_role_name}"

  depends_on = [google_service_account.finops_reader]
}

locals {
  # Non-secret external-account configuration for GCP_FINOPS_WIF_CREDENTIAL_CONFIG_JSON.
  aws_credential_config = var.resources_enabled && var.aws_federation_enabled ? jsonencode({
    type                              = "external_account"
    audience                          = "//iam.googleapis.com/${google_iam_workload_identity_pool_provider.aws_portfolio[0].name}"
    subject_token_type                = "urn:ietf:params:aws:token-type:aws4_request"
    token_url                         = "https://sts.googleapis.com/v1/token"
    service_account_impersonation_url = "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${local.finops_reader_email}:generateAccessToken"
    credential_source = {
      environment_id                 = "aws1"
      regional_cred_verification_url = "https://sts.{region}.amazonaws.com?Action=GetCallerIdentity&Version=2011-06-15"
    }
  }) : null
}
