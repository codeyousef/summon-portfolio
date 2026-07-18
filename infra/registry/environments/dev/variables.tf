variable "project_id" {
  type    = string
  default = "portfolio-476219"
}

variable "region" {
  type    = string
  default = "us-central1"
}

variable "container_image" {
  description = "Immutable registry-service image URI including @sha256 digest."
  type        = string
  default     = null
  nullable    = true
}

variable "signer_container_image" {
  description = "Immutable signer image changed only by the reviewed privileged apply path."
  type        = string
  default     = null
  nullable    = true
}

variable "online_key_version_numbers" {
  type    = map(string)
  default = {}
}

variable "online_public_keys_hex" {
  type    = map(string)
  default = {}
}

variable "trusted_root_v1_sha256" {
  type     = string
  default  = null
  nullable = true
}

variable "secret_versions" {
  description = "Reviewed numeric Secret Manager versions; never use latest."
  type        = map(string)
  default     = {}
}

variable "portfolio_gateway_service_account" {
  type     = string
  default  = null
  nullable = true
}

variable "reviewer_service_account" {
  type     = string
  default  = null
  nullable = true
}

variable "github_ci_enabled" {
  type    = bool
  default = false
}

variable "notification_channel_ids" {
  type    = list(string)
  default = []
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

variable "workloads_enabled" {
  type    = bool
  default = false
}

variable "refresh_jobs_enabled" {
  type    = bool
  default = false
}

variable "ceremony_operations" {
  type    = set(string)
  default = []
}

variable "schedules_enabled" {
  type    = bool
  default = false
}

variable "schedules_paused" {
  type    = bool
  default = true
}

variable "edge_provisioned" {
  type    = bool
  default = false
}

variable "edge_cutover_enabled" {
  type    = bool
  default = false
}
