locals {
  writer_firestore_users = toset([
    "api",
    "source",
    "scanner",
    "promoter",
    "release_actions",
    "security_actions",
  ])

  ceremony_firestore_users = setintersection(
    var.ceremony_operations,
    toset([
      "online_bootstrap",
      "targets_renewal",
      "targets_releases_rotation",
      "targets_security_rotation",
      "root_importer",
    ]),
  )

  firestore_users = setunion(
    var.workloads_enabled ? local.writer_firestore_users : toset([]),
    var.refresh_jobs_enabled ? toset(["release_refresh", "security_refresh"]) : toset([]),
    local.ceremony_firestore_users,
  )

  firestore_readers = local.read_only_api_enabled ? toset(["api"]) : toset([])

  writer_metadata_readers = toset([
    "api",
    "promoter",
    "release_actions",
    "security_actions",
  ])

  signer_metadata_readers = toset([
    for role in local.selected_signer_roles : "signer_${role}"
  ])

  metadata_readers = setunion(
    var.workloads_enabled ? local.writer_metadata_readers : toset([]),
    local.read_only_api_enabled ? toset(["api"]) : toset([]),
    local.signers_enabled ? local.signer_metadata_readers : toset([]),
    local.root_verifier_job_enabled ? toset(["root_verifier"]) : toset([]),
    var.refresh_jobs_enabled ? toset(["release_refresh", "security_refresh"]) : toset([]),
    var.ceremony_operations,
  )

  writer_metadata_creator_suffixes = {
    promoter         = [".releases.json", ".snapshot.json"]
    release_actions  = [".releases.json", ".snapshot.json"]
    security_actions = [".security.json", ".snapshot.json"]
  }

  refresh_metadata_creator_suffixes = {
    release_refresh  = [".releases.json", ".snapshot.json"]
    security_refresh = [".security.json", ".snapshot.json"]
  }

  ceremony_metadata_creator_suffixes = {
    targets_renewal           = [".targets.json", ".snapshot.json"]
    targets_releases_rotation = [".targets.json", ".releases.json", ".snapshot.json"]
    targets_security_rotation = [".targets.json", ".security.json", ".snapshot.json"]
    root_importer             = [".root.json"]
  }

  metadata_creator_suffixes = merge(
    var.workloads_enabled ? local.writer_metadata_creator_suffixes : {},
    var.refresh_jobs_enabled ? local.refresh_metadata_creator_suffixes : {},
    {
      for identity, suffixes in local.ceremony_metadata_creator_suffixes :
      identity => suffixes if contains(var.ceremony_operations, identity)
    },
  )

  bootstrap_metadata_objects = {
    offline_root    = { identity = "offline_bootstrap_importer", filename = "1.root.json" }
    offline_targets = { identity = "offline_bootstrap_importer", filename = "1.targets.json" }
    online_releases = { identity = "online_bootstrap", filename = "1.releases.json" }
    online_security = { identity = "online_bootstrap", filename = "1.security.json" }
    online_snapshot = { identity = "online_bootstrap", filename = "1.snapshot.json" }
  }
}

resource "google_project_iam_member" "firestore_user" {
  for_each = var.enabled ? local.firestore_users : toset([])

  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.runtime[each.value].email}"

  condition {
    title       = "seen_registry_${var.environment}_database_${replace(each.value, "_", "-")}"
    description = "Restricts registry data access to its environment-specific Firestore database."
    expression = join(" || ", [
      "resource.name == 'projects/${var.project_id}/databases/${var.firestore_database}'",
      "resource.name.startsWith('projects/${var.project_id}/databases/${var.firestore_database}/')",
    ])
  }
}

resource "google_project_iam_member" "firestore_reader" {
  for_each = var.enabled ? local.firestore_readers : toset([])

  project = var.project_id
  role    = "roles/datastore.viewer"
  member  = "serviceAccount:${google_service_account.runtime[each.value].email}"

  condition {
    title       = "seen_registry_${var.environment}_database_read_${replace(each.value, "_", "-")}"
    description = "Restricts read-only registry data access to its environment-specific Firestore database."
    expression = join(" || ", [
      "resource.name == 'projects/${var.project_id}/databases/${var.firestore_database}'",
      "resource.name.startsWith('projects/${var.project_id}/databases/${var.firestore_database}/')",
    ])
  }
}

resource "google_storage_bucket_iam_member" "metadata_reader" {
  for_each = var.enabled ? local.metadata_readers : toset([])

  bucket = google_storage_bucket.registry["metadata"].name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.runtime[each.value].email}"

  condition {
    title       = "metadata_read_${replace(each.value, "_", "-")}"
    description = "Reads only the environment metadata prefix. Object listing is intentionally not granted."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name.startsWith('${local.metadata_object_prefix}')"
  }
}

resource "google_storage_bucket_iam_member" "metadata_creator" {
  for_each = var.enabled ? local.metadata_creator_suffixes : {}

  bucket = google_storage_bucket.registry["metadata"].name
  role   = google_project_iam_custom_role.metadata_creator[0].name
  member = "serviceAccount:${google_service_account.runtime[each.key].email}"

  condition {
    title       = "immutable_metadata_${replace(each.key, "_", "-")}"
    description = "Can create only role-versioned metadata and cannot replace root.json or timestamp.json."
    expression = join(" && ", [
      "resource.type == 'storage.googleapis.com/Object'",
      "resource.name.startsWith('${local.metadata_object_prefix}')",
      "resource.name != '${local.timestamp_object_name}'",
      "resource.name != '${local.root_pointer_object_name}'",
      "(${join(" || ", [for suffix in each.value : "resource.name.endsWith('${suffix}')"])})",
    ])
  }
}

resource "google_storage_bucket_iam_member" "bootstrap_root_pointer_create" {
  count = var.enabled && contains(var.ceremony_operations, "offline_bootstrap_importer") ? 1 : 0

  bucket = google_storage_bucket.registry["metadata"].name
  role   = google_project_iam_custom_role.metadata_creator[0].name
  member = "serviceAccount:${google_service_account.runtime["offline_bootstrap_importer"].email}"

  condition {
    title       = "bootstrap_initial_root_pointer"
    description = "Bootstrap may create root.json once; create-only permission cannot replace it."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name == '${local.root_pointer_object_name}'"
  }
}

resource "google_storage_bucket_iam_member" "bootstrap_metadata_creator" {
  for_each = var.enabled ? {
    for key, binding in local.bootstrap_metadata_objects : key => binding
    if contains(var.ceremony_operations, binding.identity)
  } : {}

  bucket = google_storage_bucket.registry["metadata"].name
  role   = google_project_iam_custom_role.metadata_creator[0].name
  member = "serviceAccount:${google_service_account.runtime[each.value.identity].email}"

  condition {
    title       = "bootstrap_${replace(each.key, "_", "-")}"
    description = "Bootstrap authority is restricted to one exact version-one immutable metadata object."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name == '${local.metadata_object_prefix}${each.value.filename}'"
  }
}

resource "google_storage_bucket_iam_member" "timestamp_pointer_replacer" {
  count = var.enabled && local.signers_enabled ? 1 : 0

  bucket = google_storage_bucket.registry["metadata"].name
  role   = google_project_iam_custom_role.pointer_replacer[0].name
  member = "serviceAccount:${google_service_account.runtime["signer_timestamp"].email}"

  condition {
    title       = "timestamp_pointer_commit"
    description = "The timestamp signer is the sole online authority that can generation-CAS timestamp.json."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name == '${local.timestamp_object_name}'"
  }
}

resource "google_storage_bucket_iam_member" "root_pointer_replacer" {
  count = var.enabled && contains(var.ceremony_operations, "root_importer") ? 1 : 0

  bucket = google_storage_bucket.registry["metadata"].name
  role   = google_project_iam_custom_role.pointer_replacer[0].name
  member = "serviceAccount:${google_service_account.runtime["root_importer"].email}"

  condition {
    title       = "root_pointer_import"
    description = "Only the ephemeral offline-authorized root importer may generation-CAS root.json."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name == '${local.root_pointer_object_name}'"
  }
}

resource "google_storage_bucket_iam_member" "api_quarantine" {
  count = var.enabled && var.workloads_enabled ? 1 : 0

  bucket = google_storage_bucket.registry["quarantine"].name
  role   = google_project_iam_custom_role.quarantine_writer[0].name
  member = "serviceAccount:${google_service_account.runtime["api"].email}"

  condition {
    title       = "api_quarantine_prefix"
    description = "API creates and verifies uploads but cannot delete or overwrite them."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name.startsWith('${local.quarantine_object_prefix}')"
  }
}

resource "google_storage_bucket_iam_member" "review_quarantine_reader" {
  for_each = var.enabled && var.workloads_enabled ? toset(["source", "scanner"]) : toset([])

  bucket = google_storage_bucket.registry["quarantine"].name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.runtime[each.value].email}"

  condition {
    title       = "quarantine_read_${each.value}"
    description = "Review worker reads only quarantined package archives."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name.startsWith('${local.quarantine_object_prefix}')"
  }
}

resource "google_storage_bucket_iam_member" "promoter_quarantine" {
  count = var.enabled && var.workloads_enabled ? 1 : 0

  bucket = google_storage_bucket.registry["quarantine"].name
  role   = google_project_iam_custom_role.quarantine_promoter[0].name
  member = "serviceAccount:${google_service_account.runtime["promoter"].email}"

  condition {
    title       = "promoter_quarantine_prefix"
    description = "Promoter reads and removes only quarantined package archives."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name.startsWith('${local.quarantine_object_prefix}')"
  }
}

resource "google_storage_bucket_iam_member" "api_public_reader" {
  count = var.enabled && local.api_enabled ? 1 : 0

  bucket = google_storage_bucket.registry["public"].name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.runtime["api"].email}"

  condition {
    title       = "api_public_blob_read"
    description = "API reads immutable content-addressed blobs for the authenticated private origin."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name.startsWith('${local.public_object_prefix}')"
  }
}

resource "google_storage_bucket_iam_member" "promoter_public_creator" {
  count = var.enabled && var.workloads_enabled ? 1 : 0

  bucket = google_storage_bucket.registry["public"].name
  role   = google_project_iam_custom_role.public_blob_creator[0].name
  member = "serviceAccount:${google_service_account.runtime["promoter"].email}"

  condition {
    title       = "promoter_public_blob_create"
    description = "Promoter creates and verifies content-addressed blobs without replacement authority."
    expression  = "resource.type == 'storage.googleapis.com/Object' && resource.name.startsWith('${local.public_object_prefix}')"
  }
}
