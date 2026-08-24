variable "resources_enabled" {
  description = "Fail-closed gate for the dev-only FinOps billing export and WIF resources."
  type        = bool
  default     = false
}

variable "project_id" {
  description = "Dedicated GCP project used to execute bounded billing export queries."
  type        = string
  default     = "felidai-dev"

  validation {
    condition     = var.project_id == "felidai-dev"
    error_message = "This root is dev-only and may manage only felidai-dev."
  }
}

variable "billing_account_id" {
  description = "Cloud Billing account ID whose standard export will populate the dataset."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.billing_account_id == null ||
      can(regex("^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$", var.billing_account_id))
    )
    error_message = "billing_account_id must use the XXXXXX-XXXXXX-XXXXXX format."
  }
}

variable "billing_dataset_id" {
  description = "Dataset that receives the standard Cloud Billing export."
  type        = string
  default     = "billing_export"

  validation {
    condition = (
      length(var.billing_dataset_id) <= 1024 &&
      can(regex("^[A-Za-z_][A-Za-z0-9_]*$", var.billing_dataset_id))
    )
    error_message = "billing_dataset_id must be a valid BigQuery dataset ID."
  }
}

variable "billing_source_dataset_id" {
  description = "Existing standard billing-export dataset read by the Worker; defaults to the managed dataset."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.billing_source_dataset_id == null ||
      (
        length(var.billing_source_dataset_id) <= 1024 &&
        can(regex("^[A-Za-z_][A-Za-z0-9_]*$", var.billing_source_dataset_id))
      )
    )
    error_message = "billing_source_dataset_id must be a valid BigQuery dataset ID."
  }
}

variable "billing_dataset_location" {
  description = "Location for the North America-first billing export dataset."
  type        = string
  default     = "US"

  validation {
    condition     = var.billing_dataset_location == "US"
    error_message = "The dev FinOps billing export dataset must remain in the US multi-region."
  }
}

variable "access_issuer" {
  description = "Exact Cloudflare Access team issuer, without a trailing slash."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.access_issuer == null ||
      can(regex("^https://[a-z0-9][a-z0-9-]{1,61}[a-z0-9]\\.cloudflareaccess\\.com$", var.access_issuer))
    )
    error_message = "access_issuer must be an exact cloudflareaccess.com HTTPS issuer."
  }
}

variable "access_application_audience" {
  description = "Exact audience of the dedicated Cloudflare Access WIF broker application."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.access_application_audience == null ||
      can(regex("^[0-9a-f]{64}$", var.access_application_audience))
    )
    error_message = "access_application_audience must be a 64-character lowercase hexadecimal audience."
  }
}

variable "access_service_token_client_id" {
  description = "Immutable client ID of the dedicated Cloudflare Access service token."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.access_service_token_client_id == null ||
      can(regex("^[0-9a-f]{32}\\.access$", var.access_service_token_client_id))
    )
    error_message = "access_service_token_client_id must be a Cloudflare Access service-token client ID."
  }
}
