check "registry_requires_network" {
  assert {
    condition     = !var.enabled || var.network != null
    error_message = "The registry requires the platform network."
  }
}

check "kms_requires_registry" {
  assert {
    condition     = !var.kms_keys_enabled || var.enabled
    error_message = "Signing keys require the enabled registry foundation."
  }
}

check "public_keys_are_distinct" {
  assert {
    condition     = length(distinct(values(var.online_public_keys_hex))) == length(var.online_public_keys_hex)
    error_message = "Every online role must have its own public key."
  }
}

check "production_is_read_only" {
  assert {
    condition = local.is_dev || alltrue([
      for name, runtime in local.runtimes : runtime.kind == "job" || name == "read-only-api"
    ])
    error_message = "Production runs only the read-only API plus maintenance jobs and signers."
  }
}
