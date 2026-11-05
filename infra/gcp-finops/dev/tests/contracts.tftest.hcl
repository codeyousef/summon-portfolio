mock_provider "google" {}

mock_provider "google" {
  alias = "portfolio_dev"
}

run "inert_by_default" {
  command = plan

  variables {
    resources_enabled = false
  }

  assert {
    condition = (
      output.enabled == false &&
      length(google_project_service.finops) == 0 &&
      length(google_bigquery_dataset.billing_export) == 0 &&
      length(google_iam_workload_identity_pool.cloudflare) == 0 &&
      length(google_project_iam_member.portfolio_dev_dammam_firestore) == 0
    )
    error_message = "The dev GCP FinOps root must create nothing by default."
  }
}

run "reviewed_dev_shape" {
  command = plan

  variables {
    resources_enabled              = true
    billing_account_id             = "ABCDEF-123456-ABCDEF"
    billing_source_dataset_id      = "existing_billing_export"
    access_issuer                  = "https://felidai-studio.cloudflareaccess.com"
    access_application_audience    = "aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd"
    access_service_token_client_id = "0123456789abcdef0123456789abcdef.access"
  }

  assert {
    condition = (
      google_bigquery_dataset.billing_export[0].location == "US" &&
      google_bigquery_dataset.billing_export[0].delete_contents_on_destroy == false &&
      google_service_account.finops_reader[0].account_id == "finops-reader" &&
      google_iam_workload_identity_pool.cloudflare[0].workload_identity_pool_id == "cloudflare-dev" &&
      google_iam_workload_identity_pool_provider.access[0].workload_identity_pool_provider_id == "access-dev" &&
      google_iam_workload_identity_pool_provider.access[0].attribute_mapping["google.subject"] == "assertion.common_name" &&
      google_iam_workload_identity_pool_provider.access[0].oidc[0].issuer_uri == "https://felidai-studio.cloudflareaccess.com" &&
      google_service_account_iam_member.wif_user[0].role == "roles/iam.workloadIdentityUser" &&
      google_project_iam_member.bigquery_job_user[0].role == "roles/bigquery.jobUser" &&
      google_bigquery_dataset_iam_member.billing_data_viewer[0].role == "roles/bigquery.dataViewer" &&
      google_project_iam_member.portfolio_dev_dammam_firestore[0].project == "portfolio-476219" &&
      google_project_iam_member.portfolio_dev_dammam_firestore[0].role == "roles/datastore.user" &&
      google_project_iam_member.portfolio_dev_dammam_firestore[0].member == "serviceAccount:portfolio-dev-runtime@portfolio-476219.iam.gserviceaccount.com" &&
      google_project_iam_member.portfolio_dev_dammam_firestore[0].condition[0].expression == "resource.name == 'projects/portfolio-476219/databases/portfolio-me-dev'"
    )
    error_message = "The dev WIF and BigQuery reader must remain exact, keyless, US-based, and least-privilege."
  }

  assert {
    condition = (
      output.worker_configuration.billing_table == "gcp_billing_export_v1_ABCDEF_123456_ABCDEF" &&
      output.worker_configuration.billing_dataset == "existing_billing_export" &&
      output.worker_configuration.maximum_bytes_billed == 50000000 &&
      output.worker_configuration.authoritative_database == "firestore"
    )
    error_message = "The Worker handoff must retain the standard export table and 50 MB query cap."
  }
}

run "aws_federation_trusts_only_the_ssr_task_role" {
  command = plan

  variables {
    resources_enabled              = true
    aws_federation_enabled         = true
    billing_account_id             = "ABCDEF-123456-ABCDEF"
    billing_source_dataset_id      = "existing_billing_export"
    access_issuer                  = "https://felidai-studio.cloudflareaccess.com"
    access_application_audience    = "aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd"
    access_service_token_client_id = "0123456789abcdef0123456789abcdef.access"
  }

  assert {
    condition = (
      google_iam_workload_identity_pool_provider.aws_portfolio[0].aws[0].account_id == "266629879950" &&
      strcontains(google_iam_workload_identity_pool_provider.aws_portfolio[0].attribute_condition, "assumed-role/portfolio-dev-app-ssr-task/")
    )
    error_message = "The AWS pool must accept only the Portfolio dev SSR task role."
  }

  assert {
    condition     = google_service_account_iam_member.aws_wif_user[0].role == "roles/iam.workloadIdentityUser"
    error_message = "The AWS task may only impersonate finops-reader."
  }

  assert {
    condition     = length(google_iam_workload_identity_pool.cloudflare) == 1
    error_message = "The Cloudflare federation stays independent during the migration."
  }
}

run "aws_federation_is_separately_gated" {
  command = plan

  variables {
    resources_enabled              = true
    billing_account_id             = "ABCDEF-123456-ABCDEF"
    billing_source_dataset_id      = "existing_billing_export"
    access_issuer                  = "https://felidai-studio.cloudflareaccess.com"
    access_application_audience    = "aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd"
    access_service_token_client_id = "0123456789abcdef0123456789abcdef.access"
  }

  assert {
    condition     = length(google_iam_workload_identity_pool.aws_portfolio) == 0 && output.aws_finops_credential_config == null
    error_message = "Enabling the existing FinOps root must not create the AWS federation."
  }
}
