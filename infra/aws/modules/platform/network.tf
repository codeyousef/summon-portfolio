# The shared edge stack (environments/edge) owns the VPC, subnets, VPC
# endpoints, public ALB, and its listener. This environment looks them up by
# name and adds only its own groups, target groups, and host-scoped rules.
data "aws_vpc" "edge" {
  count = local.enabled

  tags = { Name = "portfolio-edge" }
}

data "aws_subnets" "public" {
  count = local.enabled

  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.edge[0].id]
  }

  tags = { Tier = "public" }
}

data "aws_subnets" "isolated" {
  count = local.enabled

  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.edge[0].id]
  }

  tags = { Tier = "isolated" }
}

data "aws_security_group" "alb" {
  count = local.enabled

  vpc_id = data.aws_vpc.edge[0].id
  name   = "portfolio-edge-alb"
}

data "aws_security_group" "isolated_clients" {
  count = local.playground_enabled ? 1 : 0

  vpc_id = data.aws_vpc.edge[0].id
  name   = "portfolio-edge-isolated-clients"
}

# Fails the plan if the edge has not enabled the isolated-subnet endpoints the
# playground needs to pull images and write logs.
data "aws_vpc_endpoint" "isolated" {
  for_each = local.playground_enabled ? toset(["ecr.api", "ecr.dkr", "logs"]) : toset([])

  vpc_id       = data.aws_vpc.edge[0].id
  service_name = "com.amazonaws.${var.region}.${each.key}"
}

data "aws_lb" "edge" {
  count = local.enabled

  name = "portfolio-edge"
}

data "aws_lb_listener" "edge" {
  count = local.edge

  load_balancer_arn = data.aws_lb.edge[0].arn
  port              = 443
}

resource "aws_security_group" "ssr" {
  count = local.enabled

  name        = "${local.name}-ssr"
  description = "Portfolio SSR tasks"
  vpc_id      = data.aws_vpc.edge[0].id
}

resource "aws_vpc_security_group_ingress_rule" "ssr_from_alb" {
  count = local.enabled

  security_group_id            = aws_security_group.ssr[0].id
  referenced_security_group_id = data.aws_security_group.alb[0].id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

# GitHub docs, BigQuery, Stripe, Cloudflare billing, and AWS APIs.
resource "aws_vpc_security_group_egress_rule" "ssr_https" {
  count = local.enabled

  security_group_id = aws_security_group.ssr[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_security_group" "playground" {
  count = local.playground_enabled ? 1 : 0

  name        = "${local.name}-playground"
  description = "Seen playground tasks; no internet egress"
  vpc_id      = data.aws_vpc.edge[0].id
}

resource "aws_vpc_security_group_ingress_rule" "playground_from_alb" {
  count = local.playground_enabled ? 1 : 0

  security_group_id            = aws_security_group.playground[0].id
  referenced_security_group_id = data.aws_security_group.alb[0].id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}
