locals {
  long_lived_signer_authorizations = concat(
    var.workloads_enabled ? [
      { operation = "release", role = "releases", identity = "promoter" },
      { operation = "release", role = "snapshot", identity = "promoter" },
      { operation = "release", role = "timestamp", identity = "promoter" },
      { operation = "release", role = "releases", identity = "release_actions" },
      { operation = "release", role = "snapshot", identity = "release_actions" },
      { operation = "release", role = "timestamp", identity = "release_actions" },
      { operation = "security", role = "security", identity = "security_actions" },
      { operation = "security", role = "snapshot", identity = "security_actions" },
      { operation = "security", role = "timestamp", identity = "security_actions" },
    ] : [],
    var.refresh_jobs_enabled ? [
      { operation = "release", role = "releases", identity = "release_refresh" },
      { operation = "release", role = "snapshot", identity = "release_refresh" },
      { operation = "release", role = "timestamp", identity = "release_refresh" },
      { operation = "security", role = "security", identity = "security_refresh" },
      { operation = "security", role = "snapshot", identity = "security_refresh" },
      { operation = "security", role = "timestamp", identity = "security_refresh" },
    ] : [],
  )

  ceremony_signer_authorizations = [
    for binding in [
      { operation = "bootstrap", role = "releases", identity = "online_bootstrap" },
      { operation = "bootstrap", role = "security", identity = "online_bootstrap" },
      { operation = "bootstrap", role = "snapshot", identity = "online_bootstrap" },
      { operation = "bootstrap", role = "timestamp", identity = "online_bootstrap" },
      { operation = "targets-renewal", role = "snapshot", identity = "targets_renewal" },
      { operation = "targets-renewal", role = "timestamp", identity = "targets_renewal" },
      { operation = "targets-rotation:releases", role = "releases", identity = "targets_releases_rotation" },
      { operation = "targets-rotation:releases", role = "snapshot", identity = "targets_releases_rotation" },
      { operation = "targets-rotation:releases", role = "timestamp", identity = "targets_releases_rotation" },
      { operation = "targets-rotation:security", role = "security", identity = "targets_security_rotation" },
      { operation = "targets-rotation:security", role = "snapshot", identity = "targets_security_rotation" },
      { operation = "targets-rotation:security", role = "timestamp", identity = "targets_security_rotation" },
    ] : binding if contains(var.ceremony_operations, binding.identity)
  ]

  signer_authorizations = concat(
    local.long_lived_signer_authorizations,
    local.ceremony_signer_authorizations,
  )

  signer_callers = {
    for binding in local.signer_authorizations :
    "${binding.role}:${binding.identity}" => binding
  }

  signer_caller_bindings = var.enabled ? {
    for role in local.roles : role => join(",", [
      for binding in local.signer_authorizations :
      "${binding.operation}=${local.service_account_ids[binding.identity]}@${var.project_id}.iam.gserviceaccount.com"
      if binding.role == role
    ])
  } : {}
}

resource "google_cloud_run_v2_service_iam_member" "signer_invoker" {
  for_each = var.enabled && local.signers_enabled ? local.signer_callers : {}

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.signer[each.value.role].name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.runtime[each.value.identity].email}"
}
