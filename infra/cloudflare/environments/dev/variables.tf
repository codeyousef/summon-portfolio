variable "cloudflare_account_id" {
  type     = string
  default  = null
  nullable = true
}

variable "name_prefix" {
  type    = string
  default = "yousef"
}

variable "resources_enabled" {
  type    = bool
  default = false
}

variable "access_enabled" {
  type    = bool
  default = false
}

variable "access_auth_domain" {
  type     = string
  default  = null
  nullable = true
}

variable "access_owner_email" {
  type     = string
  default  = null
  nullable = true
}

variable "access_identity_provider_id" {
  type     = string
  default  = null
  nullable = true
}

variable "access_application_domain" {
  type     = string
  default  = null
  nullable = true
}

variable "access_dev_domains" {
  type    = map(string)
  default = {}
}

variable "finops_wif_access_enabled" {
  type    = bool
  default = false
}

variable "portfolio_r2_location" {
  type    = string
  default = "weur"
}

variable "seen_r2_location" {
  type    = string
  default = "enam"
}

variable "zones" {
  type = map(object({
    zone_id              = string
    zone_name            = string
    dns_inventory_sha256 = string
    nameservers          = list(string)
  }))
  default = {}
}
