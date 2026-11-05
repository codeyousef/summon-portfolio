output "state_backend_contract" {
  description = "Non-secret values used to prepare the dev and prod partial backend files."
  value = {
    bucket   = var.state_bucket_name
    endpoint = var.cloudflare_account_id == null ? null : "https://${var.cloudflare_account_id}.r2.cloudflarestorage.com"
    lockfile = true
    active_keys = {
      dev  = "state/dev/terraform.tfstate"
      prod = "state/prod/terraform.tfstate"
    }
    immutable_backup_prefix = "backups/"
  }
}

output "state_bucket_created" {
  value = var.state_bucket_enabled
}
