resource "aws_lb" "main" {
  count = local.enabled

  name                       = local.name
  load_balancer_type         = "application"
  internal                   = false
  security_groups            = [aws_security_group.alb[0].id]
  subnets                    = [for subnet in aws_subnet.public : subnet.id]
  drop_invalid_header_fields = true
  enable_deletion_protection = !local.is_dev
  idle_timeout               = 120
}

resource "aws_lb_target_group" "ssr" {
  count = local.enabled

  name                 = "${local.name}-ssr"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = aws_vpc.main[0].id
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
  vpc_id               = aws_vpc.main[0].id
  deregistration_delay = 15

  health_check {
    path                = "/health/ready"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# Unknown hosts are rejected; every served host has an explicit rule.
resource "aws_lb_listener" "https" {
  count = local.enabled

  load_balancer_arn = aws_lb.main[0].arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.origin[0].certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

locals {
  # Mirrors cloudflare/portfolio-edge/src/routing.ts. Path routes apply to
  # every host, exactly as the Worker classified them before host dispatch.
  rewrite_routes = merge(
    {
      seen = {
        priority  = 20
        paths     = ["/seen", "/seen/*"]
        target    = "playground"
        host      = "seen.${local.host_suffix}"
        url_regex = "^/seen/?(.*)$"
      }
      seen_tools = {
        priority  = 21
        paths     = ["/playground", "/playground/*", "/api/seen/run", "/api/seen/run/*"]
        target    = "playground"
        host      = "seen.${local.host_suffix}"
        url_regex = ""
      }
    },
    {
      for index, project in local.ecosystem_projects : project => {
        priority  = 30 + index
        paths     = ["/${project}", "/${project}/*"]
        target    = "ssr"
        host      = "${project}.${local.host_suffix}"
        url_regex = "^/${project}/?(.*)$"
      }
    },
  )

  # The playground rules exist only while the playground runs; until then
  # those paths fall through to SSR like any other dynamic route.
  active_rewrite_routes = {
    for key, route in local.rewrite_routes : key => route
    if route.target == "ssr" || local.playground_enabled
  }

  target_group_arns = merge(
    var.resources_enabled ? { ssr = aws_lb_target_group.ssr[0].arn } : {},
    local.playground_enabled ? { playground = aws_lb_target_group.playground[0].arn } : {},
  )

  # ALB rules accept at most five condition values, so hosts are chunked.
  host_chunks = chunklist(local.host_names, 4)
}

# Worker-only internal API surface; never reachable from the public edge.
resource "aws_lb_listener_rule" "internal" {
  count = local.enabled

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 10

  condition {
    path_pattern {
      values = ["/internal/*"]
    }
  }

  action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

# GitHub delivers signed docs-invalidation webhooks; the application verifies
# the signature, so this path bypasses the interactive dev gate.
resource "aws_lb_listener_rule" "github_hook" {
  count = local.enabled

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 11

  condition {
    path_pattern {
      values = ["/__hooks/github"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ssr[0].arn
  }
}

resource "aws_lb_listener_rule" "rewrite" {
  for_each = var.resources_enabled ? local.active_rewrite_routes : {}

  listener_arn = aws_lb_listener.https[0].arn
  priority     = each.value.priority

  condition {
    path_pattern {
      values = each.value.paths
    }
  }

  transform {
    type = "host-header-rewrite"

    host_header_rewrite_config {
      rewrite {
        regex   = "^.*$"
        replace = each.value.host
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
    for_each = local.is_dev ? [1] : []

    content {
      type = "authenticate-cognito"

      authenticate_cognito {
        user_pool_arn              = aws_cognito_user_pool.dev_gate[0].arn
        user_pool_client_id        = aws_cognito_user_pool_client.dev_gate[0].id
        user_pool_domain           = aws_cognito_user_pool_domain.dev_gate[0].domain
        on_unauthenticated_request = "authenticate"
        session_timeout            = 43200
      }
    }
  }

  action {
    type             = "forward"
    target_group_arn = local.target_group_arns[each.value.target]
  }
}

resource "aws_lb_listener_rule" "hosts" {
  count = var.resources_enabled ? length(local.host_chunks) : 0

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 100 + count.index

  condition {
    host_header {
      values = local.host_chunks[count.index]
    }
  }

  dynamic "action" {
    for_each = local.is_dev ? [1] : []

    content {
      type = "authenticate-cognito"

      authenticate_cognito {
        user_pool_arn              = aws_cognito_user_pool.dev_gate[0].arn
        user_pool_client_id        = aws_cognito_user_pool_client.dev_gate[0].id
        user_pool_domain           = aws_cognito_user_pool_domain.dev_gate[0].domain
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
