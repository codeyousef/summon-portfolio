resource "google_dns_managed_zone" "restricted_googleapis" {
  count = var.enabled ? 1 : 0

  project     = var.project_id
  name        = "${replace(var.name_prefix, "-", "-")}-restricted-googleapis"
  dns_name    = "googleapis.com."
  description = "Routes Google API traffic through the restricted Private Google Access VIP."
  visibility  = "private"

  private_visibility_config {
    networks {
      network_url = google_compute_network.registry[0].id
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_dns_record_set" "restricted_googleapis_a" {
  count = var.enabled ? 1 : 0

  project      = var.project_id
  managed_zone = google_dns_managed_zone.restricted_googleapis[0].name
  name         = "restricted.googleapis.com."
  type         = "A"
  ttl          = 300
  rrdatas      = ["199.36.153.4", "199.36.153.5", "199.36.153.6", "199.36.153.7"]
}

resource "google_dns_record_set" "restricted_googleapis_wildcard" {
  count = var.enabled ? 1 : 0

  project      = var.project_id
  managed_zone = google_dns_managed_zone.restricted_googleapis[0].name
  name         = "*.googleapis.com."
  type         = "CNAME"
  ttl          = 300
  rrdatas      = ["restricted.googleapis.com."]
}

resource "google_compute_firewall" "restricted_googleapis_egress" {
  count = var.enabled ? 1 : 0

  project            = var.project_id
  name               = "${var.name_prefix}-allow-restricted-googleapis"
  network            = google_compute_network.registry[0].name
  direction          = "EGRESS"
  priority           = 900
  destination_ranges = ["199.36.153.4/30"]
  target_tags        = ["${var.name_prefix}-restricted-egress"]

  allow {
    protocol = "tcp"
    ports    = ["443"]
  }

  log_config {
    metadata = "INCLUDE_ALL_METADATA"
  }
}

resource "google_compute_firewall" "restricted_egress_deny" {
  count = var.enabled ? 1 : 0

  project            = var.project_id
  name               = "${var.name_prefix}-deny-other-egress"
  network            = google_compute_network.registry[0].name
  direction          = "EGRESS"
  priority           = 1000
  destination_ranges = ["0.0.0.0/0"]
  target_tags        = ["${var.name_prefix}-restricted-egress"]

  deny {
    protocol = "all"
  }

  log_config {
    metadata = "INCLUDE_ALL_METADATA"
  }
}
