variable "aws_account_id" {
  type = string
}

variable "region" {
  type    = string
  default = "eu-north-1"
}

variable "resources_enabled" {
  type    = bool
  default = false
}

variable "certificates_validated" {
  type    = bool
  default = false
}

variable "images" {
  type = object({
    ssr        = optional(string)
    playground = optional(string)
  })
  default = {}
}

variable "schedules_enabled" {
  type    = bool
  default = false
}

variable "app_environment" {
  type    = map(string)
  default = {}
}

variable "registry" {
  description = "Seen registry phases; each flag is a separately approved step."
  type = object({
    enabled                   = optional(bool, false)
    kms_keys_enabled          = optional(bool, false)
    certificates_validated    = optional(bool, false)
    image                     = optional(string)
    signer_image              = optional(string)
    online_public_keys_hex    = optional(map(string), {})
    trusted_root_v1_sha256    = optional(string)
    schedules_enabled         = optional(bool, false)
    refresh_schedules_enabled = optional(bool, false)
  })
  default = {}
}
