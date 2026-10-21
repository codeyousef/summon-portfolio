terraform {
  required_version = ">= 1.12.0, < 1.13.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.68.0"
    }
  }

  backend "s3" {}
}

provider "aws" {
  region = var.region

  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      Project     = "portfolio"
      Environment = "edge"
      ManagedBy   = "opentofu"
    }
  }
}

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

variable "isolated_endpoints_enabled" {
  type    = bool
  default = false
}

# The shared network and public ALB serve both environments, so the operator
# applies this root; environment CI identities only add their own rules.
module "edge" {
  source = "../../modules/edge"

  aws_account_id             = var.aws_account_id
  region                     = var.region
  resources_enabled          = var.resources_enabled
  certificates_validated     = var.certificates_validated
  isolated_endpoints_enabled = var.isolated_endpoints_enabled
}

output "names" {
  value = module.edge.names
}

output "listener_ready" {
  value = module.edge.listener_ready
}

output "isolated_endpoints_enabled" {
  value = module.edge.isolated_endpoints_enabled
}

output "certificate_validation_records" {
  value = module.edge.certificate_validation_records
}
