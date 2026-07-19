resource "google_secret_manager_secret" "registry" {
  for_each = var.enabled ? local.secret_names : {}

  project   = var.project_id
  secret_id = each.value
  labels    = merge(local.common_labels, { purpose = replace(each.key, "_", "-") })

  replication {
    dynamic "auto" {
      for_each = contains(keys(var.secret_replica_locations), each.key) ? [] : [true]
      content {}
    }

    dynamic "user_managed" {
      for_each = contains(keys(var.secret_replica_locations), each.key) ? [var.secret_replica_locations[each.key]] : []
      content {
        dynamic "replicas" {
          for_each = user_managed.value
          content {
            location = replicas.value
          }
        }
      }
    }
  }

  depends_on = [google_project_service.required]

  lifecycle {
    prevent_destroy = true
  }
}

locals {
  long_lived_secret_access_requests = {
    api_publisher = {
      identity = "api"
      secret   = "publisher_token"
    }
    api_trust = {
      identity = "api"
      secret   = "trust_and_safety_token"
    }
    release_actions_publisher = {
      identity = "release_actions"
      secret   = "publisher_token"
    }
    security_actions_security = {
      identity = "security_actions"
      secret   = "security_token"
    }
    source_github_app_id = {
      identity = "source"
      secret   = "github_app_id"
    }
    source_github_app_private_key = {
      identity = "source"
      secret   = "github_app_private_key"
    }
    source_gitlab = {
      identity = "source"
      secret   = "gitlab_forge_token"
    }
  }

  ceremony_secret_access_requests = {
    bootstrap_root = {
      identity  = "offline_bootstrap_importer"
      secret    = "bootstrap_root_envelope"
      operation = "offline_bootstrap_importer"
    }
    bootstrap_targets = {
      identity  = "offline_bootstrap_importer"
      secret    = "bootstrap_targets_envelope"
      operation = "offline_bootstrap_importer"
    }
    targets_renewal = {
      identity  = "targets_renewal"
      secret    = "targets_renewal_envelope"
      operation = "targets_renewal"
    }
    targets_releases_rotation = {
      identity  = "targets_releases_rotation"
      secret    = "targets_releases_rotation_envelope"
      operation = "targets_releases_rotation"
    }
    targets_security_rotation = {
      identity  = "targets_security_rotation"
      secret    = "targets_security_rotation_envelope"
      operation = "targets_security_rotation"
    }
    root_rotation = {
      identity  = "root_importer"
      secret    = "root_rotation_envelope"
      operation = "root_importer"
    }
  }

  secret_access_requests = merge(
    var.workloads_enabled ? local.long_lived_secret_access_requests : {},
    {
      for key, request in local.ceremony_secret_access_requests :
      key => request if contains(var.ceremony_operations, request.operation)
    },
  )

  enabled_secret_access = {
    for key, value in local.secret_access_requests : key => merge(value, {
      version = var.secret_versions[value.secret]
    })
    if var.enabled && contains(keys(var.secret_versions), value.secret)
  }
}

data "google_project" "current" {
  count = var.enabled ? 1 : 0

  project_id = var.project_id
}

resource "google_secret_manager_secret_iam_member" "runtime" {
  for_each = local.enabled_secret_access

  project   = var.project_id
  secret_id = google_secret_manager_secret.registry[each.value.secret].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime[each.value.identity].email}"

  condition {
    title       = "exact_${each.value.secret}_version_${each.value.version}"
    description = "Rejects latest and every unreviewed secret version."
    expression  = "resource.name == 'projects/${data.google_project.current[0].number}/secrets/${google_secret_manager_secret.registry[each.value.secret].secret_id}/versions/${each.value.version}'"
  }

  lifecycle {
    precondition {
      condition     = local.bootstrap_authority_split_contract && local.inactive_ceremonies_have_no_authority_contract
      error_message = "Secret-version access must preserve bootstrap isolation and leave every unselected ceremony identity unauthorized."
    }

    precondition {
      condition     = local.read_only_api_contract
      error_message = "The read-only API identity must never receive Secret Manager access."
    }
  }
}
