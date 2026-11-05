variable "aws_account_id" {
  description = "The dedicated Portfolio AWS account. The provider refuses to run against any other account."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "region" {
  description = "Regional home for the state bucket. The organization SCP permits regional services only in eu-north-1."
  type        = string
  default     = "eu-north-1"

  validation {
    condition     = var.region == "eu-north-1"
    error_message = "The organization region policy allows regional resources only in eu-north-1."
  }
}

variable "bootstrap_enabled" {
  description = "Fail-closed creation gate. Enable only for one explicitly approved state-bootstrap plan."
  type        = bool
  default     = false
}

variable "state_bucket_name" {
  description = "Globally unique private S3 bucket used by the shared, dev, and prod S3 backends."
  type        = string
  default     = "yousef-portfolio-tofu-state"

  validation {
    condition = (
      length(var.state_bucket_name) >= 3 &&
      length(var.state_bucket_name) <= 63 &&
      can(regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$", var.state_bucket_name))
    )
    error_message = "state_bucket_name must be 3-63 lowercase letters, numbers, or hyphens."
  }
}

variable "monthly_budget_usd" {
  description = "Monthly cost budget for the Portfolio account."
  type        = number
  default     = 150

  validation {
    condition     = var.monthly_budget_usd > 0
    error_message = "monthly_budget_usd must be positive."
  }
}

variable "budget_alert_email" {
  description = "Recipient for budget alerts. Supply it through the ignored tfvars file."
  type        = string
  default     = null
  nullable    = true
}
