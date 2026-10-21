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

variable "dns_records" {
  type = map(object({
    name    = string
    type    = string
    ttl     = optional(number, 300)
    records = list(string)
  }))
  default = {}
}

variable "github_repository" {
  type    = string
  default = "codeyousef/summon-portfolio"
}
