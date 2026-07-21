variable "control_project_id" {
  description = "Existing management project used only as the Google provider quota project during bootstrap."
  type        = string
  default     = "portfolio-476219"

  validation {
    condition     = var.control_project_id == "portfolio-476219"
    error_message = "The reviewed production bootstrap control project is portfolio-476219."
  }
}

variable "enable_control_project_apis" {
  description = "Enables only the reviewed Organization Policy, Cloud Billing, and IAM APIs in the existing bootstrap quota project."
  type        = bool
  default     = false
}

variable "github_infrastructure_environments_reviewed" {
  description = "Manual attestation that seen-registry-production-plan and seen-registry-production-apply require explicit approval by the sole repository operator, permit that operator to approve their own deployment, disable administrator bypass, and allow only master. Must be true before project creation."
  type        = bool
  default     = false
}

variable "github_operations_environments_reviewed" {
  description = "Manual attestation that seen-registry-production-materials and seen-registry-production-jobs require explicit approval by the sole repository operator, permit that operator to approve their own deployment, disable administrator bypass, and allow only master. Kept separate so an older infrastructure-only attestation cannot activate operations federation."
  type        = bool
  default     = false
}

variable "enable_organization_guardrails" {
  description = "Enforces the organization-level no-default-network policy before any production project is created."
  type        = bool
  default     = false

  validation {
    condition     = !var.enable_organization_guardrails || var.enable_control_project_apis
    error_message = "Organization guardrails require the reviewed control-project APIs."
  }
}

variable "organization_guardrail_effective" {
  description = "Manual attestation set only after the separately applied organization no-default-network policy has propagated and its effective value was verified."
  type        = bool
  default     = false

  validation {
    condition     = !var.organization_guardrail_effective || var.enable_organization_guardrails
    error_message = "The organization guardrail can be attested effective only while the reviewed guardrail remains configured."
  }
}

variable "enable_organization_policy_admin_lease" {
  description = "Temporary human organization-level Policy Admin lease used only while changing organization guardrails."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_organization_policy_admin_lease || (
      var.enable_organization_guardrails &&
      !var.project_executor_handoff_complete
    )
    error_message = "The organization Policy Admin lease may exist only during the direct-human guardrail phase."
  }
}

variable "enable_production_project_creation" {
  description = "Explicit approval gate for managing only the isolated production project resource in its first saved-plan phase. Keep this true after creation so the project remains managed."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_production_project_creation || (
      var.enable_organization_guardrails &&
      var.organization_guardrail_effective &&
      var.github_infrastructure_environments_reviewed &&
      var.github_operations_environments_reviewed
    )
    error_message = "Production project creation requires the separately verified effective organization guardrail and all four reviewed protected GitHub environments."
  }
}

variable "production_project_creation_verified" {
  description = "Manual attestation set only after the separate project-only apply succeeded and the exact project ID, assigned project number, sole expected creator Owner, and absence of Editor bindings were verified."
  type        = bool
  default     = false

  validation {
    condition     = !var.production_project_creation_verified || var.enable_production_project_creation
    error_message = "Production project creation can be attested only while the isolated production project remains managed."
  }
}

variable "enable_production_project_bootstrap" {
  description = "Explicit approval gate for configuring the verified production project bootstrap foundation in a separate saved-plan phase."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_production_project_bootstrap || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_organization_guardrails &&
      var.organization_guardrail_effective &&
      var.github_infrastructure_environments_reviewed &&
      var.github_operations_environments_reviewed
    )
    error_message = "Production project bootstrap requires the separately applied and verified project creation phase, effective organization guardrail, and reviewed protected GitHub environments."
  }
}

variable "enable_project_policy_admin_lease" {
  description = "Temporary human Policy Admin lease used only while changing production-project policies. The organization-level grant is constrained to the permanently tagged production project and expires within four hours."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_project_policy_admin_lease || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap
    )
    error_message = "The tag-scoped human Policy Admin lease for project policies is limited to the verified direct-human bootstrap phase."
  }
}

variable "policy_admin_lease_expiry" {
  description = "Concrete RFC3339 expiry for the one active Policy Admin lease. It must be in the future, no more than four hours after plan creation, and null when neither policy-administration purpose is enabled."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      (!var.enable_organization_policy_admin_lease && !var.enable_project_policy_admin_lease && var.policy_admin_lease_expiry == null) ||
      (
        var.enable_organization_policy_admin_lease != var.enable_project_policy_admin_lease &&
        var.policy_admin_lease_expiry != null &&
        can(formatdate("YYYY-MM-DD'T'hh:mm:ss'Z'", var.policy_admin_lease_expiry)) &&
        try(timecmp(var.policy_admin_lease_expiry, plantimestamp()) > 0, false) &&
        try(timecmp(var.policy_admin_lease_expiry, timeadd(plantimestamp(), "4h")) <= 0, false)
      )
    )
    error_message = "Exactly one Policy Admin purpose requires one concrete future RFC3339 lease expiry no more than four hours after plan creation; otherwise policy_admin_lease_expiry must be null."
  }
}

variable "enable_temporary_human_state_migration_access" {
  description = "Temporary time-bounded state read across both dedicated state buckets, plus write/delete access to each root's exact state and lock objects, for the reviewed human operator through migration, foundation, and Owner-removal phases. The first WIF handoff apply removes it."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_temporary_human_state_migration_access || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap
    )
    error_message = "Temporary human state migration access is allowed only before the CI identity handoff."
  }
}

variable "temporary_human_state_migration_access_expiry" {
  description = "Concrete RFC3339 expiry for temporary human state migration access. It must be in the future, no more than 24 hours after plan creation, and null while access is disabled."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      (!var.enable_temporary_human_state_migration_access && var.temporary_human_state_migration_access_expiry == null) ||
      (
        var.enable_temporary_human_state_migration_access &&
        var.temporary_human_state_migration_access_expiry != null &&
        can(formatdate("YYYY-MM-DD'T'hh:mm:ss'Z'", var.temporary_human_state_migration_access_expiry)) &&
        try(timecmp(var.temporary_human_state_migration_access_expiry, plantimestamp()) > 0, false) &&
        try(timecmp(var.temporary_human_state_migration_access_expiry, timeadd(plantimestamp(), "24h")) <= 0, false)
      )
    )
    error_message = "Temporary human state migration access requires one concrete future RFC3339 expiry no more than 24 hours after plan creation; otherwise its expiry must be null."
  }
}

variable "enable_temporary_human_state_bucket_policy_access" {
  description = "Temporary exact IAM-policy recovery access for the reviewed human on only the two dedicated state buckets, split between a bucket-metadata-and-policy-only reader and a set-only role constrained to the three state roles. It is retained through live verification and complete reconciliation, then its four project-level bindings are removed before Owner removal."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_temporary_human_state_bucket_policy_access || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap
    )
    error_message = "Temporary state-bucket policy recovery access requires the verified production bootstrap and must remain a direct-human pre-handoff lease."
  }
}

variable "temporary_human_state_bucket_policy_access_expiry" {
  description = "Concrete RFC3339 expiry for temporary human state-bucket policy recovery access. It must be in the future, no more than four hours after plan creation, and null while access is disabled."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      (!var.enable_temporary_human_state_bucket_policy_access && var.temporary_human_state_bucket_policy_access_expiry == null) ||
      (
        var.enable_temporary_human_state_bucket_policy_access &&
        var.temporary_human_state_bucket_policy_access_expiry != null &&
        can(formatdate("YYYY-MM-DD'T'hh:mm:ss'Z'", var.temporary_human_state_bucket_policy_access_expiry)) &&
        try(timecmp(var.temporary_human_state_bucket_policy_access_expiry, plantimestamp()) > 0, false) &&
        try(timecmp(var.temporary_human_state_bucket_policy_access_expiry, timeadd(plantimestamp(), "4h")) <= 0, false)
      )
    )
    error_message = "Temporary human state-bucket policy recovery access requires one concrete future RFC3339 expiry no more than four hours after plan creation; otherwise its expiry must be null."
  }
}

variable "enable_state_bucket_iam_reconciliation" {
  description = "One-way gate for authoritative IAM policies on both state buckets. Keep false for a clean prerequisite bootstrap, then enable only after exact temporary bucket-policy access is installed and verified. Existing partially applied environments whose policies already landed must keep it true."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_state_bucket_iam_reconciliation || (
      var.enable_production_project_bootstrap
    )
    error_message = "Authoritative state-bucket IAM reconciliation requires the verified production bootstrap; the central phase contract requires verified recovery access before reconciliation and preserves the gate through handoff."
  }
}

variable "temporary_human_state_bucket_policy_access_verified" {
  description = "Manual attestation set only after the exact recovery bindings propagated and bucket IAM-policy read/write permissions were verified live on both dedicated state buckets. It remains true after the temporary access is removed."
  type        = bool
  default     = false

  validation {
    condition = !var.temporary_human_state_bucket_policy_access_verified || (
      var.enable_production_project_bootstrap
    )
    error_message = "State-bucket policy access may be attested only for the verified production bootstrap."
  }
}

variable "approve_temporary_human_state_bucket_policy_access_removal" {
  description = "Explicit direct-human gate for removing all four temporary state-bucket policy recovery bindings in a complete plan after the production foundation is applied and before Owner removal."
  type        = bool
  default     = false

  validation {
    condition = !var.approve_temporary_human_state_bucket_policy_access_removal || (
      var.enable_production_project_bootstrap &&
      var.temporary_human_state_bucket_policy_access_verified &&
      !var.enable_temporary_human_state_bucket_policy_access &&
      var.temporary_human_state_bucket_policy_access_expiry == null &&
      var.project_creator_owner_member == "user:yousef@felidai.com"
    )
    error_message = "Recovery-binding removal requires verified disabled recovery access and the exact reviewed creator member; the central phase contract enforces foundation, adoption, and handoff ordering."
  }
}

variable "temporary_human_state_bucket_policy_access_removed" {
  description = "Manual attestation set only after all four temporary recovery bindings were removed in the separately approved human cleanup plan and verified absent."
  type        = bool
  default     = false

  validation {
    condition = !var.temporary_human_state_bucket_policy_access_removed || (
      var.enable_production_project_bootstrap &&
      var.temporary_human_state_bucket_policy_access_verified &&
      !var.enable_temporary_human_state_bucket_policy_access &&
      var.temporary_human_state_bucket_policy_access_expiry == null
    )
    error_message = "Recovery access can be attested removed only after verified access is disabled and its expiry is cleared; the central phase contract enforces foundation and cleanup ordering."
  }
}

variable "project_executor_handoff_complete" {
  description = "Manual attestation that the protected GitHub OIDC identities now supply ADC for project operations; enables identity-safe refresh without evaluating human credentials."
  type        = bool
  default     = false

  validation {
    condition = !var.project_executor_handoff_complete || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap &&
      var.temporary_human_state_bucket_policy_access_verified &&
      var.enable_state_bucket_iam_reconciliation &&
      var.temporary_human_state_bucket_policy_access_removed &&
      !var.enable_temporary_human_state_bucket_policy_access &&
      !var.enable_temporary_human_state_migration_access &&
      var.temporary_human_state_migration_access_expiry == null &&
      !var.approve_temporary_human_state_bucket_policy_access_removal &&
      var.github_infrastructure_environments_reviewed &&
      var.github_operations_environments_reviewed
    )
    error_message = "CI handoff requires the production bootstrap foundation and separately reviewed infrastructure and operations environments."
  }
}

variable "production_foundation_applied" {
  description = "Manual attestation set only after the direct-human production root created its APIs, repository, keys, buckets, secrets, service accounts, exact service-account IAM grants, and custom runtime roles under verified effective organization policies."
  type        = bool
  default     = false

  validation {
    condition = !var.production_foundation_applied || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap &&
      !var.enable_organization_policy_admin_lease &&
      !var.enable_project_policy_admin_lease &&
      var.temporary_human_state_bucket_policy_access_verified &&
      var.enable_state_bucket_iam_reconciliation &&
      var.managed_member_policy_effective &&
      var.enable_portfolio_gateway_exception &&
      var.portfolio_gateway_exception_effective &&
      var.automatic_default_service_account_grants_policy_effective
    )
    error_message = "The production foundation attestation requires verified state-bucket policy access, removal of both Policy Admin purposes, and effective managed-member, gateway-exception, and default-service-account policies."
  }
}

variable "production_image_publisher_foundation_applied" {
  description = "Manual attestation that the optional production image-publisher service account, exact GitHub WIF trust, and Artifact Registry writer binding were created in the direct-human foundation phase. If omitted, adding them later requires a separately reviewed privileged path."
  type        = bool
  default     = false

  validation {
    condition     = !var.production_image_publisher_foundation_applied || var.production_foundation_applied
    error_message = "Image-publisher foundation attestation requires the production foundation."
  }
}

variable "project_creator_owner_member" {
  description = "Exact automatically granted project creator Owner member, supplied only during the human adoption and removal phases."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.project_creator_owner_member == null ||
      var.project_creator_owner_member == "user:yousef@felidai.com"
    )
    error_message = "project_creator_owner_member must be the reviewed active project creator user:yousef@felidai.com."
  }

}

variable "adopt_project_creator_owner" {
  description = "Imports the creator's automatic Owner member into state without granting it, ready for a separate reviewed removal plan."
  type        = bool
  default     = false

  validation {
    condition = !var.adopt_project_creator_owner || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap &&
      !var.project_executor_handoff_complete &&
      var.project_creator_owner_member == "user:yousef@felidai.com"
    )
    error_message = "Owner adoption requires the project, exact creator, direct human credentials, and no removal phase."
  }
}

variable "approve_project_creator_owner_removal" {
  description = "Explicit direct-human gate that removes the previously imported creator Owner member in its own reviewed saved plan before CI handoff."
  type        = bool
  default     = false

  validation {
    condition = !var.approve_project_creator_owner_removal || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap &&
      !var.project_executor_handoff_complete &&
      !var.enable_organization_policy_admin_lease &&
      !var.enable_project_policy_admin_lease &&
      var.temporary_human_state_bucket_policy_access_verified &&
      var.temporary_human_state_bucket_policy_access_removed &&
      var.project_creator_owner_member == "user:yousef@felidai.com"
    )
    error_message = "Owner removal must be a separate direct-human phase for the exact previously imported creator after both Policy Admin lease gates are disabled."
  }
}

variable "project_creator_owner_removed" {
  description = "Manual attestation set only after the separately approved Owner-removal apply succeeded and the effective project IAM policy was checked."
  type        = bool
  default     = false

  validation {
    condition = !var.project_creator_owner_removed || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap &&
      !var.enable_organization_policy_admin_lease &&
      !var.enable_project_policy_admin_lease &&
      var.temporary_human_state_bucket_policy_access_verified &&
      var.temporary_human_state_bucket_policy_access_removed &&
      !var.adopt_project_creator_owner &&
      !var.approve_project_creator_owner_removal &&
      var.project_creator_owner_member == null
    )
    error_message = "Owner removal can be attested only after the adoption/removal resources and member input are absent."
  }
}

variable "enable_portfolio_gateway_exception" {
  description = "Manages the required legacy domain-restriction override. It is first created in a separate pre-handoff human Policy Admin lease after the managed policy is effective, then retained read-only after handoff."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_portfolio_gateway_exception || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap &&
      var.managed_member_policy_effective
    )
    error_message = "The gateway exception requires the production project and a recorded effective managed-member policy check."
  }
}

variable "portfolio_gateway_exception_effective" {
  description = "Manual attestation set only after the pre-handoff gateway-exception apply propagated and the effective legacy policy was checked."
  type        = bool
  default     = false

  validation {
    condition = !var.portfolio_gateway_exception_effective || (
      var.enable_portfolio_gateway_exception &&
      var.managed_member_policy_effective
    )
    error_message = "The gateway exception can be attested effective only while the exception and effective managed member policy remain configured."
  }
}

variable "managed_member_policy_effective" {
  description = "Manual attestation set only after the effective managed-member policy matches the reviewed allowlist following propagation."
  type        = bool
  default     = false

  validation {
    condition = !var.managed_member_policy_effective || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap
    )
    error_message = "The managed-member policy can be attested effective only after the verified project creation and bootstrap phases."
  }
}

variable "automatic_default_service_account_grants_policy_effective" {
  description = "Manual attestation set only after iam.automaticIamGrantsForDefaultServiceAccounts is effectively enforced on the production project, before Compute is enabled by the production root."
  type        = bool
  default     = false

  validation {
    condition = !var.automatic_default_service_account_grants_policy_effective || (
      var.enable_production_project_creation &&
      var.production_project_creation_verified &&
      var.enable_production_project_bootstrap
    )
    error_message = "The default-service-account policy can be attested effective only for the bootstrap-managed production project."
  }
}

variable "project_id" {
  description = "Globally unique project ID for the isolated production registry."
  type        = string

  validation {
    condition     = var.project_id == "seen-registry-prod-476219"
    error_message = "project_id must be the reviewed dedicated production project seen-registry-prod-476219."
  }
}

variable "organization_id" {
  description = "Numeric organization that owns the production registry project."
  type        = string

  validation {
    condition     = var.organization_id == "567958019562"
    error_message = "organization_id must be the reviewed Felidai organization 567958019562."
  }
}

variable "billing_account_id" {
  description = "Billing account linked to the isolated production registry project."
  type        = string

  validation {
    condition     = can(regex("^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$", var.billing_account_id))
    error_message = "billing_account_id must use the canonical XXXXXX-XXXXXX-XXXXXX format."
  }
}

variable "bootstrap_operator_members" {
  description = "Exact reviewed human operators eligible only for temporary policy-administration or state-migration leases."
  type        = set(string)

  validation {
    condition     = var.bootstrap_operator_members == toset(["user:yousef@felidai.com"])
    error_message = "bootstrap_operator_members must contain only the reviewed human user:yousef@felidai.com."
  }
}

variable "notification_email" {
  description = "Reviewed operations email address for production registry monitoring alerts."
  type        = string

  validation {
    condition     = can(regex("^[^[:space:]@]+@[^[:space:]@]+\\.[^[:space:]@]+$", var.notification_email))
    error_message = "notification_email must be a syntactically valid email address."
  }
}
