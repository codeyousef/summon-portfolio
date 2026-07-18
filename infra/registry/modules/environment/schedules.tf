locals {
  base_schedules = {
    source = {
      schedule    = "*/5 * * * *"
      description = "Verify one quarantined package source every five minutes"
    }
    scanner = {
      schedule    = "2-59/5 * * * *"
      description = "Scan one source-verified package every five minutes"
    }
    promoter = {
      schedule    = "0 * * * *"
      description = "Promote one eligible package each hour"
    }
  }

  refresh_schedules = {
    release_refresh = {
      schedule    = "20 0 * * *"
      description = "Refresh the releases role daily"
    }
    security_refresh = {
      schedule    = "10 * * * *"
      description = "Refresh security, snapshot, and timestamp every hour"
    }
  }

  schedules = merge(
    local.base_schedules,
    var.refresh_jobs_enabled ? local.refresh_schedules : {},
  )
}

resource "google_cloud_run_v2_job_iam_member" "scheduler" {
  for_each = var.enabled && var.workloads_enabled && var.schedules_enabled ? local.schedules : {}

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_job.long_lived[each.key].name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.runtime["scheduler"].email}"
}

resource "google_service_account_iam_member" "scheduler_token_creator" {
  count = var.enabled && var.workloads_enabled && var.schedules_enabled ? 1 : 0

  service_account_id = "projects/${var.project_id}/serviceAccounts/${local.service_account_ids.scheduler}@${var.project_id}.iam.gserviceaccount.com"
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:service-${data.google_project.current[0].number}@gcp-sa-cloudscheduler.iam.gserviceaccount.com"
}

resource "google_cloud_scheduler_job" "registry" {
  for_each = var.enabled && var.workloads_enabled && var.schedules_enabled ? local.schedules : {}

  project          = var.project_id
  region           = var.region
  name             = "${google_cloud_run_v2_job.long_lived[each.key].name}-schedule"
  description      = each.value.description
  schedule         = each.value.schedule
  time_zone        = "Etc/UTC"
  attempt_deadline = "30s"
  paused           = var.schedules_paused

  http_target {
    uri         = "https://run.googleapis.com/v2/projects/${var.project_id}/locations/${var.region}/jobs/${google_cloud_run_v2_job.long_lived[each.key].name}:run"
    http_method = "POST"
    body        = base64encode("{}")
    headers = {
      "Content-Type" = "application/json"
    }

    oauth_token {
      service_account_email = google_service_account.runtime["scheduler"].email
      scope                 = "https://www.googleapis.com/auth/cloud-platform"
    }
  }

  depends_on = [
    google_cloud_run_v2_job_iam_member.scheduler,
    google_service_account_iam_member.scheduler_token_creator,
  ]
}
