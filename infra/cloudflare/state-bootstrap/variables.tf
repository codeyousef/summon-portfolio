variable "cloudflare_account_id" {
  description = "Cloudflare account ID. It is not a secret, but must be supplied through the ignored tfvars file."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.cloudflare_account_id == null ||
      can(regex("^[0-9a-f]{32}$", var.cloudflare_account_id))
    )
    error_message = "cloudflare_account_id must be a 32-character lowercase hexadecimal account ID."
  }
}

variable "state_bucket_enabled" {
  description = "Fail-closed creation gate. Enable only for one explicitly approved state-bootstrap plan."
  type        = bool
  default     = false
}

variable "state_bucket_name" {
  description = "Globally unique private R2 bucket used by the dev and prod S3 backends."
  type        = string
  default     = "yousef-cloudflare-tofu-state"

  validation {
    condition = (
      length(var.state_bucket_name) >= 3 &&
      length(var.state_bucket_name) <= 63 &&
      can(regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$", var.state_bucket_name))
    )
    error_message = "state_bucket_name must be 3-63 lowercase letters, numbers, or hyphens."
  }
}

variable "state_bucket_location" {
  description = "R2 creation location hint for CI state access."
  type        = string
  default     = "enam"

  validation {
    condition     = contains(["apac", "eeur", "enam", "oc", "weur", "wnam"], var.state_bucket_location)
    error_message = "state_bucket_location must be an R2-supported location hint."
  }
}
