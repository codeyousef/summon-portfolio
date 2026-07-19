locals {
  base_long_lived_jobs = {
    source = {
      name         = "${var.name_prefix}-source-verify-v2"
      identity     = "source"
      argument     = "verify-source-once"
      timeout      = "300s"
      memory       = "1Gi"
      active_roles = toset([])
      environment  = local.review_environment
      secrets = {
        REGISTRY_GITHUB_APP_ID              = "github_app_id"
        REGISTRY_GITHUB_APP_PRIVATE_KEY_PEM = "github_app_private_key"
        REGISTRY_GITLAB_FORGE_TOKEN         = "gitlab_forge_token"
      }
    }
    scanner = {
      name         = "${var.name_prefix}-scan-v2"
      identity     = "scanner"
      argument     = "scan-once"
      timeout      = "120s"
      memory       = "1Gi"
      active_roles = toset([])
      environment  = local.review_environment
      secrets      = {}
    }
    promoter = {
      name         = "${var.name_prefix}-promote-v2"
      identity     = "promoter"
      argument     = "promote-once"
      timeout      = "300s"
      memory       = "1Gi"
      active_roles = toset(["releases", "snapshot", "timestamp"])
      environment  = merge(local.promotion_environment, local.public_key_environment)
      secrets      = {}
    }
    root_verifier = {
      name         = "${var.name_prefix}-root-verify-v2"
      identity     = "root_verifier"
      argument     = "verify-root-chain"
      timeout      = "300s"
      memory       = "1Gi"
      active_roles = toset([])
      environment  = merge(local.metadata_maintenance_environment, local.public_key_environment)
      secrets      = {}
    }
  }

  refresh_jobs = {
    release_refresh = {
      name         = "${var.name_prefix}-release-refresh-v2"
      identity     = "release_refresh"
      argument     = "refresh-releases-once"
      timeout      = "300s"
      memory       = "1Gi"
      active_roles = toset(["releases", "snapshot", "timestamp"])
      environment  = merge(local.coordinator_environment, local.public_key_environment)
      secrets      = {}
    }
    security_refresh = {
      name         = "${var.name_prefix}-security-refresh-v2"
      identity     = "security_refresh"
      argument     = "refresh-security-once"
      timeout      = "300s"
      memory       = "1Gi"
      active_roles = toset(["security", "snapshot", "timestamp"])
      environment  = merge(local.coordinator_environment, local.public_key_environment)
      secrets      = {}
    }
  }

  long_lived_jobs = merge(
    var.workloads_enabled ? local.base_long_lived_jobs : {},
    !var.workloads_enabled && var.root_verifier_job_enabled ? {
      root_verifier = local.base_long_lived_jobs.root_verifier
    } : {},
    var.refresh_jobs_enabled ? local.refresh_jobs : {},
  )

  operations_long_lived_jobs = var.environment == "prod" && var.job_operations_service_account != null ? {
    for key, job in local.long_lived_jobs : key => job
    if contains(["root_verifier", "release_refresh", "security_refresh"], key)
  } : {}
}

resource "google_cloud_run_v2_job" "long_lived" {
  for_each = var.enabled ? local.long_lived_jobs : {}

  project  = var.project_id
  location = var.region
  name     = each.value.name
  # Production jobs are selector-managed, stateless definitions. Keeping them
  # removable is required to revoke refresh authority before a ceremony.
  deletion_protection = var.environment != "prod"
  labels              = merge(local.common_labels, { component = replace(each.key, "_", "-") })

  template {
    task_count  = 1
    parallelism = 1

    template {
      service_account       = google_service_account.runtime[each.value.identity].email
      timeout               = each.value.timeout
      max_retries           = 0
      execution_environment = "EXECUTION_ENVIRONMENT_GEN2"

      vpc_access {
        egress = "ALL_TRAFFIC"
        network_interfaces {
          network    = google_compute_network.registry[0].name
          subnetwork = google_compute_subnetwork.registry[0].name
          tags = concat(
            ["${var.name_prefix}-${replace(each.key, "_", "-")}"],
            contains(["scanner", "root_verifier"], each.key) ? ["${var.name_prefix}-restricted-egress"] : [],
          )
        }
      }

      containers {
        name  = "registry"
        image = var.container_image
        args  = [each.value.argument]

        resources {
          limits = {
            cpu    = "1"
            memory = each.value.memory
          }
        }

        dynamic "env" {
          for_each = merge(
            each.value.environment,
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
  }

  depends_on = [
    google_cloud_run_v2_service.signer,
    google_compute_router_nat.registry,
    google_project_iam_member.firestore_user,
    google_secret_manager_secret_iam_member.runtime,
    google_service_account_iam_member.infrastructure_executor_act_as,
    google_storage_bucket_iam_member.metadata_creator,
  ]
}

resource "google_cloud_run_v2_job_iam_member" "operations_long_lived" {
  for_each = var.enabled ? local.operations_long_lived_jobs : {}

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_job.long_lived[each.key].name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.job_operations_service_account}"
}

resource "google_cloud_run_v2_job_iam_member" "operations_long_lived_viewer" {
  for_each = var.enabled ? local.operations_long_lived_jobs : {}

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_job.long_lived[each.key].name
  role     = var.job_operations_viewer_role
  member   = "serviceAccount:${var.job_operations_service_account}"
}

locals {
  ceremony_jobs = {
    offline_bootstrap_importer = {
      name         = "${var.name_prefix}-offline-bootstrap"
      identity     = "offline_bootstrap_importer"
      args         = ["import-offline-bootstrap"]
      active_roles = toset([])
      secret_key   = null
      secret_env = {
        REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64    = "bootstrap_root_envelope"
        REGISTRY_BOOTSTRAP_TARGETS_ENVELOPE_BASE64 = "bootstrap_targets_envelope"
      }
    }
    online_bootstrap = {
      name         = "${var.name_prefix}-online-bootstrap"
      identity     = "online_bootstrap"
      args         = ["bootstrap-online"]
      active_roles = local.roles
      secret_key   = null
      secret_env   = {}
    }
    targets_renewal = {
      name         = "${var.name_prefix}-targets-renewal"
      identity     = "targets_renewal"
      args         = ["import-offline-targets-renewal", "/var/run/seen-transfer/envelope.json"]
      active_roles = toset(["snapshot", "timestamp"])
      secret_key   = "targets_renewal_envelope"
      secret_env   = {}
    }
    targets_releases_rotation = {
      name         = "${var.name_prefix}-targets-release-rotate"
      identity     = "targets_releases_rotation"
      args         = ["import-offline-targets-rotation", "releases", "/var/run/seen-transfer/envelope.json"]
      active_roles = toset(["releases", "snapshot", "timestamp"])
      secret_key   = "targets_releases_rotation_envelope"
      secret_env   = {}
    }
    targets_security_rotation = {
      name         = "${var.name_prefix}-targets-security-rotate"
      identity     = "targets_security_rotation"
      args         = ["import-offline-targets-rotation", "security", "/var/run/seen-transfer/envelope.json"]
      active_roles = toset(["security", "snapshot", "timestamp"])
      secret_key   = "targets_security_rotation_envelope"
      secret_env   = {}
    }
    root_importer = {
      name         = "${var.name_prefix}-root-import"
      identity     = "root_importer"
      args         = ["import-offline-root-rotation", "/var/run/seen-transfer/envelope.json"]
      active_roles = toset([])
      secret_key   = "root_rotation_envelope"
      secret_env   = {}
    }
  }

  selected_ceremony_jobs = {
    for key, job in local.ceremony_jobs : key => job
    if contains(var.ceremony_operations, key)
  }

  operations_ceremony_jobs = var.environment == "prod" && var.job_operations_service_account != null ? local.selected_ceremony_jobs : {}

  ceremony_required_secret_keys = toset(flatten([
    for job in values(local.selected_ceremony_jobs) : concat(
      job.secret_key == null ? [] : [job.secret_key],
      values(job.secret_env),
    )
  ]))
}

resource "google_cloud_run_v2_job" "ceremony" {
  for_each = var.enabled ? local.selected_ceremony_jobs : {}

  project             = var.project_id
  location            = var.region
  name                = each.value.name
  deletion_protection = false
  labels              = merge(local.common_labels, { component = "ephemeral-ceremony", operation = replace(each.key, "_", "-") })

  template {
    task_count  = 1
    parallelism = 1

    template {
      service_account       = google_service_account.runtime[each.value.identity].email
      timeout               = "300s"
      max_retries           = 0
      execution_environment = "EXECUTION_ENVIRONMENT_GEN2"

      vpc_access {
        egress = "ALL_TRAFFIC"
        network_interfaces {
          network    = google_compute_network.registry[0].name
          subnetwork = google_compute_subnetwork.registry[0].name
          tags       = ["${var.name_prefix}-ceremony"]
        }
      }

      containers {
        name  = "registry"
        image = var.container_image
        args  = each.value.args

        resources {
          limits = {
            cpu    = "1"
            memory = "1Gi"
          }
        }

        dynamic "env" {
          for_each = merge(
            each.value.identity == "offline_bootstrap_importer" ? local.metadata_maintenance_environment : local.coordinator_environment,
            local.public_key_environment,
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
            for environment_name, secret_key in each.value.secret_env : environment_name => secret_key
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

        dynamic "volume_mounts" {
          for_each = each.value.secret_key == null ? [] : [each.value.secret_key]
          content {
            name       = "transfer"
            mount_path = "/var/run/seen-transfer"
          }
        }
      }

      dynamic "volumes" {
        for_each = each.value.secret_key == null ? [] : [each.value.secret_key]
        content {
          name = "transfer"
          secret {
            secret = google_secret_manager_secret.registry[volumes.value].secret_id
            items {
              version = var.secret_versions[volumes.value]
              path    = "envelope.json"
              mode    = 256
            }
          }
        }
      }
    }
  }

  depends_on = [
    google_cloud_run_v2_service.signer,
    google_secret_manager_secret_iam_member.runtime,
    google_service_account_iam_member.infrastructure_executor_act_as,
    google_storage_bucket_iam_member.metadata_creator,
    google_storage_bucket_iam_member.timestamp_pointer_replacer,
  ]
}

resource "google_cloud_run_v2_job_iam_member" "operations_ceremony" {
  for_each = var.enabled ? local.operations_ceremony_jobs : {}

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_job.ceremony[each.key].name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.job_operations_service_account}"
}

resource "google_cloud_run_v2_job_iam_member" "operations_ceremony_viewer" {
  for_each = var.enabled ? local.operations_ceremony_jobs : {}

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_job.ceremony[each.key].name
  role     = var.job_operations_viewer_role
  member   = "serviceAccount:${var.job_operations_service_account}"
}
