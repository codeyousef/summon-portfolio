resource "google_iam_workload_identity_pool" "github" {
  count = var.enabled && var.github_ci_enabled ? 1 : 0

  project                   = var.project_id
  workload_identity_pool_id = "${var.name_prefix}-github"
  display_name              = "Seen registry ${var.environment} GitHub CI"
  description               = "OIDC pool restricted to the reviewed registry image-publishing workflow"

  depends_on = [google_project_service.required]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  count = var.enabled && var.github_ci_enabled ? 1 : 0

  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github[0].workload_identity_pool_id
  workload_identity_pool_provider_id = "image-publisher"
  display_name                       = "Seen registry image publisher"
  description                        = "Accepts only the exact repository, branch, and workflow used for registry image publication"

  attribute_mapping   = local.github_ci_attribute_mapping
  attribute_condition = local.github_ci_attribute_condition

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }

  lifecycle {
    precondition {
      condition     = local.production_ci_claim_pinning_contract
      error_message = "Production image-publisher federation must pin every required GitHub claim, a branch ref, and the push event."
    }
  }
}

resource "google_service_account" "image_publisher" {
  count = var.enabled && var.github_ci_enabled ? 1 : 0

  project      = var.project_id
  account_id   = "${var.name_prefix}-image-pub"
  display_name = "Seen registry ${var.environment} image publisher"
  description  = "Pushes application images only; cannot deploy, impersonate runtime identities, read registry data, or sign metadata"

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account_iam_member" "github_image_publisher" {
  count = var.enabled && var.github_ci_enabled ? 1 : 0

  service_account_id = "projects/${var.project_id}/serviceAccounts/${var.name_prefix}-image-pub@${var.project_id}.iam.gserviceaccount.com"
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github[0].name}/attribute.repository_id/${var.github_repository_id}"

  depends_on = [google_service_account.image_publisher]

  lifecycle {
    precondition {
      condition     = local.production_ci_claim_pinning_contract
      error_message = "Image-publisher impersonation must remain bound to the fully claim-pinned GitHub provider and immutable repository ID."
    }
  }
}

resource "google_artifact_registry_repository_iam_member" "github_image_publisher" {
  count = var.enabled && var.github_ci_enabled && var.create_artifact_repository ? 1 : 0

  project    = var.project_id
  location   = var.region
  repository = google_artifact_registry_repository.registry[0].repository_id
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.image_publisher[0].email}"
}
