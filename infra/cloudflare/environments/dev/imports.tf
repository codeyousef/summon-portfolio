locals {
  live_dev_access_application_imports = {
    portfolio_apex      = "75128ff0-ddb9-4cb4-9951-70303388754a"
    portfolio_ecosystem = "6206dbdd-7a9b-45d2-900e-ac3a1e1860a1"
    felidai_apex        = "142955c5-3354-4afa-8623-93ac9410145f"
    felidai_ecosystem   = "e41fae66-a63f-4a6e-83e6-5b3949cb9c9e"
    samurai             = "90d411eb-1fc3-40a0-80c5-fdf72f7444b9"
    samurai_worker      = "61379ceb-ce37-4e1e-895e-06502e3cfd8f"
  }
}

# These resources were provisioned through the authenticated Cloudflare API
# during the dev cutover. Declarative imports make the next reviewed OpenTofu
# plan adopt them instead of attempting to create duplicate Access objects.
import {
  for_each = local.live_dev_access_application_imports
  to       = module.platform.cloudflare_zero_trust_access_application.dev_sites[each.key]
  id       = "accounts/${var.cloudflare_account_id}/${each.value}"
}

import {
  to = module.platform.cloudflare_zero_trust_access_service_token.dev_machine["portfolio_samurai_identity"]
  id = "accounts/${var.cloudflare_account_id}/2d1e83e9-0f2d-4425-b49c-0c71ffe5bd01"
}

import {
  to = module.platform.cloudflare_zero_trust_access_policy.dev_machine["portfolio_samurai_identity"]
  id = "${var.cloudflare_account_id}/0544dedb-8718-4a56-bf81-0a4fd8e3e088"
}

import {
  to = module.platform.cloudflare_zero_trust_access_application.dev_machine["portfolio_samurai_identity"]
  id = "accounts/${var.cloudflare_account_id}/5383e1ea-9d4f-44c3-bb11-f31ee447bfda"
}
