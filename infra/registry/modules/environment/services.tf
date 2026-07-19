locals {
  signer_services = {
    releases = {
      name           = "${var.name_prefix}-sign-releases"
      identity       = "signer_releases"
      maximum_expiry = 604800
      audience       = "https://${var.name_prefix}-sign-releases.${var.project_id}.registry.invalid"
    }
    security = {
      name           = "${var.name_prefix}-sign-security"
      identity       = "signer_security"
      maximum_expiry = 21600
      audience       = "https://${var.name_prefix}-sign-security.${var.project_id}.registry.invalid"
    }
    snapshot = {
      name           = "${var.name_prefix}-sign-snapshot"
      identity       = "signer_snapshot"
      maximum_expiry = 86400
      audience       = "https://${var.name_prefix}-sign-snapshot.${var.project_id}.registry.invalid"
    }
    timestamp = {
      name           = "${var.name_prefix}-sign-timestamp"
      identity       = "signer_timestamp"
      maximum_expiry = 21600
      audience       = "https://${var.name_prefix}-sign-timestamp.${var.project_id}.registry.invalid"
    }
  }

  selected_signer_services = {
    for role in local.selected_signer_roles : role => local.signer_services[role]
  }

  api_service = {
    api = {
      name              = "${var.name_prefix}-api-v2"
      identity          = "api"
      argument          = local.read_only_api_enabled ? "serve-read-only-public-api" : "serve-public-api"
      server_mode       = local.read_only_api_enabled ? "read-only-public-api" : "public-api"
      active_roles      = toset([])
      minimum_instances = 1
      maximum_instances = 3
      concurrency       = 2
      memory            = "2Gi"
      environment       = local.read_only_api_enabled ? local.read_only_api_environment : local.writer_api_environment
      secrets = local.read_only_api_enabled ? tomap({}) : tomap({
        REGISTRY_WRITER_TOKEN           = "publisher_token"
        REGISTRY_TRUST_AND_SAFETY_TOKEN = "trust_and_safety_token"
      })
    }
  }

  action_services = {
    release_actions = {
      name              = "${var.name_prefix}-release-actions-v2"
      identity          = "release_actions"
      argument          = "serve-release-actions"
      server_mode       = "release-actions"
      active_roles      = toset(["releases", "snapshot", "timestamp"])
      minimum_instances = 0
      maximum_instances = 2
      concurrency       = 1
      memory            = "1Gi"
      environment       = local.coordinator_environment
      secrets = {
        REGISTRY_WRITER_TOKEN = "publisher_token"
      }
    }
    security_actions = {
      name              = "${var.name_prefix}-security-actions-v2"
      identity          = "security_actions"
      argument          = "serve-security-actions"
      server_mode       = "security-actions"
      active_roles      = toset(["security", "snapshot", "timestamp"])
      minimum_instances = 0
      maximum_instances = 2
      concurrency       = 1
      memory            = "1Gi"
      environment       = local.coordinator_environment
      secrets = {
        REGISTRY_SECURITY_TOKEN = "security_token"
      }
    }
  }

  application_services = merge(
    local.api_enabled ? local.api_service : {},
    var.workloads_enabled ? local.action_services : {},
  )
}

resource "google_cloud_run_v2_service" "signer" {
  for_each = var.enabled ? local.selected_signer_services : {}

  project  = var.project_id
  location = var.region
  name     = each.value.name
  # Every authorized caller sends all egress through this stack's Direct VPC
  # network, so signer endpoints never need internet ingress.
  ingress = "INGRESS_TRAFFIC_INTERNAL_ONLY"
  # Production runtime definitions are deliberately reversible through the
  # complete saved-plan gate so refresh authority can be removed before a
  # later isolated ceremony. Durable keys/data remain separately protected.
  deletion_protection = (
    var.environment != "prod" &&
    (var.workloads_enabled || var.refresh_jobs_enabled)
  )
  invoker_iam_disabled = false
  custom_audiences     = [each.value.audience]
  labels               = merge(local.common_labels, { component = "tuf-signer", tuf_role = each.key })

  template {
    service_account                  = google_service_account.runtime[each.value.identity].email
    timeout                          = "35s"
    max_instance_request_concurrency = 8
    execution_environment            = "EXECUTION_ENVIRONMENT_GEN2"

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    vpc_access {
      egress = "ALL_TRAFFIC"
      network_interfaces {
        network    = google_compute_network.registry[0].name
        subnetwork = google_compute_subnetwork.registry[0].name
        tags = [
          "${var.name_prefix}-signer",
          "${var.name_prefix}-restricted-egress",
        ]
      }
    }

    containers {
      name  = "registry"
      image = var.signer_container_image
      args  = ["serve-tuf-signer"]

      ports {
        name           = "http1"
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle          = true
        startup_cpu_boost = false
      }

      startup_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 3
        period_seconds        = 5
        failure_threshold     = 12

        tcp_socket {
          port = 8080
        }
      }

      env {
        name  = "REGISTRY_ENVIRONMENT"
        value = var.runtime_environment
      }
      env {
        name  = "REGISTRY_REPOSITORY_ID"
        value = var.repository_id
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_ROLE"
        value = each.key
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_KMS_KEY_VERSION"
        value = local.online_key_versions[each.key]
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX"
        value = var.online_public_keys_hex[each.key]
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_MAX_EXPIRY_SECONDS"
        value = tostring(each.value.maximum_expiry)
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_MAX_REQUEST_BYTES"
        value = "1048576"
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_MAX_CONCURRENCY"
        value = "8"
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_METADATA_BUCKET"
        value = var.bucket_names.metadata
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_OBJECT_PREFIX"
        value = var.object_prefix
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256"
        value = var.trusted_root_v1_sha256
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_AUDIENCE"
        value = each.value.audience
      }
      env {
        name  = "REGISTRY_TUF_SIGNER_CALLER_BINDINGS"
        value = local.signer_caller_bindings[each.key]
      }
    }
  }

  depends_on = [
    google_compute_router_nat.registry,
    google_kms_crypto_key_iam_member.signer,
    google_storage_bucket_iam_member.metadata_reader,
  ]
}

locals {
  signer_target_environment = var.enabled && local.signers_enabled ? merge([
    for role, service in google_cloud_run_v2_service.signer : {
      "REGISTRY_TUF_${upper(role)}_SIGNER_URL"      = "${service.uri}/sign"
      "REGISTRY_TUF_${upper(role)}_SIGNER_AUDIENCE" = local.signer_services[role].audience
    }
  ]...) : {}
}

resource "google_cloud_run_v2_service" "application" {
  for_each = var.enabled ? local.application_services : {}

  project              = var.project_id
  location             = var.region
  name                 = each.value.name
  ingress              = var.edge_cutover_enabled ? "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER" : var.private_service_ingress
  deletion_protection  = var.environment != "prod"
  invoker_iam_disabled = var.edge_cutover_enabled
  default_uri_disabled = var.edge_cutover_enabled
  labels               = merge(local.common_labels, { component = replace(each.key, "_", "-") })

  template {
    service_account                  = google_service_account.runtime[each.value.identity].email
    timeout                          = "60s"
    max_instance_request_concurrency = each.value.concurrency
    execution_environment            = "EXECUTION_ENVIRONMENT_GEN2"

    scaling {
      min_instance_count = each.value.minimum_instances
      max_instance_count = each.value.maximum_instances
    }

    vpc_access {
      egress = "ALL_TRAFFIC"
      network_interfaces {
        network    = google_compute_network.registry[0].name
        subnetwork = google_compute_subnetwork.registry[0].name
        tags = concat(
          ["${var.name_prefix}-${replace(each.key, "_", "-")}"],
          each.key == "api" ? ["${var.name_prefix}-restricted-egress"] : [],
        )
      }
    }

    containers {
      name  = "registry"
      image = var.container_image
      args  = [each.value.argument]

      ports {
        name           = "http1"
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = each.value.memory
        }
        cpu_idle          = false
        startup_cpu_boost = false
      }

      startup_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 3
        period_seconds        = 5
        failure_threshold     = 12

        tcp_socket {
          port = 8080
        }
      }

      liveness_probe {
        initial_delay_seconds = 10
        timeout_seconds       = 3
        period_seconds        = 30
        failure_threshold     = 3

        http_get {
          path = "/health"
          port = 8080
        }
      }

      dynamic "env" {
        for_each = merge(
          each.value.environment,
          local.public_key_environment,
          { REGISTRY_SERVER_MODE = each.value.server_mode },
          { for role in each.value.active_roles : "REGISTRY_TUF_${upper(role)}_SIGNER_URL" => local.signer_target_environment["REGISTRY_TUF_${upper(role)}_SIGNER_URL"] },
          { for role in each.value.active_roles : "REGISTRY_TUF_${upper(role)}_SIGNER_AUDIENCE" => local.signer_target_environment["REGISTRY_TUF_${upper(role)}_SIGNER_AUDIENCE"] },
        )
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = {
          for environment_name, secret_key in each.value.secrets : environment_name => secret_key
          if contains(keys(var.secret_versions), secret_key)
        }
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.registry[env.value].secret_id
              version = var.secret_versions[env.value]
            }
          }
        }
      }
    }
  }

  depends_on = [
    google_cloud_run_v2_service.signer,
    google_compute_router_nat.registry,
    google_project_iam_member.firestore_reader,
    google_project_iam_member.firestore_user,
    google_secret_manager_secret_iam_member.runtime,
    google_storage_bucket_iam_member.metadata_reader,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "portfolio_gateway" {
  for_each = var.enabled && local.api_enabled && !var.edge_cutover_enabled && var.portfolio_gateway_service_account != null ? google_cloud_run_v2_service.application : {}

  project  = var.project_id
  location = var.region
  name     = each.value.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.portfolio_gateway_service_account}"
}
