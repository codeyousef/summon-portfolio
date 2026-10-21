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
