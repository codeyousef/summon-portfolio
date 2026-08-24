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
