provider "google" {
  alias   = "portfolio_dev"
  project = "portfolio-476219"
}

/**
 * The Cloudflare dev Container already has a separate conditional binding for
 * `(default)`. Dual-write startup also needs the same runtime identity to read
 * and write only the Dammam dev database. Keep this as a second exact-resource
 * condition so neither binding broadens into project-wide Firestore access.
 */
resource "google_project_iam_member" "portfolio_dev_dammam_firestore" {
  count    = var.resources_enabled ? 1 : 0
  provider = google.portfolio_dev

  project = "portfolio-476219"
  role    = "roles/datastore.user"
  member  = "serviceAccount:portfolio-dev-runtime@portfolio-476219.iam.gserviceaccount.com"

  condition {
    title       = "portfolio-dev-dammam-firestore"
    description = "Portfolio dev Dammam Firestore only"
    expression  = "resource.name == 'projects/portfolio-476219/databases/portfolio-me-dev'"
  }

  lifecycle {
    prevent_destroy = true
  }
}
