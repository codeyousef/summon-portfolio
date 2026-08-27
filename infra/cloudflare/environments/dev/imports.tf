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

locals {
  live_dev_internal_ai_bypass_imports = {
    openrouter = {
      application_id = "9f2bd9ef-ccad-4fc6-9d48-7e6e68cdc6d0"
      policy_id      = "8d6b0bc4-9427-4976-b186-c412af7de47f"
    }
    catalog = {
      application_id = "b9a83cb5-8484-436a-87f4-39c9f7680f1d"
      policy_id      = "a80ea106-d676-4c64-9267-a6b6d6054509"
    }
    inference = {
      application_id = "411f0e6c-eb2a-4b6f-9494-ffb00ac21d5a"
      policy_id      = "66bed3e9-eac6-4d61-b372-8ec8fa80c0b9"
    }
  }
}

import {
  for_each = local.live_dev_internal_ai_bypass_imports
  to       = module.platform.cloudflare_zero_trust_access_policy.dev_internal_ai_bypass[each.key]
  id       = "${var.cloudflare_account_id}/${each.value.policy_id}"
}

import {
  for_each = local.live_dev_internal_ai_bypass_imports
  to       = module.platform.cloudflare_zero_trust_access_application.dev_internal_ai_bypass[each.key]
  id       = "accounts/${var.cloudflare_account_id}/${each.value.application_id}"
}
