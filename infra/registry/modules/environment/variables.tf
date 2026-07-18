variable "enabled" {
  description = "Creates this environment. Production roots keep this false until launch approval."
  type        = bool
  default     = true
}

variable "project_id" {
  description = "Google Cloud project that contains this isolated registry environment."
  type        = string
}

variable "region" {
  type    = string
  default = "us-central1"
}

variable "environment" {
  description = "Short infrastructure environment name."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be dev or prod."
  }
}

variable "runtime_environment" {
  description = "Environment embedded in signed registry metadata."
  type        = string

  validation {
    condition     = contains(["development", "production"], var.runtime_environment)
    error_message = "runtime_environment must be development or production."
  }
}

variable "name_prefix" {
  description = "Resource prefix, for example seen-registry-dev."
  type        = string
}

variable "repository_id" {
  type = string
}

variable "registry_origin" {
  type = string

  validation {
    condition     = startswith(var.registry_origin, "https://") && !endswith(var.registry_origin, "/")
    error_message = "registry_origin must be an HTTPS origin/path without a trailing slash."
  }
}

variable "object_prefix" {
  type    = string
  default = "v1"

  validation {
    condition     = length(var.object_prefix) > 0 && !startswith(var.object_prefix, "/") && !endswith(var.object_prefix, "/")
    error_message = "object_prefix must be non-empty and have no leading or trailing slash."
  }
}

variable "public_delay_seconds" {
  type    = number
  default = 259200

  validation {
    condition     = var.public_delay_seconds >= 259200
    error_message = "The public review delay must be at least 72 hours."
  }
}

variable "labels" {
  type    = map(string)
  default = {}
}

variable "workloads_enabled" {
  description = "Deploys Cloud Run services and long-lived jobs. Keep false until signer state/commit guards are available."
  type        = bool
  default     = false
}

variable "refresh_jobs_enabled" {
  description = "Deploys role-specific refresh jobs after their CLI commands exist."
  type        = bool
  default     = false
}

variable "ceremony_operations" {
  description = "Exact ephemeral ceremony jobs to create and authorize for one reviewed operation."
  type        = set(string)
  default     = []

  validation {
    condition = length(var.ceremony_operations) <= 1 && length(setsubtract(var.ceremony_operations, toset([
      "offline_bootstrap_importer",
      "online_bootstrap",
      "targets_renewal",
      "targets_releases_rotation",
      "targets_security_rotation",
      "root_importer",
    ]))) == 0
    error_message = "ceremony_operations may select at most one known ceremony job for a reviewed operation."
  }
}

variable "schedules_enabled" {
  type    = bool
  default = false
}

variable "schedules_paused" {
  type    = bool
  default = true
}

variable "monitoring_enabled" {
  type    = bool
  default = true
}

variable "container_image" {
  description = "Immutable application/worker Artifact Registry image URI including sha256 digest."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.container_image == null || can(regex("@sha256:[0-9a-f]{64}$", var.container_image))
    error_message = "container_image must be null or an immutable sha256 image URI."
  }
}

variable "signer_container_image" {
  description = "Separately reviewed immutable signer image digest. Ordinary CI must never change this input."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.signer_container_image == null || can(regex("@sha256:[0-9a-f]{64}$", var.signer_container_image))
    error_message = "signer_container_image must be null or an immutable sha256 image URI."
  }
}

variable "firestore_database" {
  type = string
}

variable "firestore_location" {
  type    = string
  default = "us-central1"
}

variable "artifact_repository" {
  type    = string
  default = "seen-registry"
}

variable "bucket_names" {
  description = "Explicit names make existing dev buckets importable and prevent accidental cross-environment derivation."
  type = object({
    quarantine = string
    public     = string
    metadata   = string
    private    = string
    evidence   = string
    backup     = string
  })
}

variable "kms_key_ring" {
  type = string
}

variable "online_key_names" {
  type = map(string)

  validation {
    condition     = toset(keys(var.online_key_names)) == toset(["releases", "security", "snapshot", "timestamp"])
    error_message = "online_key_names must contain exactly releases, security, snapshot, and timestamp."
  }
}

variable "online_key_version_numbers" {
  description = "Concrete enabled KMS version number per role; never use latest."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for version in values(var.online_key_version_numbers) : can(regex("^[1-9][0-9]*$", version))])
    error_message = "Every online KMS version must be a positive numeric version."
  }
}

variable "online_public_keys_hex" {
  description = "Raw public Ed25519 keys. These are public ceremony inputs, not secret material."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for key in values(var.online_public_keys_hex) : can(regex("^[0-9a-f]{64}$", key))])
    error_message = "Every online public key must contain 32 lowercase-hex bytes."
  }
}

variable "trusted_root_v1_sha256" {
  description = "Reviewed SHA-256 pin of the immutable 1.root.json envelope; public trust input, never key material."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.trusted_root_v1_sha256 == null || can(regex("^[0-9a-f]{64}$", var.trusted_root_v1_sha256))
    error_message = "trusted_root_v1_sha256 must be null or 64 lowercase hexadecimal characters."
  }
}

variable "service_account_ids" {
  description = "Account IDs are explicit because Google service-account IDs are limited to 30 characters."
  type = object({
    api                        = string
    source                     = string
    scanner                    = string
    promoter                   = string
    release_actions            = string
    security_actions           = string
    release_refresh            = string
    security_refresh           = string
    signer_releases            = string
    signer_security            = string
    signer_snapshot            = string
    signer_timestamp           = string
    scheduler                  = string
    root_verifier              = string
    offline_bootstrap_importer = string
    online_bootstrap           = string
    targets_renewal            = string
    targets_releases_rotation  = string
    targets_security_rotation  = string
    root_importer              = string
  })

  validation {
    condition     = alltrue([for account_id in values(var.service_account_ids) : can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", account_id))])
    error_message = "Every service account ID must be 6-30 lowercase letters, digits, or hyphens, starting with a letter and ending with a letter or digit."
  }
}

variable "portfolio_gateway_service_account" {
  description = "Email of the existing portfolio gateway runtime. It receives only API/action invocation."
  type        = string
  default     = null
  nullable    = true
}

variable "reviewer_service_account" {
  description = "Optional manually controlled review identity. It may retrieve public KMS keys for pin verification and receives no image, runtime, data, signing, or apply authority."
  type        = string
  default     = null
  nullable    = true
}

variable "github_ci_enabled" {
  description = "Creates a repository/ref/workflow-bound GitHub OIDC provider and image-publisher identity."
  type        = bool
  default     = false
}

variable "github_repository" {
  description = "Exact GitHub owner/repository allowed to exchange an OIDC token."
  type        = string
  default     = null
  nullable    = true
}

variable "github_repository_id" {
  description = "Immutable numeric GitHub repository ID paired with github_repository to prevent name-reuse trust."
  type        = string
  default     = null
  nullable    = true
}

variable "github_repository_owner_id" {
  description = "Immutable numeric GitHub owner ID paired with github_repository to prevent owner-name reuse."
  type        = string
  default     = null
  nullable    = true
}

variable "github_ref" {
  description = "Exact Git ref allowed to publish routine application images."
  type        = string
  default     = null
  nullable    = true
}

variable "github_workflow_ref" {
  description = "Exact workflow identity, including @refs/heads/... suffix."
  type        = string
  default     = null
  nullable    = true
}

variable "github_event_name" {
  description = "Exact GitHub event allowed to request image-publication credentials."
  type        = string
  default     = null
  nullable    = true
}

variable "github_environment" {
  description = "Exact protected GitHub environment required before image-publication credentials can be minted."
  type        = string
  default     = null
  nullable    = true
}

variable "secret_names" {
  description = "Secret containers only. OpenTofu never manages their payload versions."
  type = object({
    publisher_token                    = string
    trust_and_safety_token             = string
    security_token                     = string
    github_app_id                      = string
    github_app_private_key             = string
    gitlab_forge_token                 = string
    bootstrap_root_envelope            = string
    bootstrap_targets_envelope         = string
    targets_renewal_envelope           = string
    targets_releases_rotation_envelope = string
    targets_security_rotation_envelope = string
    root_rotation_envelope             = string
  })
}

variable "secret_replica_locations" {
  description = "Optional user-managed replica locations keyed by secret role. Omitted roles use automatic replication."
  type        = map(list(string))
  default     = {}

  validation {
    condition = alltrue(flatten([
      for locations in values(var.secret_replica_locations) : [
        length(locations) > 0,
        alltrue([for location in locations : length(trimspace(location)) > 0]),
      ]
    ]))
    error_message = "Every configured secret replica list and location must be non-empty."
  }
}

variable "secret_versions" {
  description = "Numeric Secret Manager versions mounted by workloads. Values are version numbers, never payloads."
  type        = map(string)
  default     = {}
  sensitive   = false

  validation {
    condition     = alltrue([for version in values(var.secret_versions) : can(regex("^[1-9][0-9]*$", version))])
    error_message = "Secret references must use concrete positive numeric versions."
  }
}

variable "notification_channel_ids" {
  type    = list(string)
  default = []
}

variable "billing_account_id" {
  description = "Optional billing account ID used for a project-scoped monthly budget."
  type        = string
  default     = null
  nullable    = true
}

variable "monthly_budget_usd" {
  description = "Optional whole-dollar monthly budget. Set with billing_account_id."
  type        = number
  default     = null
  nullable    = true

  validation {
    condition     = var.monthly_budget_usd == null || (var.monthly_budget_usd > 0 && floor(var.monthly_budget_usd) == var.monthly_budget_usd)
    error_message = "monthly_budget_usd must be a positive whole-dollar amount."
  }
}

variable "private_service_ingress" {
  type    = string
  default = "INGRESS_TRAFFIC_ALL"

  validation {
    condition = contains([
      "INGRESS_TRAFFIC_INTERNAL_ONLY",
      "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER",
      "INGRESS_TRAFFIC_ALL",
    ], var.private_service_ingress)
    error_message = "Registry origins must use an internal Cloud Run ingress mode."
  }
}

variable "signer_jwks_all_apis_enabled" {
  description = "Development-only canary exception allowing signer-tagged workloads to reach the all-APIs private VIP for Google OIDC JWKS verification."
  type        = bool
  default     = false
}

variable "edge_provisioned" {
  description = "Creates the no-DNS-change HTTPS edge while leaving candidate origin access unchanged."
  type        = bool
  default     = false
}

variable "edge_cutover_enabled" {
  description = "Switches candidate application origins to LB-only ingress, disables run.app URLs, and removes gateway invocation."
  type        = bool
  default     = false
}

variable "portfolio_fallback_service" {
  description = "Existing Cloud Run service used as the URL-map fallback for docs, playground, and every non-registry path."
  type        = string
  default     = null
  nullable    = true
}

variable "edge_certificate_domains" {
  description = "Domains placed on the managed certificate. DNS is intentionally managed outside this module."
  type        = list(string)
  default     = []
}

variable "network_cidr" {
  type    = string
  default = "10.42.0.0/24"
}

variable "uptime_host" {
  description = "Public gateway host used for the metadata uptime check."
  type        = string
}

variable "create_artifact_repository" {
  type    = bool
  default = true
}

variable "create_firestore_database" {
  type    = bool
  default = true
}
