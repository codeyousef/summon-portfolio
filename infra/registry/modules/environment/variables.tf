variable "enabled" {
  description = "Creates this environment. Production roots keep this false until launch approval."
  type        = bool
  default     = true

  validation {
    condition = var.environment != "prod" || !var.enabled || (
      !var.workloads_enabled &&
      !var.schedules_enabled &&
      !var.edge_provisioned &&
      !var.edge_cutover_enabled
    )
    error_message = "Production permits only the explicitly selected read-only API, verifier, refresh, ceremony, and image-publication resources; writer workloads, schedules, and edge remain disabled."
  }
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

  validation {
    condition = (
      var.environment == "dev" && var.runtime_environment == "development"
      ) || (
      var.environment == "prod" && var.runtime_environment == "production"
    )
    error_message = "The short and signed environment names do not match."
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
  description = "Deploys the complete writer-capable development stack: public API, action services, review jobs, signers, and their mutation authority."
  type        = bool
  default     = false
}

variable "read_only_api_enabled" {
  description = "Deploys only the credential-free production public API. It receives Firestore plus public/metadata read authority and no writer surface."
  type        = bool
  default     = false

  validation {
    condition     = !var.read_only_api_enabled || (var.environment == "prod" && !var.workloads_enabled)
    error_message = "read_only_api_enabled is reserved for production and cannot be combined with the writer-capable stack."
  }
}

variable "root_verifier_job_enabled" {
  description = "Deploys the read-only root-chain verification job independently from the writer pipeline."
  type        = bool
  default     = false
}

variable "refresh_jobs_enabled" {
  description = "Deploys only the role-specific metadata refresh jobs and the signers they require. It does not create schedules."
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

  validation {
    condition = !var.enabled || !(
      var.workloads_enabled ||
      var.read_only_api_enabled ||
      var.root_verifier_job_enabled ||
      var.refresh_jobs_enabled ||
      length(var.ceremony_operations) > 0
    ) || var.container_image != null
    error_message = "Every selected container workload requires an immutable application image."
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

  validation {
    condition = !var.enabled || !(
      var.workloads_enabled ||
      var.refresh_jobs_enabled ||
      length(setintersection(var.ceremony_operations, toset([
        "online_bootstrap",
        "targets_renewal",
        "targets_releases_rotation",
        "targets_security_rotation",
      ]))) > 0
    ) || var.signer_container_image != null
    error_message = "Every selected signer-backed operation requires a separately reviewed immutable signer image."
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

  validation {
    condition     = length(var.online_key_names) == length(toset(values(var.online_key_names)))
    error_message = "Every online role must use a distinct KMS key resource."
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

  validation {
    condition = !var.enabled || toset(keys(var.online_key_version_numbers)) == setunion(
      var.workloads_enabled || var.refresh_jobs_enabled ? toset(["releases", "security", "snapshot", "timestamp"]) : toset([]),
      contains(var.ceremony_operations, "online_bootstrap") ? toset(["releases", "security", "snapshot", "timestamp"]) : toset([]),
      contains(var.ceremony_operations, "targets_renewal") ? toset(["snapshot", "timestamp"]) : toset([]),
      contains(var.ceremony_operations, "targets_releases_rotation") ? toset(["releases", "snapshot", "timestamp"]) : toset([]),
      contains(var.ceremony_operations, "targets_security_rotation") ? toset(["security", "snapshot", "timestamp"]) : toset([]),
    )
    error_message = "Enabled signer-backed operations require concrete versions for exactly their selected online roles."
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

  validation {
    condition     = length(var.online_public_keys_hex) == length(toset(values(var.online_public_keys_hex)))
    error_message = "Every online role must use a distinct public key."
  }

  validation {
    condition = !var.enabled || !(
      var.workloads_enabled ||
      var.read_only_api_enabled ||
      var.root_verifier_job_enabled ||
      var.refresh_jobs_enabled ||
      length(var.ceremony_operations) > 0
      ) || toset(keys(var.online_public_keys_hex)) == toset([
        "releases",
        "security",
        "snapshot",
        "timestamp",
    ])
    error_message = "Every selected container workload requires the complete reviewed online public-key set."
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

  validation {
    condition = !var.enabled || !(
      var.workloads_enabled ||
      var.refresh_jobs_enabled ||
      length(setintersection(var.ceremony_operations, toset([
        "online_bootstrap",
        "targets_renewal",
        "targets_releases_rotation",
        "targets_security_rotation",
      ]))) > 0
    ) || var.trusted_root_v1_sha256 != null
    error_message = "Every selected signer-backed operation requires the reviewed immutable root-envelope pin."
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

  validation {
    condition = !var.enabled || !(
      var.workloads_enabled || var.read_only_api_enabled
    ) || var.edge_cutover_enabled || var.portfolio_gateway_service_account != null
    error_message = "An enabled API requires the reviewed portfolio gateway service account unless an approved edge cutover owns invocation."
  }
}

variable "reviewer_service_account" {
  description = "Optional manually controlled review identity. It may retrieve public KMS keys for pin verification and receives no image, runtime, data, signing, or apply authority."
  type        = string
  default     = null
  nullable    = true
}

variable "infrastructure_executor_service_account" {
  description = "Optional OpenTofu apply identity. It receives read-only access to the exact image repository plus one pre-provisioned serviceAccountUser binding on each explicitly selected finite runtime account; it never receives a project-wide actAs grant."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = var.infrastructure_executor_service_account == null || (
      can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]@", var.infrastructure_executor_service_account)) &&
      endswith(var.infrastructure_executor_service_account, "@${var.project_id}.iam.gserviceaccount.com")
    )
    error_message = "infrastructure_executor_service_account must be a service-account email in this registry project."
  }

  validation {
    condition = var.infrastructure_executor_service_account == null || !contains(
      [for account_id in values(var.service_account_ids) : "${account_id}@${var.project_id}.iam.gserviceaccount.com"],
      var.infrastructure_executor_service_account,
    )
    error_message = "The infrastructure executor must remain distinct from every registry runtime service account."
  }

  validation {
    condition = var.environment != "prod" || !var.enabled || !(
      var.workloads_enabled ||
      var.read_only_api_enabled ||
      var.root_verifier_job_enabled ||
      var.refresh_jobs_enabled ||
      length(var.ceremony_operations) > 0
    ) || var.infrastructure_executor_service_account != null
    error_message = "Selected production Cloud Run services and jobs require the reviewed infrastructure executor service account."
  }
}

variable "job_operations_service_account" {
  description = "Optional protected operations identity. In production it receives only roles/run.invoker and the exact one-permission viewer role on each currently selected verifier, refresh, or ceremony job."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = var.job_operations_service_account == null || (
      can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]@", var.job_operations_service_account)) &&
      endswith(var.job_operations_service_account, "@${var.project_id}.iam.gserviceaccount.com")
    )
    error_message = "job_operations_service_account must be a service-account email in this registry project."
  }

  validation {
    condition = var.job_operations_service_account == null || (
      var.job_operations_service_account != var.infrastructure_executor_service_account &&
      !contains(
        [for account_id in values(var.service_account_ids) : "${account_id}@${var.project_id}.iam.gserviceaccount.com"],
        var.job_operations_service_account,
      )
    )
    error_message = "The job operations identity must remain distinct from the infrastructure executor and every registry runtime service account."
  }

  validation {
    condition = var.environment != "prod" || !var.enabled || !(
      var.root_verifier_job_enabled ||
      var.refresh_jobs_enabled ||
      length(var.ceremony_operations) > 0
    ) || var.job_operations_service_account != null
    error_message = "Selected production verifier, refresh, and ceremony jobs require the reviewed job operations service account."
  }
}

variable "job_operations_viewer_role" {
  description = "Optional exact same-project custom role containing only run.jobs.get for protected preflight validation. It must be paired with the job operations identity."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.job_operations_service_account == null &&
      var.job_operations_viewer_role == null
      ) || (
      var.job_operations_service_account != null &&
      var.job_operations_viewer_role == "projects/${var.project_id}/roles/seenRegistryJobViewer"
    )
    error_message = "job_operations_viewer_role must be null with no operations identity, or the exact same-project seenRegistryJobViewer role when that identity is configured."
  }
}

variable "infrastructure_executor_act_as_identities" {
  description = "Exact logical runtime identity keys on which the infrastructure executor receives serviceAccountUser. Must be explicit, finite, and cover every selected deployable workload."
  type        = set(string)
  default     = []

  validation {
    condition = (
      var.infrastructure_executor_service_account == null &&
      length(var.infrastructure_executor_act_as_identities) == 0
      ) || (
      var.infrastructure_executor_service_account != null &&
      length(var.infrastructure_executor_act_as_identities) > 0 &&
      length(setsubtract(
        var.infrastructure_executor_act_as_identities,
        toset(keys(var.service_account_ids)),
      )) == 0
    )
    error_message = "Executor actAs identities must be empty without an executor, or a non-empty subset of the module's exact runtime identity keys when an executor is configured."
  }

  validation {
    condition = var.environment != "prod" || length(setintersection(
      var.infrastructure_executor_act_as_identities,
      toset([
        "source",
        "scanner",
        "promoter",
        "release_actions",
        "security_actions",
        "scheduler",
      ]),
    )) == 0
    error_message = "Production executor actAs cannot include writer, review, promotion, action, or scheduler identities that the production root hard-disables."
  }
}

variable "github_ci_enabled" {
  description = "Creates a repository/ref/workflow-bound GitHub OIDC provider and image-publisher identity."
  type        = bool
  default     = false

  validation {
    condition = !var.github_ci_enabled || (
      var.enabled &&
      var.create_artifact_repository &&
      var.github_repository != null &&
      can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository)) &&
      can(regex("^[1-9][0-9]*$", var.github_repository_id)) &&
      can(regex("^[1-9][0-9]*$", var.github_repository_owner_id)) &&
      var.github_ref != null &&
      can(regex("^refs/heads/[A-Za-z0-9._/-]+$", var.github_ref)) &&
      var.github_workflow_ref != null &&
      can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/\\.github/workflows/[A-Za-z0-9_./-]+\\.ya?ml@refs/heads/[A-Za-z0-9._/-]+$", var.github_workflow_ref)) &&
      try(startswith(var.github_workflow_ref, "${var.github_repository}/"), false) &&
      try(endswith(var.github_workflow_ref, "@${var.github_ref}"), false) &&
      var.github_event_name != null &&
      can(regex("^[a-z_]+$", var.github_event_name)) &&
      var.github_environment != null &&
      can(regex("^[A-Za-z0-9._-]+$", var.github_environment))
    )
    error_message = "GitHub CI requires an enabled Artifact Registry plus exact repository/owner IDs, repository-bound ref and workflow, event, and protected environment."
  }
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

  validation {
    condition = !var.enabled || (
      !contains(var.ceremony_operations, "offline_bootstrap_importer") || alltrue([
        contains(keys(var.secret_versions), "bootstrap_root_envelope"),
        contains(keys(var.secret_versions), "bootstrap_targets_envelope"),
      ])
      ) && (
      !contains(var.ceremony_operations, "targets_renewal") || contains(keys(var.secret_versions), "targets_renewal_envelope")
      ) && (
      !contains(var.ceremony_operations, "targets_releases_rotation") || contains(keys(var.secret_versions), "targets_releases_rotation_envelope")
      ) && (
      !contains(var.ceremony_operations, "targets_security_rotation") || contains(keys(var.secret_versions), "targets_security_rotation_envelope")
      ) && (
      !contains(var.ceremony_operations, "root_importer") || contains(keys(var.secret_versions), "root_rotation_envelope")
    )
    error_message = "Enabled ceremony jobs require reviewed numeric versions for every offline envelope secret."
  }
}

variable "notification_channel_ids" {
  type    = list(string)
  default = []

  validation {
    condition     = var.environment != "prod" || !var.enabled || !var.monitoring_enabled || length(var.notification_channel_ids) > 0
    error_message = "An enabled production stack must route alerts to at least one reviewed notification channel."
  }
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
  description = "Explicitly allows signer-tagged workloads to reach the all-APIs private VIP for the exact Google OIDC JWKS hostname. Keep false unless separately reviewed."
  type        = bool
  default     = false

  validation {
    condition = var.signer_jwks_all_apis_enabled == (
      var.enabled && (
        var.workloads_enabled ||
        var.refresh_jobs_enabled ||
        length(setintersection(var.ceremony_operations, toset([
          "online_bootstrap",
          "targets_renewal",
          "targets_releases_rotation",
          "targets_security_rotation",
        ]))) > 0
      )
    )
    error_message = "The explicitly reviewed OIDC JWKS private-VIP route must be enabled exactly when this enabled stack selects signer services."
  }
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

variable "externally_managed_services" {
  description = "Required APIs permanently owned by a separately reviewed bootstrap root."
  type        = set(string)
  default     = []

  validation {
    condition = length(setsubtract(var.externally_managed_services, toset([
      "cloudresourcemanager.googleapis.com",
      "iam.googleapis.com",
      "iamcredentials.googleapis.com",
      "monitoring.googleapis.com",
      "orgpolicy.googleapis.com",
      "serviceusage.googleapis.com",
      "sts.googleapis.com",
      "storage.googleapis.com",
    ]))) == 0
    error_message = "Only the reviewed bootstrap-owned control-plane services may be excluded from this module."
  }
}
