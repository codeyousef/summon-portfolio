variable "project_id" {
  description = "Dedicated production project ID. It must not be portfolio-476219."
  type        = string
}

variable "region" {
  type    = string
  default = "us-central1"
}

variable "enable_production_foundation" {
  description = "Creates the production foundation after a separately reviewed approval. Every launch surface remains independently false by default."
  type        = bool
  default     = false
}

variable "enable_production_read_only_api" {
  description = "Deploys only the credential-free GET-only production catalog API after its immutable image and public trust inputs are reviewed."
  type        = bool
  default     = false
}

variable "enable_production_root_verifier" {
  description = "Deploys the read-only root-chain verifier independently from writer jobs."
  type        = bool
  default     = false
}

variable "enable_production_refresh_jobs" {
  description = "Deploys only releases/security metadata refresh jobs and their role-locked signers. It never creates or enables schedules."
  type        = bool
  default     = false
}

variable "production_ceremony_operations" {
  description = "Selects at most one reviewed ephemeral production ceremony job. Empty removes all ceremony jobs and authority."
  type        = set(string)
  default     = []

  validation {
    condition = length(var.production_ceremony_operations) <= 1 && length(setsubtract(var.production_ceremony_operations, toset([
      "offline_bootstrap_importer",
      "online_bootstrap",
      "targets_renewal",
      "targets_releases_rotation",
      "targets_security_rotation",
      "root_importer",
    ]))) == 0
    error_message = "production_ceremony_operations may select at most one known ceremony job."
  }
}

variable "container_image" {
  description = "Immutable production application/job image URI including @sha256 digest."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.container_image == null || can(regex("@sha256:[0-9a-f]{64}$", var.container_image))
    error_message = "container_image must be null or an immutable sha256 image URI."
  }
}

variable "signer_container_image" {
  description = "Separately reviewed immutable production signer image URI including @sha256 digest."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.signer_container_image == null || can(regex("@sha256:[0-9a-f]{64}$", var.signer_container_image))
    error_message = "signer_container_image must be null or an immutable sha256 image URI."
  }
}

variable "online_key_version_numbers" {
  description = "Concrete enabled production KMS version number per online role; never latest."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for version in values(var.online_key_version_numbers) : can(regex("^[1-9][0-9]*$", version))])
    error_message = "Every online KMS version must be a positive numeric version."
  }
}

variable "online_public_keys_hex" {
  description = "Reviewed public Ed25519 key bytes for all online production roles."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for key in values(var.online_public_keys_hex) : can(regex("^[0-9a-f]{64}$", key))])
    error_message = "Every online public key must contain 32 lowercase-hex bytes."
  }
}

variable "trusted_root_v1_sha256" {
  description = "Reviewed SHA-256 pin of immutable production 1.root.json."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.trusted_root_v1_sha256 == null || can(regex("^[0-9a-f]{64}$", var.trusted_root_v1_sha256))
    error_message = "trusted_root_v1_sha256 must be null or 64 lowercase hexadecimal characters."
  }
}

variable "secret_versions" {
  description = "Reviewed numeric Secret Manager versions used only by the selected ceremony. Never use latest or place payloads in configuration."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for version in values(var.secret_versions) : can(regex("^[1-9][0-9]*$", version))])
    error_message = "Secret references must use concrete positive numeric versions."
  }
}

variable "portfolio_gateway_service_account" {
  description = "Existing portfolio runtime service-account email granted invocation of the selected API only."
  type        = string
  default     = null
  nullable    = true
}

variable "signer_jwks_all_apis_enabled" {
  description = "Explicit false-by-default signer OIDC JWKS egress gate. Enable only in the separately reviewed signer plan."
  type        = bool
  default     = false
}

variable "github_ci_enabled" {
  description = "Creates the exact master-branch, workflow, event, and protected-environment-bound production image publisher."
  type        = bool
  default     = false
}

variable "notification_channel_ids" {
  type    = list(string)
  default = []
}

variable "reviewer_service_account" {
  type     = string
  default  = null
  nullable = true
}

variable "billing_account_id" {
  type     = string
  default  = null
  nullable = true
}

variable "monthly_budget_usd" {
  type     = number
  default  = null
  nullable = true
}
