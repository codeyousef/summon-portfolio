terraform {
  required_version = ">= 1.12.0, < 1.13.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "7.27.0"
    }
  }

  backend "s3" {}
}

provider "google" {
  project = var.project_id
}
