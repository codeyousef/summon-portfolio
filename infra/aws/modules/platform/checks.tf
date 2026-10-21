check "dev_gate_is_complete" {
  assert {
    condition     = !(var.resources_enabled && local.is_dev) || var.dev_gate_owner_email != null
    error_message = "The dev environment requires dev_gate_owner_email for the owner-only Cognito gate."
  }
}

check "production_has_no_dev_gate_inputs" {
  assert {
    condition     = local.is_dev || var.dev_gate_owner_email == null
    error_message = "Production is public; dev_gate_owner_email must be unset."
  }
}

check "spot_is_dev_only" {
  assert {
    condition     = local.is_dev || !var.use_fargate_spot
    error_message = "Fargate Spot is allowed only in dev."
  }
}

check "services_require_the_platform" {
  assert {
    condition     = var.resources_enabled || (var.images.ssr == null && var.images.playground == null)
    error_message = "Images can be set only when resources_enabled is true."
  }
}

check "playground_requires_ssr" {
  assert {
    condition     = var.images.playground == null || var.images.ssr != null
    error_message = "The Seen playground is routed beside the SSR service and requires it."
  }
}

check "host_chunks_fit_rule_limits" {
  assert {
    condition     = alltrue([for chunk in local.host_chunks : length(chunk) <= 5])
    error_message = "ALB host conditions accept at most five values per rule."
  }
}
