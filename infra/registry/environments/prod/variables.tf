variable "project_id" {
  description = "Dedicated production project ID. It must not be portfolio-476219."
  type        = string
}

variable "region" {
  type    = string
  default = "us-central1"
}

variable "enable_production_foundation" {
  description = "Creates only the inert production foundation after a separately reviewed approval."
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
