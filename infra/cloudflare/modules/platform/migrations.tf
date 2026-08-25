moved {
  from = cloudflare_zero_trust_access_policy.finops_owner[0]
  to   = cloudflare_zero_trust_access_policy.dev_owner[0]
}

# Reuse the existing dashboard application as the hostname-wide direct Worker
# perimeter. The former API-path application is intentionally retired after
# the whole host is protected, avoiding multiple CF_Authorization audiences on
# one hostname.
moved {
  from = cloudflare_zero_trust_access_application.finops["dashboard"]
  to   = cloudflare_zero_trust_access_application.dev_sites["portfolio_worker"]
}
