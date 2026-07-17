locals {
  edge_cloud_run_services = merge(
    {
      for key, service in local.application_services : key => service.name
    },
    var.portfolio_fallback_service == null ? {} : {
      portfolio = var.portfolio_fallback_service
    },
  )

  edge_archive_upload_match = "request.method == 'PUT' && request.path.matches('^/packages/api/v1/uploads/upl_[A-Za-z0-9_-]{16,96}/archive$')"
}

resource "google_compute_global_address" "edge" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project      = var.project_id
  name         = "${var.name_prefix}-edge"
  address_type = "EXTERNAL"
  ip_version   = "IPV4"

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_compute_region_network_endpoint_group" "edge" {
  for_each = var.enabled && var.workloads_enabled && var.edge_provisioned ? local.edge_cloud_run_services : {}

  project               = var.project_id
  region                = var.region
  name                  = "${var.name_prefix}-${replace(each.key, "_", "-")}-neg"
  network_endpoint_type = "SERVERLESS"

  cloud_run {
    service = each.key == "portfolio" ? each.value : google_cloud_run_v2_service.application[each.key].name
  }
}

resource "google_compute_security_policy" "registry_edge" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project     = var.project_id
  name        = "${var.name_prefix}-edge"
  description = "Baseline Cloud Armor policy for registry API and action origins"

  rule {
    action      = "deny(403)"
    priority    = 100
    description = "Reject missing, malformed, empty, or oversized archive lengths"

    match {
      expr {
        expression = "(${local.edge_archive_upload_match}) && (!has(request.headers['content-length']) || !request.headers['content-length'].matches('^[1-9][0-9]{0,7}$') || int(request.headers['content-length']) > 26214400)"
      }
    }
  }

  rule {
    action      = "deny(403)"
    priority    = 1000
    description = "Block stable SQL injection signatures"
    preview     = true

    match {
      expr {
        expression = "!(${local.edge_archive_upload_match}) && evaluatePreconfiguredWaf('sqli-stable')"
      }
    }
  }

  rule {
    action      = "deny(403)"
    priority    = 1010
    description = "Block stable cross-site scripting signatures"
    preview     = true

    match {
      expr {
        expression = "!(${local.edge_archive_upload_match}) && evaluatePreconfiguredWaf('xss-stable')"
      }
    }
  }

  rule {
    action      = "rate_based_ban"
    priority    = 1100
    description = "Preview per-IP registry traffic rate limit before enforcement"
    preview     = true

    match {
      expr {
        expression = "(request.method == 'POST' || request.method == 'PUT' || request.method == 'PATCH' || request.method == 'DELETE') && request.path.matches('^/packages/api/v1(/|$)')"
      }
    }

    rate_limit_options {
      conform_action   = "allow"
      exceed_action    = "deny(429)"
      enforce_on_key   = "IP"
      ban_duration_sec = 300

      rate_limit_threshold {
        count        = 600
        interval_sec = 60
      }

      ban_threshold {
        count        = 1200
        interval_sec = 60
      }
    }
  }

  rule {
    action      = "allow"
    priority    = 2147483647
    description = "Default allow after WAF evaluation"

    match {
      versioned_expr = "SRC_IPS_V1"

      config {
        src_ip_ranges = ["*"]
      }
    }
  }
}

resource "google_compute_backend_service" "edge" {
  for_each = var.enabled && var.workloads_enabled && var.edge_provisioned ? local.edge_cloud_run_services : {}

  project               = var.project_id
  name                  = "${var.name_prefix}-${replace(each.key, "_", "-")}-backend"
  protocol              = "HTTP"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  enable_cdn            = false
  security_policy       = each.key == "portfolio" ? null : google_compute_security_policy.registry_edge[0].id

  backend {
    group = google_compute_region_network_endpoint_group.edge[each.key].id
  }

  log_config {
    enable      = true
    sample_rate = 1.0
  }
}

resource "google_compute_url_map" "edge" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project         = var.project_id
  name            = "${var.name_prefix}-edge"
  default_service = google_compute_backend_service.edge["portfolio"].id

  host_rule {
    hosts        = var.edge_certificate_domains
    path_matcher = "seen-registry"
  }

  path_matcher {
    name            = "seen-registry"
    default_service = google_compute_backend_service.edge["portfolio"].id

    route_rules {
      priority = 10
      service  = google_compute_backend_service.edge["release_actions"].id

      match_rules {
        regex_match = "^/packages/api/v1/packages/[^/]+/[^/]+/releases/[^/]+/actions/(yank|unyank)$"
      }
    }

    route_rules {
      priority = 20
      service  = google_compute_backend_service.edge["security_actions"].id

      match_rules {
        regex_match = "^/packages/api/v1/packages/[^/]+/[^/]+/releases/[^/]+/actions/(security-quarantine|security-reinstate)$"
      }
    }

    route_rules {
      priority = 100
      service  = google_compute_backend_service.edge["api"].id

      match_rules {
        full_path_match = "/packages/api/v1"
      }

      match_rules {
        prefix_match = "/packages/api/v1/"
      }
    }
  }
}

resource "google_compute_ssl_policy" "edge" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project         = var.project_id
  name            = "${var.name_prefix}-edge"
  description     = "Modern TLS policy for the Seen registry edge"
  profile         = "MODERN"
  min_tls_version = "TLS_1_2"
}

resource "google_certificate_manager_dns_authorization" "edge" {
  for_each = var.enabled && var.workloads_enabled && var.edge_provisioned ? toset(var.edge_certificate_domains) : toset([])

  project     = var.project_id
  name        = "${var.name_prefix}-${replace(each.value, ".", "-")}"
  description = "DNS authorization for the Seen registry edge certificate"
  domain      = each.value
}

resource "google_certificate_manager_certificate" "edge" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project     = var.project_id
  name        = "${var.name_prefix}-edge"
  description = "Seen registry edge certificate"

  managed {
    domains            = var.edge_certificate_domains
    dns_authorizations = [for authorization in google_certificate_manager_dns_authorization.edge : authorization.id]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_certificate_manager_certificate_map" "edge" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project     = var.project_id
  name        = "${var.name_prefix}-edge"
  description = "Seen registry edge certificate map"
}

resource "google_certificate_manager_certificate_map_entry" "edge" {
  for_each = var.enabled && var.workloads_enabled && var.edge_provisioned ? toset(var.edge_certificate_domains) : toset([])

  project      = var.project_id
  name         = "${var.name_prefix}-${replace(each.value, ".", "-")}"
  description  = "Seen registry edge certificate for ${each.value}"
  map          = google_certificate_manager_certificate_map.edge[0].name
  certificates = [google_certificate_manager_certificate.edge[0].id]
  hostname     = each.value
}

resource "google_compute_target_https_proxy" "edge" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project         = var.project_id
  name            = "${var.name_prefix}-edge"
  url_map         = google_compute_url_map.edge[0].id
  certificate_map = "//certificatemanager.googleapis.com/${google_certificate_manager_certificate_map.edge[0].id}"
  ssl_policy      = google_compute_ssl_policy.edge[0].id
}

resource "google_compute_global_forwarding_rule" "edge_https" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project               = var.project_id
  name                  = "${var.name_prefix}-https"
  ip_address            = google_compute_global_address.edge[0].id
  ip_protocol           = "TCP"
  port_range            = "443"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  target                = google_compute_target_https_proxy.edge[0].id
}

resource "google_compute_url_map" "http_redirect" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project = var.project_id
  name    = "${var.name_prefix}-http-redirect"

  default_url_redirect {
    https_redirect = true
    strip_query    = false
  }
}

resource "google_compute_target_http_proxy" "http_redirect" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project = var.project_id
  name    = "${var.name_prefix}-http-redirect"
  url_map = google_compute_url_map.http_redirect[0].id
}

resource "google_compute_global_forwarding_rule" "edge_http" {
  count = var.enabled && var.workloads_enabled && var.edge_provisioned ? 1 : 0

  project               = var.project_id
  name                  = "${var.name_prefix}-http"
  ip_address            = google_compute_global_address.edge[0].id
  ip_protocol           = "TCP"
  port_range            = "80"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  target                = google_compute_target_http_proxy.http_redirect[0].id
}
