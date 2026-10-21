terraform {
  required_version = ">= 1.12.0, < 1.13.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.68.0"
    }
  }

  # The bootstrap cannot store its initial state in the bucket it creates.
  # Keep the ignored file encrypted and backed up offline.
  backend "local" {
    path = ".state/bootstrap.tfstate"
  }
}

provider "aws" {
  region = var.region

  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      Project   = "portfolio"
      ManagedBy = "opentofu"
      Stack     = "state-bootstrap"
    }
  }
}
