resource "google_kms_key_ring" "registry" {
  count = var.enabled ? 1 : 0

  project  = var.project_id
  location = var.region
  name     = var.kms_key_ring

  depends_on = [google_project_service.required]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_kms_crypto_key" "online" {
  for_each = var.enabled ? local.roles : toset([])

  name                       = var.online_key_names[each.value]
  key_ring                   = google_kms_key_ring.registry[0].id
  purpose                    = "ASYMMETRIC_SIGN"
  destroy_scheduled_duration = "2592000s"
  labels                     = merge(local.common_labels, { tuf_role = each.value })

  version_template {
    algorithm        = "EC_SIGN_ED25519"
    protection_level = "SOFTWARE"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_kms_crypto_key_iam_member" "signer" {
  for_each = var.enabled ? local.selected_signer_roles : toset([])

  crypto_key_id = google_kms_crypto_key.online[each.value].id
  role          = "projects/${var.project_id}/roles/${google_project_iam_custom_role.kms_exact_signer[0].role_id}"
  member        = "serviceAccount:${google_service_account.runtime["signer_${each.value}"].email}"
}

resource "google_kms_crypto_key_iam_member" "reviewer_public_key" {
  for_each = var.enabled && var.reviewer_service_account != null ? local.roles : toset([])

  crypto_key_id = google_kms_crypto_key.online[each.value].id
  role          = "roles/cloudkms.publicKeyViewer"
  member        = "serviceAccount:${var.reviewer_service_account}"
}
