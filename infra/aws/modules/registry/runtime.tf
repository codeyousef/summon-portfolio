locals {
  # Hosts that receive an internal listener rule: routine services always,
  # signers once their keys exist.
  routed_hosts = merge(
    { for name, service in local.services : name => local.internal_hosts[service.host] if local.routine_ready && var.ecs_services_enabled },
    { for role in local.online_roles : "signer-${role}" => local.internal_hosts[role] if local.signers_ready && var.ecs_services_enabled },
  )
}

resource "aws_security_group" "internal_alb" {
  count = var.enabled ? 1 : 0

  name        = "${local.name}-registry-alb"
  description = "Internal registry and signer endpoints"
  vpc_id      = var.network.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "internal_alb_from_callers" {
  for_each = var.enabled ? {
    gateway      = var.network.gateway_security_group
    coordinators = aws_security_group.runtime[0].id
  } : {}

  security_group_id            = aws_security_group.internal_alb[0].id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

resource "aws_vpc_security_group_egress_rule" "internal_alb_to_targets" {
  for_each = merge(
    var.enabled ? { runtime = aws_security_group.runtime[0].id } : {},
    local.kms_ready ? { signers = aws_security_group.signer[0].id } : {},
  )

  security_group_id            = aws_security_group.internal_alb[0].id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_security_group" "runtime" {
  count = var.enabled ? 1 : 0

  name        = "${local.name}-registry-runtime"
  description = "Registry APIs, coordinators, and jobs"
  vpc_id      = var.network.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "runtime_from_alb" {
  count = var.enabled ? 1 : 0

  security_group_id            = aws_security_group.runtime[0].id
  referenced_security_group_id = aws_security_group.internal_alb[0].id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

# AWS APIs, the internal signer endpoints, and GitHub/GitLab for source checks.
resource "aws_vpc_security_group_egress_rule" "runtime_https" {
  count = var.enabled ? 1 : 0

  security_group_id = aws_security_group.runtime[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_security_group" "signer" {
  count = local.kms_ready ? 1 : 0

  name        = "${local.name}-registry-signer"
  description = "Role-locked TUF signers"
  vpc_id      = var.network.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "signer_from_alb" {
  count = local.kms_ready ? 1 : 0

  security_group_id            = aws_security_group.signer[0].id
  referenced_security_group_id = aws_security_group.internal_alb[0].id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

# KMS, regional STS for caller-identity checks, and S3.
resource "aws_vpc_security_group_egress_rule" "signer_https" {
  count = local.kms_ready ? 1 : 0

  security_group_id = aws_security_group.signer[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_lb" "internal" {
  count = local.active && var.ecs_services_enabled ? 1 : 0

  name                       = "${local.name}-registry"
  load_balancer_type         = "application"
  internal                   = true
  security_groups            = [aws_security_group.internal_alb[0].id]
  subnets                    = var.network.isolated_subnet_ids
  drop_invalid_header_fields = true
  enable_deletion_protection = !local.is_dev
}

resource "aws_acm_certificate" "internal" {
  count = var.enabled ? 1 : 0

  domain_name       = "*.registry.${var.network.host_suffix}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "internal_certificate_validation" {
  for_each = var.enabled ? {
    for option in aws_acm_certificate.internal[0].domain_validation_options : option.domain_name => option
  } : {}

  zone_id         = var.network.zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  ttl             = 300
  records         = [each.value.resource_record_value]
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "internal" {
  count = local.edge_ready ? 1 : 0

  certificate_arn         = aws_acm_certificate.internal[0].arn
  validation_record_fqdns = [for record in aws_route53_record.internal_certificate_validation : record.fqdn]
}

# Signers require X-Forwarded-Proto: https, so TLS terminates here.
resource "aws_lb_listener" "internal" {
  count = local.edge_ready && var.ecs_services_enabled ? 1 : 0

  load_balancer_arn = aws_lb.internal[0].arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.internal[0].certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

resource "aws_route53_record" "internal" {
  for_each = local.active && var.ecs_services_enabled ? local.internal_hosts : {}

  zone_id = var.network.zone_id
  name    = each.value
  type    = "A"

  alias {
    name                   = aws_lb.internal[0].dns_name
    zone_id                = aws_lb.internal[0].zone_id
    evaluate_target_health = false
  }
}

resource "aws_lb_target_group" "registry" {
  for_each = local.routed_hosts

  name                 = substr("${local.name}-reg-${each.key}", 0, 32)
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = var.network.vpc_id
  deregistration_delay = 30

  health_check {
    path                = startswith(each.key, "signer-") ? "/healthz" : "/health"
    matcher             = startswith(each.key, "signer-") ? "204" : "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener_rule" "registry" {
  for_each = local.routed_hosts

  listener_arn = aws_lb_listener.internal[0].arn
  priority     = 100 + index(sort(keys(local.routed_hosts)), each.key)

  condition {
    host_header {
      values = [each.value]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.registry[each.key].arn
  }
}

resource "aws_cloudwatch_log_group" "registry" {
  for_each = var.enabled ? toset(concat(keys(local.runtimes), [for role in local.online_roles : "signer-${role}"])) : toset([])

  name              = "/${local.name}/registry/${each.key}"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_task_definition" "runtime" {
  for_each = local.routine_ready ? toset(keys(local.runtimes)) : toset([])

  family                   = "${local.name}-app-registry-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = local.runtimes[each.key].cpu
  memory                   = local.runtimes[each.key].memory
  execution_role_arn       = aws_iam_role.registry_execution[0].arn
  task_role_arn            = aws_iam_role.runtime[each.key].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "registry"
    image        = var.image
    essential    = true
    command      = local.runtimes[each.key].command
    portMappings = local.runtimes[each.key].kind == "service" ? [{ containerPort = 8080, protocol = "tcp" }] : []
    environment = [
      for name in sort(keys(local.runtime_environment[each.key])) : { name = name, value = local.runtime_environment[each.key][name] }
    ]
    secrets = [
      for name in sort(local.runtimes[each.key].secrets) : { name = name, valueFrom = aws_secretsmanager_secret.registry[name].arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.registry[each.key].name
        awslogs-region        = var.region
        awslogs-stream-prefix = each.key
      }
    }
  }])
}

resource "aws_ecs_service" "registry" {
  for_each = local.routine_ready && var.ecs_services_enabled ? toset(keys(local.services)) : toset([])

  name                              = "app-registry-${each.key}"
  cluster                           = var.network.cluster_arn
  task_definition                   = aws_ecs_task_definition.runtime[each.key].arn
  desired_count                     = 1
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 120
  enable_execute_command            = false

  network_configuration {
    subnets          = var.network.public_subnet_ids
    security_groups  = [aws_security_group.runtime[0].id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.registry[each.key].arn
    container_name   = "registry"
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [task_definition]
  }
}

resource "aws_ecs_task_definition" "signer" {
  for_each = local.signers_ready ? toset(local.online_roles) : toset([])

  family                   = "${local.name}-signer-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.signer_execution[0].arn
  task_role_arn            = aws_iam_role.signer[each.key].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "signer"
    image        = var.signer_image
    essential    = true
    command      = ["serve-tuf-signer"]
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      for name, value in local.signer_environment[each.key] : { name = name, value = value }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.registry["signer-${each.key}"].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "signer"
      }
    }
  }])
}

resource "aws_ecs_service" "signer" {
  for_each = local.signers_ready && var.ecs_services_enabled ? toset(local.online_roles) : toset([])

  name                              = "signer-${each.key}"
  cluster                           = var.network.cluster_arn
  task_definition                   = aws_ecs_task_definition.signer[each.key].arn
  desired_count                     = 1
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 60
  enable_execute_command            = false

  network_configuration {
    subnets          = var.network.public_subnet_ids
    security_groups  = [aws_security_group.signer[0].id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.registry["signer-${each.key}"].arn
    container_name   = "signer"
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [task_definition]
  }
}

data "aws_iam_policy_document" "scheduler_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [var.aws_account_id]
    }
  }
}

locals {
  scheduled_jobs = {
    for name, job in local.jobs : name => job
    if local.routine_ready && job.schedule != null && (job.schedule.group == "review" || var.refresh_schedules_enabled)
  }
}

resource "aws_iam_role" "registry_scheduler" {
  count = length(local.scheduled_jobs) > 0 ? 1 : 0

  name               = "${local.name}-registry-scheduler"
  assume_role_policy = data.aws_iam_policy_document.scheduler_trust.json
}

resource "aws_iam_role_policy" "registry_scheduler" {
  count = length(local.scheduled_jobs) > 0 ? 1 : 0

  name = "run-registry-jobs"
  role = aws_iam_role.registry_scheduler[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Action    = "ecs:RunTask"
        Resource  = [for name in keys(local.scheduled_jobs) : "arn:aws:ecs:${var.region}:${var.aws_account_id}:task-definition/${local.name}-app-registry-${name}:*"]
        Condition = { ArnEquals = { "ecs:cluster" = var.network.cluster_arn } }
      },
      {
        Effect    = "Allow"
        Action    = "iam:PassRole"
        Resource  = concat([aws_iam_role.registry_execution[0].arn], [for name in keys(local.scheduled_jobs) : aws_iam_role.runtime[name].arn])
        Condition = { StringEquals = { "iam:PassedToService" = "ecs-tasks.amazonaws.com" } }
      },
    ]
  })
}

resource "aws_scheduler_schedule_group" "registry" {
  count = length(local.scheduled_jobs) > 0 ? 1 : 0

  name = "${local.name}-registry"
}

# Every registry schedule is created DISABLED. Enabling one requires a
# separate approval naming it, after its target, caller identity, retry
# policy, and one scheduler-originated execution are verified.
resource "aws_scheduler_schedule" "registry" {
  for_each = local.scheduled_jobs

  name                         = each.key
  group_name                   = aws_scheduler_schedule_group.registry[0].name
  schedule_expression          = each.value.schedule.expression
  schedule_expression_timezone = "UTC"
  state                        = var.schedules_enabled ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = var.network.cluster_arn
    role_arn = aws_iam_role.registry_scheduler[0].arn

    ecs_parameters {
      task_definition_arn = trimsuffix(aws_ecs_task_definition.runtime[each.key].arn, ":${aws_ecs_task_definition.runtime[each.key].revision}")
      launch_type         = "FARGATE"
      task_count          = 1

      network_configuration {
        subnets          = var.network.public_subnet_ids
        security_groups  = [aws_security_group.runtime[0].id]
        assign_public_ip = true
      }
    }

    # One task, zero retries for every one-shot job.
    retry_policy {
      maximum_event_age_in_seconds = 300
      maximum_retry_attempts       = 0
    }
  }
}
