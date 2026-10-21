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
      Environment = "shared"
      ManagedBy   = "opentofu"
    }
  }
}
