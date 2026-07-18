locals {
  bucket_configuration = {
    quarantine = {
      name                = var.bucket_names.quarantine
      versioning          = true
      retention_seconds   = 0
      soft_delete_seconds = 604800
      age_delete_days     = 30
    }
    public = {
      name                = var.bucket_names.public
      versioning          = true
      retention_seconds   = 604800
      soft_delete_seconds = 604800
      age_delete_days     = 0
    }
    metadata = {
      name                = var.bucket_names.metadata
      versioning          = true
      retention_seconds   = 0
      soft_delete_seconds = 604800
      age_delete_days     = 0
    }
    private = {
      name                = var.bucket_names.private
      versioning          = true
      retention_seconds   = 2592000
      soft_delete_seconds = 604800
      age_delete_days     = 90
    }
    evidence = {
      name                = var.bucket_names.evidence
      versioning          = true
      retention_seconds   = 7776000
      soft_delete_seconds = 2592000
      age_delete_days     = 365
    }
    backup = {
      name                = var.bucket_names.backup
      versioning          = true
      retention_seconds   = 2592000
      soft_delete_seconds = 2592000
      age_delete_days     = 365
    }
  }
}

resource "google_storage_bucket" "registry" {
  for_each = var.enabled ? local.bucket_configuration : {}

  project                     = var.project_id
  name                        = each.value.name
  location                    = var.region
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false
  labels                      = local.common_labels

  versioning {
    enabled = each.value.versioning
  }

  soft_delete_policy {
    retention_duration_seconds = each.value.soft_delete_seconds
  }

  dynamic "retention_policy" {
    for_each = each.value.retention_seconds > 0 ? [each.value.retention_seconds] : []
    content {
      retention_period = retention_policy.value
      is_locked        = false
    }
  }

  dynamic "lifecycle_rule" {
    for_each = each.value.age_delete_days > 0 ? [each.value.age_delete_days] : []
    content {
      condition {
        age = lifecycle_rule.value
      }
      action {
        type = "Delete"
      }
    }
  }

  dynamic "cors" {
    for_each = each.key == "public" ? [1] : []
    content {
      origin          = [local.registry_web_origin]
      method          = ["GET", "HEAD"]
      response_header = ["Content-Type", "ETag", "X-Seen-Metadata-Sha256"]
      max_age_seconds = 3600
    }
  }

  depends_on = [google_project_service.required]

  lifecycle {
    prevent_destroy = true
  }
}
