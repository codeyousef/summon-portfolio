terraform {
  required_version = ">= 1.12.0, < 2.0.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "7.27.0"
    }
  }

  backend "local" {}
}

provider "google" {
  project               = var.control_project_id
  billing_project       = var.control_project_id
  user_project_override = true
}

provider "google" {
  alias = "project"

  project = var.project_id
  billing_project = (
    var.project_executor_handoff_complete ? var.project_id : var.control_project_id
  )
  user_project_override = true
}
