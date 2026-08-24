check "bootstrap_inputs_are_complete" {
  assert {
    condition     = !var.state_bucket_enabled || var.cloudflare_account_id != null
    error_message = "Enabling the state bucket requires cloudflare_account_id."
  }
}

resource "cloudflare_r2_bucket" "state" {
  count = var.state_bucket_enabled ? 1 : 0

  account_id    = var.cloudflare_account_id
  name          = var.state_bucket_name
  location      = var.state_bucket_location
  storage_class = "Standard"

  lifecycle {
    prevent_destroy = true
  }
}
