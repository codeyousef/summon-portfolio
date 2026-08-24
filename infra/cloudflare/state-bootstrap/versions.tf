terraform {
  required_version = ">= 1.12.0, < 1.13.0"

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "5.23.0"
    }
  }

  # This one-resource bootstrap cannot store its initial state in the bucket it
  # creates. Keep the ignored file encrypted and backed up offline.
  backend "local" {
    path = ".state/bootstrap.tfstate"
  }
}

provider "cloudflare" {}
