variable "aws_account_id" {
  description = "The dedicated Portfolio AWS account."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "environment" {
  description = "Deployment environment."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be dev or prod."
  }
}

variable "region" {
  description = "Regional home. The organization SCP permits regional services only in eu-north-1."
  type        = string
  default     = "eu-north-1"

  validation {
    condition     = var.region == "eu-north-1"
    error_message = "The organization region policy allows regional resources only in eu-north-1."
  }
}

variable "resources_enabled" {
  description = "Fail-closed creation gate. Nothing is created until an approved plan enables it."
  type        = bool
  default     = false
}

variable "zone_name" {
  description = "Route 53 zone owned by the shared stack."
  type        = string
  default     = "yousef.codes"
}

variable "dev_gate_owner_email" {
  description = "Only Cognito user allowed through the development gate. Supply it through the ignored tfvars file."
  type        = string
  default     = null
  nullable    = true
}

variable "images" {
  description = <<-EOT
    Initial image references (repository URL with an immutable @sha256 digest)
    used when OpenTofu registers task definitions. Later rollouts are performed
    by the protected deploy identities. A null image keeps that service absent.
  EOT
  type = object({
    ssr        = optional(string)
    playground = optional(string)
  })
  default = {}

  validation {
    condition = alltrue([
      for image in [var.images.ssr, var.images.playground] :
      image == null ? true : can(regex("^[0-9]{12}\\.dkr\\.ecr\\.eu-north-1\\.amazonaws\\.com/[a-z0-9-]+@sha256:[0-9a-f]{64}$", image))
    ])
    error_message = "Images must be ECR references pinned by @sha256 digest."
  }
}

variable "load_balancer_enabled" {
  description = "Use the shared public load balancer and the ECS site service."
  type        = bool
  default     = true

  validation {
    condition     = var.load_balancer_enabled || var.environment == "dev" || var.site_lambda.serving
    error_message = "Production must serve from Lambda before the load balancer is removed."
  }
}

variable "site_lambda" {
  description = "Run the site on Lambda. canary adds a test path through CloudFront; serving moves traffic."
  type = object({
    enabled = optional(bool, false)
    canary  = optional(bool, false)
    serving = optional(bool, false)
  })
  default = {}
}

variable "site_lambda_origin_secret" {
  description = "Secret CloudFront sends to the site Lambda."
  type        = string
  default     = null
  sensitive   = true

  validation {
    condition     = var.site_lambda_origin_secret == null || length(coalesce(var.site_lambda_origin_secret, "")) >= 32
    error_message = "The origin secret must be at least 32 characters."
  }
}

variable "service_sizes" {
  description = "Fargate sizing and desired counts."
  type = object({
    ssr_cpu                  = optional(number, 512)
    ssr_memory               = optional(number, 1024)
    ssr_desired_count        = optional(number, 1)
    playground_cpu           = optional(number, 1024)
    playground_memory        = optional(number, 2048)
    playground_desired_count = optional(number, 1)
  })
  default = {}
}

variable "certificates_validated" {
  description = <<-EOT
    Second edge phase. Until the zone is delegated, ACM validation records must
    also exist at the current DNS host; set this only after every certificate
    from certificate_validation_records reports ISSUED.
  EOT
  type        = bool
  default     = false
}

variable "use_fargate_spot" {
  description = "Run tasks on Fargate Spot. Appropriate for dev only."
  type        = bool
  default     = false
}

variable "schedules_enabled" {
  description = <<-EOT
    Scheduler state. Schedules are created DISABLED and may be enabled only
    after their target, caller identity, retry policy, and one
    scheduler-originated execution are verified, under a separate approval.
  EOT
  type        = bool
  default     = false
}

variable "app_environment" {
  description = "Non-secret container environment overrides for the SSR service."
  type        = map(string)
  default     = {}
}

variable "app_secret_names" {
  description = "Secrets Manager secrets (created empty; values are set out of band) injected into the SSR service as same-named environment variables."
  type        = set(string)
  default = [
    "CLOUDFLARE_BILLING_API_TOKEN",
    "FINOPS_IDENTITY_READ_TOKEN",
    "GITHUB_TOKEN",
    "SAMURAI_FINOPS_ACCESS_CLIENT_SECRET",
    "STRIPE_FINOPS_READ_KEY",
  ]

  validation {
    condition     = alltrue([for name in var.app_secret_names : can(regex("^[A-Z][A-Z0-9_]*$", name))])
    error_message = "Secret names must be upper-case environment variable names."
  }
}

variable "log_retention_days" {
  type    = number
  default = 30
}
