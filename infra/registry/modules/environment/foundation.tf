resource "google_project_service" "required" {
  for_each = var.enabled ? local.required_services : toset([])

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}
resource "google_artifact_registry_repository" "registry" {
  count = var.enabled && var.create_artifact_repository ? 1 : 0

  project       = var.project_id
  location      = var.region
  repository_id = var.artifact_repository
  description   = "Isolated Seen registry container images"
  format        = "DOCKER"
  labels        = local.common_labels

  cleanup_policy_dry_run = true

  depends_on = [google_project_service.required]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_artifact_registry_repository_iam_member" "infrastructure_executor_reader" {
  count = var.enabled && var.create_artifact_repository && var.infrastructure_executor_service_account != null ? 1 : 0

  project    = var.project_id
  location   = var.region
  repository = google_artifact_registry_repository.registry[0].repository_id
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${var.infrastructure_executor_service_account}"
}

resource "google_firestore_database" "registry" {
  count = var.enabled && var.create_firestore_database ? 1 : 0

  project                     = var.project_id
  name                        = var.firestore_database
  location_id                 = var.firestore_location
  type                        = "FIRESTORE_NATIVE"
  concurrency_mode            = "PESSIMISTIC"
  app_engine_integration_mode = "DISABLED"
  delete_protection_state     = "DELETE_PROTECTION_ENABLED"
  deletion_policy             = "ABANDON"

  depends_on = [google_project_service.required]
}

resource "google_compute_network" "registry" {
  count = var.enabled ? 1 : 0

  project                 = var.project_id
  name                    = "${var.name_prefix}-network"
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"

  depends_on = [google_project_service.required]
}

resource "google_compute_subnetwork" "registry" {
  count = var.enabled ? 1 : 0

  project                  = var.project_id
  region                   = var.region
  name                     = "${var.name_prefix}-run"
  network                  = google_compute_network.registry[0].id
  ip_cidr_range            = var.network_cidr
  private_ip_google_access = true

  log_config {
    aggregation_interval = "INTERVAL_5_SEC"
    flow_sampling        = 0.5
    metadata             = "INCLUDE_ALL_METADATA"
  }
}

resource "google_compute_router" "registry" {
  count = var.enabled ? 1 : 0

  project = var.project_id
  region  = var.region
  name    = "${var.name_prefix}-router"
  network = google_compute_network.registry[0].id
}

resource "google_compute_router_nat" "registry" {
  count = var.enabled ? 1 : 0

  project                            = var.project_id
  region                             = var.region
  name                               = "${var.name_prefix}-nat"
  router                             = google_compute_router.registry[0].name
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "LIST_OF_SUBNETWORKS"

  subnetwork {
    name                    = google_compute_subnetwork.registry[0].id
    source_ip_ranges_to_nat = ["ALL_IP_RANGES"]
  }

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
}
