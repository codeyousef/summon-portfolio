variable "aws_account_id" {
  type = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "environment" {
  type = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be dev or prod."
  }
}

variable "region" {
  type    = string
  default = "eu-north-1"

  validation {
    condition     = var.region == "eu-north-1"
    error_message = "The organization region policy allows regional resources only in eu-north-1."
  }
}

variable "enabled" {
  description = "Fail-closed gate for registry data stores, network, and identities."
  type        = bool
  default     = false
}

variable "certificates_validated" {
  description = "Set only after the internal certificate from certificate_validation_records reports ISSUED."
  type        = bool
  default     = false
}

variable "running" {
  description = "Run the internal ALB, services, and jobs. Data, identities, keys, and the certificate persist when false."
  type        = bool
  default     = true
}

variable "kms_keys_enabled" {
  description = "Separately approved phase that creates the four online Ed25519 signing keys."
  type        = bool
  default     = false
}

variable "network" {
  description = "Platform network and cluster the registry runs in."
  type = object({
    vpc_id                 = string
    public_subnet_ids      = list(string)
    isolated_subnet_ids    = list(string)
    cluster_arn            = string
    zone_id                = string
    host_suffix            = string
    gateway_security_group = string
  })
  default = null
}

variable "repository_id" {
  type    = string
  default = null
}

variable "registry_origin" {
  description = "Public HTTPS origin and path served through the portfolio gateway."
  type        = string
  default     = null

  validation {
    condition     = var.registry_origin == null || (startswith(var.registry_origin, "https://") && !endswith(var.registry_origin, "/"))
    error_message = "registry_origin must be an HTTPS origin/path without a trailing slash."
  }
}

variable "image" {
  description = "Routine registry image pinned by digest. Null keeps every registry runtime absent."
  type        = string
  default     = null

  validation {
    condition     = var.image == null || can(regex("^[0-9]{12}\\.dkr\\.ecr\\.eu-north-1\\.amazonaws\\.com/seen-registry@sha256:[0-9a-f]{64}$", var.image))
    error_message = "image must be the seen-registry ECR repository pinned by @sha256 digest."
  }
}

variable "signer_image" {
  description = "Privileged signer image pinned by digest, reviewed separately from the routine image."
  type        = string
  default     = null

  validation {
    condition     = var.signer_image == null || can(regex("^[0-9]{12}\\.dkr\\.ecr\\.eu-north-1\\.amazonaws\\.com/seen-registry-signer@sha256:[0-9a-f]{64}$", var.signer_image))
    error_message = "signer_image must be the seen-registry-signer ECR repository pinned by @sha256 digest."
  }
}

variable "online_public_keys_hex" {
  description = "Reviewed raw Ed25519 public keys for releases, security, snapshot, and timestamp, recorded during the ceremony."
  type        = map(string)
  default     = {}

  validation {
    condition = alltrue([
      for role, key in var.online_public_keys_hex :
      contains(["releases", "security", "snapshot", "timestamp"], role) && can(regex("^[0-9a-f]{64}$", key))
    ])
    error_message = "online_public_keys_hex keys must be online roles and values 32-byte lowercase hex."
  }
}

variable "trusted_root_v1_sha256" {
  description = "Pinned SHA-256 of 1.root.json from the reviewed bootstrap ceremony."
  type        = string
  default     = null

  validation {
    condition     = var.trusted_root_v1_sha256 == null || can(regex("^[0-9a-f]{64}$", var.trusted_root_v1_sha256))
    error_message = "trusted_root_v1_sha256 must be lowercase hex SHA-256."
  }
}

variable "schedules_enabled" {
  description = "Registry job schedules are created DISABLED and enabled only under a separate approval naming every schedule."
  type        = bool
  default     = false
}

variable "signer_lambda_enabled" {
  description = "Create the TUF signers on Lambda."
  type        = bool
  default     = false
}

variable "signer_lambda_serving" {
  description = "Send coordinator signing calls to the Lambda signers."
  type        = bool
  default     = false
}

variable "api_lambda_enabled" {
  description = "Create the read-only API on Lambda."
  type        = bool
  default     = false
}

variable "api_lambda_serving" {
  description = "Send gateway traffic to the Lambda API instead of the internal load balancer."
  type        = bool
  default     = false
}

variable "refresh_schedules_enabled" {
  description = "Separately gated refresh schedules."
  type        = bool
  default     = false
}

variable "public_delay_seconds" {
  type    = number
  default = 259200

  validation {
    condition     = var.public_delay_seconds >= 259200
    error_message = "public_delay_seconds must be at least three days."
  }
}

variable "log_retention_days" {
  type    = number
  default = 90
}

variable "data_copy" {
  description = <<-EOF
    Development-only GCP source for the one-shot registry copy job. The task
    reads GCP through a read-only federated identity whose configuration is
    stored in the FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64 secret, and writes
    only when REGISTRY_COPY_CONFIRM is supplied for one approved run.
  EOF
  type = object({
    gcp_project        = string
    firestore_database = optional(string, "seen-registry-dev")
    source_buckets = object({
      quarantine = string
      public     = string
      metadata   = string
    })
  })
  default = null

  validation {
    condition = var.data_copy == null || alltrue([
      for bucket in values(var.data_copy.source_buckets) : !strcontains(bucket, "prod")
    ])
    error_message = "The copy job reads development buckets only."
  }
}
