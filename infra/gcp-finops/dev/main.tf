locals {
  required_services = toset([
    "bigquery.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
  ])
  standard_export_table_id  = var.billing_account_id == null ? null : "gcp_billing_export_v1_${replace(var.billing_account_id, "-", "_")}"
  finops_reader_email       = "finops-reader@${var.project_id}.iam.gserviceaccount.com"
  billing_source_dataset_id = coalesce(var.billing_source_dataset_id, var.billing_dataset_id)
}

resource "google_project_service" "finops" {
  for_each = var.resources_enabled ? local.required_services : toset([])

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_bigquery_dataset" "billing_export" {
  count = var.resources_enabled ? 1 : 0

  project                    = var.project_id
  dataset_id                 = var.billing_dataset_id
  friendly_name              = "Felidai Studio dev billing export"
  description                = "Standard Cloud Billing export queried once daily by the dev FinOps reader."
  location                   = var.billing_dataset_location
  delete_contents_on_destroy = false
  labels = {
    environment = "dev"
    purpose     = "finops"
  }

  depends_on = [google_project_service.finops]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account" "finops_reader" {
  count = var.resources_enabled ? 1 : 0

  project      = var.project_id
  account_id   = "finops-reader"
  display_name = "Felidai Studio dev FinOps billing reader"
  description  = "Keyless read-only identity for bounded Cloudflare Worker billing-export queries."

  depends_on = [google_project_service.finops]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_iam_workload_identity_pool" "cloudflare" {
  count = var.resources_enabled ? 1 : 0

  project                   = var.project_id
  workload_identity_pool_id = "cloudflare-dev"
  display_name              = "Cloudflare dev"
  description               = "Trust boundary for the exact Felidai Studio dev Access service token."
  disabled                  = false

  depends_on = [google_project_service.finops]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_iam_workload_identity_pool_provider" "access" {
  count = var.resources_enabled ? 1 : 0

  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.cloudflare[0].workload_identity_pool_id
  workload_identity_pool_provider_id = "access-dev"
  display_name                       = "Cloudflare Access dev"
  description                        = "Accepts only the dedicated dev WIF broker Access application token."
  disabled                           = false
  attribute_mapping = {
    "google.subject" = "assertion.common_name"
  }
  attribute_condition = join(" && ", [
    "assertion.common_name == '${var.access_service_token_client_id}'",
    "assertion.type == 'app'",
    "assertion.sub == ''",
  ])

  oidc {
    issuer_uri        = var.access_issuer
    allowed_audiences = [var.access_application_audience]
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account_iam_member" "wif_user" {
  count = var.resources_enabled ? 1 : 0

  service_account_id = "projects/${var.project_id}/serviceAccounts/${local.finops_reader_email}"
  role               = "roles/iam.workloadIdentityUser"
  member             = "principal://iam.googleapis.com/${google_iam_workload_identity_pool.cloudflare[0].name}/subject/${var.access_service_token_client_id}"

  depends_on = [google_service_account.finops_reader]
}

resource "google_project_iam_member" "bigquery_job_user" {
  count = var.resources_enabled ? 1 : 0

  project = var.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${local.finops_reader_email}"

  depends_on = [google_service_account.finops_reader]
}

resource "google_bigquery_dataset_iam_member" "billing_data_viewer" {
  count = var.resources_enabled ? 1 : 0

  project    = var.project_id
  dataset_id = local.billing_source_dataset_id
  role       = "roles/bigquery.dataViewer"
  member     = "serviceAccount:${local.finops_reader_email}"

  depends_on = [google_bigquery_dataset.billing_export, google_service_account.finops_reader]
}
