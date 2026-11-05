variable "aws_account_id" {
  description = "The dedicated Portfolio AWS account."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account ID."
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
  description = "Fail-closed creation gate for the shared account foundation."
  type        = bool
  default     = false
}

variable "zone_name" {
  description = "Public DNS zone moved from Cloudflare."
  type        = string
  default     = "yousef.codes"
}

variable "dns_records" {
  description = <<-EOT
    Non-application records recreated from the reviewed Cloudflare zone export
    (mail, verification, and other third-party records). Application hostnames
    are owned by the dev and prod stacks and must not appear here.
  EOT
  type = map(object({
    name    = string
    type    = string
    ttl     = optional(number, 300)
    records = list(string)
  }))
  default = {}

  validation {
    condition = alltrue([
      for record in values(var.dns_records) :
      contains(["A", "AAAA", "CAA", "CNAME", "MX", "SRV", "TXT"], record.type) && length(record.records) > 0
    ])
    error_message = "dns_records support A, AAAA, CAA, CNAME, MX, SRV, and TXT with at least one value."
  }
}

variable "github_repository" {
  description = "GitHub owner/repository whose protected environments may assume CI roles."
  type        = string
  default     = "codeyousef/summon-portfolio"

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must be owner/repository."
  }
}

variable "environments" {
  description = "Deployment environments that receive separate plan, apply, routine deploy, and signer deploy identities."
  type        = set(string)
  default     = ["dev", "prod"]

  validation {
    condition     = length(setsubtract(var.environments, ["dev", "prod"])) == 0
    error_message = "Only dev and prod environments are supported."
  }
}

variable "state_bucket_name" {
  description = "S3 bucket created by state-bootstrap."
  type        = string
  default     = "yousef-portfolio-tofu-state"
}

variable "image_repositories" {
  description = "ECR repositories. Signer images are isolated from routine images and deployed by a separate identity."
  type = map(object({
    privileged = bool
  }))
  default = {
    "portfolio-ssr"        = { privileged = false }
    "seen-playground"      = { privileged = false }
    "seen-registry"        = { privileged = false }
    "seen-registry-signer" = { privileged = true }
  }
}
