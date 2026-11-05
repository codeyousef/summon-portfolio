resource "aws_lb_target_group" "ssr" {
  count = var.resources_enabled && var.load_balancer_enabled ? 1 : 0

  name                 = "${local.name}-ssr"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = data.aws_vpc.edge[0].id
  deregistration_delay = 30

  # Fifth Wall game state lives in process memory, so a viewer must keep
  # reaching the same task.
  stickiness {
    type            = "lb_cookie"
    enabled         = true
    cookie_duration = 86400
  }

  health_check {
    path                = "/health/ready"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_target_group" "playground" {
  count = local.playground_enabled ? 1 : 0

  name                 = "${local.name}-playground"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = data.aws_vpc.edge[0].id
  deregistration_delay = 15

  health_check {
    path                = "/health/ready"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# The shared listener selects this environment's certificate by SNI.
resource "aws_lb_listener_certificate" "origin" {
  count = local.lb

  listener_arn    = data.aws_lb_listener.edge[0].arn
  certificate_arn = aws_acm_certificate_validation.origin[0].certificate_arn
}

locals {
  # Dev and prod share one listener, so every rule is scoped to this
  # environment's hosts and uses its own priority range. Dev's wildcard sorts
  # ahead of every prod rule.
  priority_base = local.is_dev ? 100 : 1000

  host_condition_sets = local.is_dev ? [[local.hosts.main, "*.${local.host_suffix}"]] : chunklist(local.host_names, 3)

  # Mirrors cloudflare/portfolio-edge/src/routing.ts. Each route has at most
  # two path values so a host chunk plus paths fits the five-value limit.
  path_routes = concat(
    [
      # Worker-only internal API surface; never reachable from the public edge.
      { key = "internal", paths = ["/internal/*"], action = "not-found", target = null, auth = false, host = "", url_regex = "" },
      # GitHub signs docs-invalidation webhooks; the application verifies them.
      { key = "github-hook", paths = ["/__hooks/github"], action = "forward", target = "ssr", auth = false, host = "", url_regex = "" },
    ],
    local.playground_enabled ? [
      { key = "seen", paths = ["/seen", "/seen/*"], action = "forward", target = "playground", auth = true, host = "seen.${local.host_suffix}", url_regex = "^/seen/?(.*)$" },
      { key = "playground", paths = ["/playground", "/playground/*"], action = "forward", target = "playground", auth = true, host = "seen.${local.host_suffix}", url_regex = "" },
      { key = "seen-run", paths = ["/api/seen/run", "/api/seen/run/*"], action = "forward", target = "playground", auth = true, host = "seen.${local.host_suffix}", url_regex = "" },
    ] : [],
    [
      for project in local.ecosystem_projects : {
        key       = project
        paths     = ["/${project}", "/${project}/*"]
        action    = "forward"
        target    = "ssr"
        auth      = true
        host      = "${project}.${local.host_suffix}"
        url_regex = "^/${project}/?(.*)$"
      }
    ],
  )

  route_order = ["internal", "github-hook", "seen", "playground", "seen-run", "summon", "materia", "sigil", "aether"]

  path_rules = {
    for pair in setproduct(local.path_routes, range(length(local.host_condition_sets))) :
    "${pair[0].key}-${pair[1]}" => merge(pair[0], {
      hosts    = local.host_condition_sets[pair[1]]
      priority = local.priority_base + index(local.route_order, pair[0].key) * 10 + pair[1]
    })
  }

  host_rule_sets = local.is_dev ? local.host_condition_sets : chunklist(local.host_names, 5)

  target_group_arns = merge(
    var.resources_enabled && var.load_balancer_enabled ? { ssr = aws_lb_target_group.ssr[0].arn } : {},
    local.playground_enabled ? { playground = aws_lb_target_group.playground[0].arn } : {},
  )

  dev_gate = local.is_dev && var.resources_enabled ? {
    user_pool_arn       = aws_cognito_user_pool.dev_gate[0].arn
    user_pool_client_id = aws_cognito_user_pool_client.dev_gate[0].id
    user_pool_domain    = aws_cognito_user_pool_domain.dev_gate[0].domain
  } : null
}

resource "aws_lb_listener_rule" "paths" {
  for_each = local.lb_enabled ? local.path_rules : {}

  listener_arn = data.aws_lb_listener.edge[0].arn
  priority     = each.value.priority

  condition {
    host_header {
      values = each.value.hosts
    }
  }

  condition {
    path_pattern {
      values = each.value.paths
    }
  }

  dynamic "transform" {
    for_each = each.value.host == "" ? [] : [each.value.host]

    content {
      type = "host-header-rewrite"

      host_header_rewrite_config {
        rewrite {
          regex   = "^.*$"
          replace = transform.value
        }
      }
    }
  }

  dynamic "transform" {
    for_each = each.value.url_regex == "" ? [] : [each.value.url_regex]

    content {
      type = "url-rewrite"

      url_rewrite_config {
        rewrite {
          regex   = transform.value
          replace = "/$1"
        }
      }
    }
  }

  dynamic "action" {
    for_each = each.value.auth && local.dev_gate != null ? [local.dev_gate] : []

    content {
      type = "authenticate-cognito"

      authenticate_cognito {
        user_pool_arn              = action.value.user_pool_arn
        user_pool_client_id        = action.value.user_pool_client_id
        user_pool_domain           = action.value.user_pool_domain
        on_unauthenticated_request = "authenticate"
        session_timeout            = 43200
      }
    }
  }

  dynamic "action" {
    for_each = each.value.action == "not-found" ? [1] : []

    content {
      type = "fixed-response"

      fixed_response {
        content_type = "text/plain"
        message_body = "Not found"
        status_code  = "404"
      }
    }
  }

  dynamic "action" {
    for_each = each.value.action == "forward" ? [each.value.target] : []

    content {
      type             = "forward"
      target_group_arn = local.target_group_arns[action.value]
    }
  }
}

resource "aws_lb_listener_rule" "hosts" {
  count = local.lb_enabled ? length(local.host_rule_sets) : 0

  listener_arn = data.aws_lb_listener.edge[0].arn
  priority     = local.priority_base + 100 + count.index

  condition {
    host_header {
      values = local.host_rule_sets[count.index]
    }
  }

  dynamic "action" {
    for_each = local.dev_gate == null ? [] : [local.dev_gate]

    content {
      type = "authenticate-cognito"

      authenticate_cognito {
        user_pool_arn              = action.value.user_pool_arn
        user_pool_client_id        = action.value.user_pool_client_id
        user_pool_domain           = action.value.user_pool_domain
        on_unauthenticated_request = "authenticate"
        session_timeout            = 43200
      }
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ssr[0].arn
  }
}
