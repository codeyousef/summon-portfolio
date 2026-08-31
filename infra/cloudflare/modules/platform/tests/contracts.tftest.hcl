mock_provider "cloudflare" {}

run "development_is_inert_by_default" {
  command = plan

  variables {
    environment = "dev"
  }

  assert {
    condition     = output.resources_enabled == false
    error_message = "Development must not create Cloudflare resources by default."
  }

  assert {
    condition     = output.authoritative_database == "firestore"
    error_message = "The Cloudflare foundation must not replace Firestore."
  }

  assert {
    condition     = length(output.r2_buckets) == 10 && length(output.queues) == 7
    error_message = "The contract must retain ten data buckets, three Queue/DLQ pairs, and the Portfolio recovery parking queue."
  }

  assert {
    condition = (
      length(output.r2_buckets.seen_public.lock_rules) == 1 &&
      length(output.r2_buckets.seen_evidence.lock_rules) == 1 &&
      length(output.r2_buckets.seen_backup.lock_rules) == 1
    )
    error_message = "Published Seen packages, evidence, and backups must retain reviewed Bucket Lock rules."
  }
}

run "reviewed_production_shape" {
  command = plan

  variables {
    cloudflare_account_id = "0123456789abcdef0123456789abcdef"
    environment           = "prod"
    resources_enabled     = true
    zones = {
      yousef_codes = {
        zone_id              = "11111111111111111111111111111111"
        zone_name            = "yousef.codes"
        dns_inventory_sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        nameservers          = ["one.ns.cloudflare.com", "two.ns.cloudflare.com"]
      }
      felidai_com = {
        zone_id              = "22222222222222222222222222222222"
        zone_name            = "felidai.com"
        dns_inventory_sha256 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        nameservers          = ["three.ns.cloudflare.com", "four.ns.cloudflare.com"]
      }
    }
  }

  assert {
    condition = (
      output.worker_release_contracts.portfolio_edge.container_regions == ["ME", "WEUR"] &&
      output.worker_release_contracts.seen_edge.container_regions == ["ENAM", "WNAM"]
    )
    error_message = "Portfolio must remain Middle East-first and Seen must retain North American coordination."
  }

  assert {
    condition     = length(output.worker_release_contracts.seen_edge.route_less_workers) == 4
    error_message = "Seen must retain four isolated route-less online signer Workers."
  }

  assert {
    condition     = length(output.zone_import_contracts) == 2
    error_message = "The reviewed DNS inventory contract must cover both zones without managing them."
  }

  assert {
    condition = (
      output.dev_remote_access.enabled == false &&
      length(cloudflare_zero_trust_access_application.dev_remote_client_bypass) == 0 &&
      !contains(keys(cloudflare_zero_trust_access_application.dev_machine), "samurai_remote_inference")
    )
    error_message = "Production must not provision any honeymoon remote Access exception or service credential."
  }
}

run "reviewed_dev_finops_wif_broker_shape" {
  command = plan

  variables {
    cloudflare_account_id       = "0123456789abcdef0123456789abcdef"
    environment                 = "dev"
    resources_enabled           = true
    access_enabled              = true
    access_auth_domain          = "felidai-studio.cloudflareaccess.com"
    access_owner_email          = "owner@example.com"
    access_identity_provider_id = "11111111-2222-4333-8444-555555555555"
    access_application_domain   = "portfolio-dev.example.com"
    access_dev_domains = {
      portfolio_apex   = "portfolio-dev.example.com"
      portfolio_worker = "portfolio-worker.example.workers.dev"
      ecosystem        = "*.dev.example.com"
      samurai          = "samurai.dev.example.net"
      samurai_worker   = "samurai-worker.example.workers.dev"
    }
    finops_wif_access_enabled = true
  }

  assert {
    condition = (
      cloudflare_zero_trust_access_service_token.finops_wif_broker[0].duration == "2160h" &&
      cloudflare_zero_trust_access_policy.finops_wif_broker[0].decision == "non_identity" &&
      cloudflare_zero_trust_access_policy.finops_wif_broker[0].session_duration == "1h" &&
      cloudflare_zero_trust_access_application.finops_wif_broker[0].domain == "portfolio-dev.example.com/internal/gcp-wif/assertion" &&
      cloudflare_zero_trust_access_application.finops_wif_broker[0].session_duration == "1h"
    )
    error_message = "The dev WIF broker must use one 90-day service token and an exact one-hour Service Auth route."
  }

  assert {
    condition = alltrue([
      for application in cloudflare_zero_trust_access_application.dev_sites :
      !application.path_cookie_attribute &&
      application.same_site_cookie_attribute == "lax" &&
      application.auto_redirect_to_identity &&
      length(application.allowed_idps) == 1 &&
      contains(application.allowed_idps, "11111111-2222-4333-8444-555555555555")
    ])
    error_message = "Every development hostname must use one hostname-wide owner session and the existing account-member-only identity provider."
  }


  assert {
    condition = (
      length(cloudflare_zero_trust_access_application.dev_machine) == 2 &&
      cloudflare_zero_trust_access_application.dev_machine["portfolio_samurai_identity"].domain == "samurai.dev.example.net/internal/portfolio/finops/identities" &&
      cloudflare_zero_trust_access_application.dev_machine["samurai_remote_inference"].domain == "samurai.dev.example.net/internal/remote/inference/*" &&
      alltrue([for policy in cloudflare_zero_trust_access_policy.dev_machine : policy.decision == "non_identity"])
    )
    error_message = "Machine identity access must remain limited to the exact internal identity-projection and remote-inference routes."
  }

  assert {
    condition = (
      cloudflare_zero_trust_access_application.dev_sites["samurai"].domain == "samurai.dev.example.net" &&
      toset(keys(cloudflare_zero_trust_access_application.dev_remote_client_bypass)) == toset(["api", "websocket", "android_app_links"]) &&
      toset([for application in cloudflare_zero_trust_access_application.dev_remote_client_bypass : application.domain]) == toset([
        "samurai.dev.example.net/api/remote/*",
        "samurai.dev.example.net/ws/remote/*",
        "samurai.dev.example.net/.well-known/assetlinks.json",
      ]) &&
      alltrue([
        for key, application in cloudflare_zero_trust_access_application.dev_remote_client_bypass :
        !application.auto_redirect_to_identity &&
        !application.path_cookie_attribute &&
        application.policies[0].id == cloudflare_zero_trust_access_policy.dev_remote_client_bypass[key].id
      ]) &&
      alltrue([
        for policy in cloudflare_zero_trust_access_policy.dev_remote_client_bypass :
        policy.decision == "bypass" && length(policy.include) == 1
      ])
    )
    error_message = "Remote clients and Android App Link verification must bypass only their exact dev paths rather than the hostname-wide GitHub boundary."
  }

  assert {
    condition = (
      contains(keys(cloudflare_zero_trust_access_service_token.dev_machine), "samurai_remote_inference") &&
      cloudflare_zero_trust_access_service_token.dev_machine["samurai_remote_inference"].duration == "2160h" &&
      cloudflare_zero_trust_access_application.dev_machine["samurai_remote_inference"].domain == "samurai.dev.example.net/internal/remote/inference/*" &&
      cloudflare_zero_trust_access_application.dev_machine["samurai_remote_inference"].session_duration == "1h" &&
      cloudflare_zero_trust_access_policy.dev_machine["samurai_remote_inference"].decision == "non_identity" &&
      cloudflare_zero_trust_access_policy.dev_machine["samurai_remote_inference"].session_duration == "1h" &&
      length(cloudflare_zero_trust_access_policy.dev_machine["samurai_remote_inference"].include) == 1
    )
    error_message = "Bypassing interactive Access must never weaken Samurai application authentication, and inference must retain both Service Auth and application auth."
  }
}
