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

check "rules_fit_condition_limits" {
  assert {
    condition = alltrue(concat(
      [for rule in values(local.path_rules) : length(rule.hosts) + length(rule.paths) <= 5],
      [for hosts in local.host_rule_sets : length(hosts) <= 5],
    ))
    error_message = "ALB rules accept at most five condition values."
  }
}

check "priorities_stay_in_the_environment_range" {
  assert {
    condition     = alltrue([for rule in values(local.path_rules) : rule.priority >= local.priority_base && rule.priority < local.priority_base + 100])
    error_message = "Path rule priorities must stay inside this environment's range on the shared listener."
  }
}
