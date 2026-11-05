variable "cloudflare_account_id" {
  description = "Cloudflare account ID used only when resource creation is explicitly enabled."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.cloudflare_account_id == null ||
      can(regex("^[0-9a-f]{32}$", var.cloudflare_account_id))
    )
    error_message = "cloudflare_account_id must be a 32-character lowercase hexadecimal account ID."
  }
}

variable "environment" {
  type = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be dev or prod."
  }
}

variable "name_prefix" {
  type    = string
  default = "yousef"

  validation {
    condition = (
      length(var.name_prefix) >= 3 &&
      length(var.name_prefix) <= 24 &&
      can(regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$", var.name_prefix))
    )
    error_message = "name_prefix must be 3-24 lowercase letters, numbers, or hyphens."
  }
}

variable "resources_enabled" {
  description = "Fail-closed gate for R2 bucket and Queue creation."
  type        = bool
  default     = false
}

variable "access_enabled" {
  description = "Fail-closed gate for the owner-only development Access boundary."
  type        = bool
  default     = false
}

variable "access_auth_domain" {
  description = "Account-wide Zero Trust team domain, including cloudflareaccess.com."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.access_auth_domain == null ||
      can(regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]\\.cloudflareaccess\\.com$", var.access_auth_domain))
    )
    error_message = "access_auth_domain must be a valid cloudflareaccess.com team domain."
  }
}

variable "access_owner_email" {
  description = "The single owner identity allowed through development Access applications."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.access_owner_email == null ||
      can(regex("^[^@[:space:]]+@[^@[:space:]]+\\.[^@[:space:]]+$", var.access_owner_email))
    )
    error_message = "access_owner_email must be an email address."
  }
}

variable "access_identity_provider_id" {
  description = "Existing account-member-only Cloudflare identity provider allowed for interactive development access."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.access_identity_provider_id == null ||
      can(regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", var.access_identity_provider_id))
    )
    error_message = "access_identity_provider_id must be a lowercase UUID."
  }
}

variable "access_application_domain" {
  description = "Hostname of the dev Portfolio Worker protected by Access, without a scheme or path."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.access_application_domain == null ||
      can(regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$", var.access_application_domain))
    )
    error_message = "access_application_domain must be a hostname without a scheme or path."
  }
}

variable "access_dev_domains" {
  description = "Complete owner-only dev hostname set. Wildcards cover exactly one subdomain level."
  type        = map(string)
  default     = {}

  validation {
    condition = alltrue([
      for domain in values(var.access_dev_domains) : can(regex("^(?:\\*\\.)?[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$", domain))
    ])
    error_message = "Every access_dev_domains value must be a hostname or one-level wildcard hostname without a scheme or path."
  }
}

variable "finops_wif_access_enabled" {
  description = "Fail-closed gate for the dev-only FinOps WIF broker Access application and service token."
  type        = bool
  default     = false
}

variable "portfolio_r2_location" {
  description = "R2 creation hint closest to the Portfolio Middle East data plane."
  type        = string
  default     = "weur"

  validation {
    condition     = contains(["apac", "eeur", "enam", "oc", "weur", "wnam"], var.portfolio_r2_location)
    error_message = "portfolio_r2_location must be an R2-supported location hint."
  }
}

variable "seen_r2_location" {
  description = "R2 creation hint for Seen's North American write plane."
  type        = string
  default     = "enam"

  validation {
    condition     = contains(["apac", "eeur", "enam", "oc", "weur", "wnam"], var.seen_r2_location)
    error_message = "seen_r2_location must be an R2-supported location hint."
  }
}

variable "zones" {
  description = "Read-only DNS import contract. No zone or record resource consumes these values."
  type = map(object({
    zone_id              = string
    zone_name            = string
    dns_inventory_sha256 = string
    nameservers          = list(string)
  }))
  default = {}

  validation {
    condition = alltrue([
      for zone in values(var.zones) : can(regex("^[0-9a-f]{32}$", zone.zone_id))
    ])
    error_message = "Every zone_id must be a 32-character lowercase hexadecimal Cloudflare zone ID."
  }

  validation {
    condition = alltrue([
      for zone in values(var.zones) : contains(["yousef.codes", "felidai.com"], zone.zone_name)
    ])
    error_message = "Only yousef.codes and felidai.com belong in this migration contract."
  }

  validation {
    condition = alltrue([
      for zone in values(var.zones) : can(regex("^[0-9a-f]{64}$", zone.dns_inventory_sha256))
    ])
    error_message = "Every DNS inventory must have a lowercase SHA-256 digest."
  }

  validation {
    condition = alltrue([
      for zone in values(var.zones) : length(zone.nameservers) == 2
    ])
    error_message = "Every imported zone contract must record exactly two assigned Cloudflare nameservers."
  }
}
