locals {
  signer_service_names      = [for service in values(local.signer_services) : service.name]
  application_service_names = [for service in values(local.application_services) : service.name]

  kms_signing_alerts = {
    for role in local.roles : "unexpected_kms_signing_${role}" => {
      description = "The ${role} KMS key was used by a principal other than its role-locked signer account."
      # Cloud Logging accepts the Cloud KMS audit-log resource below, but
      # exposes user-defined metrics from those entries to Monitoring on the
      # global monitored resource.
      resource_type = "global"
      filter = join(" ", [
        "resource.type=\"cloudkms_cryptokeyversion\"",
        "protoPayload.methodName=\"google.cloud.kms.v1.KeyManagementService.AsymmetricSign\"",
        "resource.labels.key_ring_id=\"${var.kms_key_ring}\"",
        "resource.labels.crypto_key_id=\"${var.online_key_names[role]}\"",
        "protoPayload.authenticationInfo.principalEmail!=\"${local.service_account_ids["signer_${role}"]}@${var.project_id}.iam.gserviceaccount.com\"",
      ])
    }
  }

  runtime_log_alerts = {
    registry_service_5xx = {
      description   = "A registry application service returned a server error."
      resource_type = "cloud_run_revision"
      filter = join(" ", [
        "resource.type=\"cloud_run_revision\"",
        "resource.labels.service_name:(\"${join("\" OR \"", local.application_service_names)}\")",
        "httpRequest.status>=500",
      ])
    }
    signer_authorization_denied = {
      description   = "A role-locked signer rejected an authenticated or state-invalid signing request."
      resource_type = "cloud_run_revision"
      filter = join(" ", [
        "resource.type=\"cloud_run_revision\"",
        "resource.labels.service_name:(\"${join("\" OR \"", local.signer_service_names)}\")",
        "(httpRequest.status=401 OR httpRequest.status=403 OR jsonPayload.event=\"tuf_signing_rejected\")",
      ])
    }
    registry_job_failure = {
      description   = "A registry Cloud Run job emitted an error or terminated unsuccessfully."
      resource_type = "cloud_run_job"
      filter = join(" ", [
        "resource.type=\"cloud_run_job\"",
        "resource.labels.job_name:\"${var.name_prefix}-\"",
        "severity>=ERROR",
      ])
    }
    metadata_expiry_breach_service = {
      description   = "A registry service emitted a fail-closed metadata expiry gate event."
      resource_type = "cloud_run_revision"
      filter = join(" ", [
        "resource.type=\"cloud_run_revision\"",
        "jsonPayload.event=\"tuf_metadata_expiry_breach\"",
        "jsonPayload.environment=\"${var.runtime_environment}\"",
      ])
    }
    metadata_expiry_breach_job = {
      description   = "A registry job emitted a fail-closed metadata expiry gate event."
      resource_type = "cloud_run_job"
      filter = join(" ", [
        "resource.type=\"cloud_run_job\"",
        "jsonPayload.event=\"tuf_metadata_expiry_breach\"",
        "jsonPayload.environment=\"${var.runtime_environment}\"",
      ])
    }
  }

  log_alerts = merge(local.kms_signing_alerts, local.runtime_log_alerts)
}

resource "google_logging_metric" "registry_alert" {
  for_each = var.enabled && var.monitoring_enabled ? local.log_alerts : {}

  project     = var.project_id
  name        = "${var.name_prefix}-${replace(each.key, "_", "-")}"
  description = each.value.description
  filter      = each.value.filter

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
  }
}

resource "google_monitoring_alert_policy" "registry_log_alert" {
  for_each = var.enabled && var.monitoring_enabled ? local.log_alerts : {}

  project               = var.project_id
  display_name          = "${var.name_prefix}: ${replace(each.key, "_", " ")}"
  combiner              = "OR"
  notification_channels = var.notification_channel_ids
  severity              = startswith(each.key, "metadata_expiry_breach") || startswith(each.key, "unexpected_kms_signing") ? "CRITICAL" : "ERROR"

  conditions {
    display_name = "Any matching event"

    condition_threshold {
      filter          = "resource.type=\"${each.value.resource_type}\" AND metric.type=\"logging.googleapis.com/user/${google_logging_metric.registry_alert[each.key].name}\""
      comparison      = "COMPARISON_GT"
      threshold_value = 0
      duration        = "0s"

      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
      }

      trigger {
        count = 1
      }
    }
  }

  alert_strategy {
    auto_close = "604800s"
  }
}

resource "google_monitoring_uptime_check_config" "metadata" {
  count = var.enabled && var.monitoring_enabled ? 1 : 0

  project      = var.project_id
  display_name = "${var.name_prefix} public timestamp metadata"
  timeout      = "10s"
  period       = "300s"
  selected_regions = [
    "USA",
    "EUROPE",
    "ASIA_PACIFIC",
  ]

  http_check {
    path           = "/packages/api/v1/metadata/timestamp.json"
    port           = 443
    request_method = "GET"
    use_ssl        = true
    validate_ssl   = true

    accepted_response_status_codes {
      status_class = "STATUS_CLASS_2XX"
    }
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      host       = var.uptime_host
      project_id = var.project_id
    }
  }
}

resource "google_monitoring_alert_policy" "metadata_uptime" {
  count = var.enabled && var.monitoring_enabled ? 1 : 0

  project               = var.project_id
  display_name          = "${var.name_prefix}: public timestamp unavailable"
  combiner              = "OR"
  notification_channels = var.notification_channel_ids
  severity              = "CRITICAL"

  conditions {
    display_name = "Most public probes fail for five minutes"

    condition_threshold {
      filter = join(" AND ", [
        "resource.type=\"uptime_url\"",
        "metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\"",
        "metric.label.check_id=\"${google_monitoring_uptime_check_config.metadata[0].uptime_check_id}\"",
      ])
      comparison      = "COMPARISON_LT"
      threshold_value = 1
      duration        = "300s"

      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_FRACTION_TRUE"
        cross_series_reducer = "REDUCE_MEAN"
      }

      trigger {
        count = 1
      }
    }
  }

  alert_strategy {
    auto_close = "604800s"
  }
}

resource "google_billing_budget" "registry" {
  count = var.enabled && var.monitoring_enabled && var.billing_account_id != null && var.monthly_budget_usd != null ? 1 : 0

  billing_account = var.billing_account_id
  display_name    = "${var.name_prefix} monthly budget"

  budget_filter {
    projects = ["projects/${data.google_project.current[0].number}"]
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = tostring(var.monthly_budget_usd)
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }

  threshold_rules {
    threshold_percent = 0.9
  }

  threshold_rules {
    threshold_percent = 1.0
  }

  all_updates_rule {
    monitoring_notification_channels = var.notification_channel_ids
    disable_default_iam_recipients   = false
  }
}
