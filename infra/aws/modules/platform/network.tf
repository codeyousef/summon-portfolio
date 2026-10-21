resource "aws_vpc" "main" {
  count = local.enabled

  cidr_block           = local.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.name }
}

resource "aws_internet_gateway" "main" {
  count = local.enabled

  vpc_id = aws_vpc.main[0].id
  tags   = { Name = local.name }
}

# Public subnets hold the ALB and the internet-facing services. Tasks receive
# public IPs for egress instead of a NAT gateway; their security groups admit
# inbound traffic only from the ALB.
resource "aws_subnet" "public" {
  for_each = var.resources_enabled ? local.public_subnets : {}

  vpc_id                  = aws_vpc.main[0].id
  availability_zone       = each.key
  cidr_block              = each.value
  map_public_ip_on_launch = false

  tags = { Name = "${local.name}-public-${each.key}", Tier = "public" }
}

# Isolated subnets have no internet route. The Seen playground executes
# untrusted programs and reaches only VPC endpoints from here.
resource "aws_subnet" "isolated" {
  for_each = var.resources_enabled ? local.isolated_subnets : {}

  vpc_id            = aws_vpc.main[0].id
  availability_zone = each.key
  cidr_block        = each.value

  tags = { Name = "${local.name}-isolated-${each.key}", Tier = "isolated" }
}

resource "aws_route_table" "public" {
  count = local.enabled

  vpc_id = aws_vpc.main[0].id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main[0].id
  }

  tags = { Name = "${local.name}-public" }
}

resource "aws_route_table" "isolated" {
  count = local.enabled

  vpc_id = aws_vpc.main[0].id
  tags   = { Name = "${local.name}-isolated" }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public[0].id
}

resource "aws_route_table_association" "isolated" {
  for_each = aws_subnet.isolated

  subnet_id      = each.value.id
  route_table_id = aws_route_table.isolated[0].id
}

resource "aws_vpc_endpoint" "gateway" {
  for_each = var.resources_enabled ? toset(["s3", "dynamodb"]) : toset([])

  vpc_id            = aws_vpc.main[0].id
  service_name      = "com.amazonaws.${var.region}.${each.key}"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public[0].id, aws_route_table.isolated[0].id]

  tags = { Name = "${local.name}-${each.key}" }
}

resource "aws_security_group" "endpoints" {
  count = local.playground_enabled ? 1 : 0

  name        = "${local.name}-endpoints"
  description = "Interface endpoints reachable from isolated tasks"
  vpc_id      = aws_vpc.main[0].id
}

resource "aws_vpc_security_group_ingress_rule" "endpoints_from_playground" {
  count = local.playground_enabled ? 1 : 0

  security_group_id            = aws_security_group.endpoints[0].id
  referenced_security_group_id = aws_security_group.playground[0].id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

# Image pulls and logs for isolated tasks. Created only when the playground
# runs, since each interface endpoint is billed hourly.
resource "aws_vpc_endpoint" "interface" {
  for_each = local.playground_enabled ? toset(["ecr.api", "ecr.dkr", "logs"]) : toset([])

  vpc_id              = aws_vpc.main[0].id
  service_name        = "com.amazonaws.${var.region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = [for subnet in aws_subnet.isolated : subnet.id]
  security_group_ids  = [aws_security_group.endpoints[0].id]

  tags = { Name = "${local.name}-${each.key}" }
}

data "aws_ec2_managed_prefix_list" "cloudfront_origin" {
  count = local.enabled

  name = "com.amazonaws.global.cloudfront.origin-facing"
}

data "aws_ec2_managed_prefix_list" "s3" {
  count = local.enabled

  name = "com.amazonaws.${var.region}.s3"
}

resource "aws_security_group" "alb" {
  count = local.enabled

  name        = "${local.name}-alb"
  description = "ALB reachable only from CloudFront origin-facing addresses"
  vpc_id      = aws_vpc.main[0].id
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  count = local.enabled

  security_group_id = aws_security_group.alb[0].id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront_origin[0].id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "alb_to_services" {
  for_each = merge(
    var.resources_enabled ? { ssr = aws_security_group.ssr[0].id } : {},
    local.playground_enabled ? { playground = aws_security_group.playground[0].id } : {},
  )

  security_group_id            = aws_security_group.alb[0].id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

# The ALB calls the Cognito token and userinfo endpoints for the dev gate.
resource "aws_vpc_security_group_egress_rule" "alb_to_cognito" {
  count = var.resources_enabled && local.is_dev ? 1 : 0

  security_group_id = aws_security_group.alb[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_security_group" "ssr" {
  count = local.enabled

  name        = "${local.name}-ssr"
  description = "Portfolio SSR tasks"
  vpc_id      = aws_vpc.main[0].id
}

resource "aws_vpc_security_group_ingress_rule" "ssr_from_alb" {
  count = local.enabled

  security_group_id            = aws_security_group.ssr[0].id
  referenced_security_group_id = aws_security_group.alb[0].id
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
  vpc_id      = aws_vpc.main[0].id
}

resource "aws_vpc_security_group_ingress_rule" "playground_from_alb" {
  count = local.playground_enabled ? 1 : 0

  security_group_id            = aws_security_group.playground[0].id
  referenced_security_group_id = aws_security_group.alb[0].id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_vpc_security_group_egress_rule" "playground_to_endpoints" {
  count = local.playground_enabled ? 1 : 0

  security_group_id            = aws_security_group.playground[0].id
  referenced_security_group_id = aws_security_group.endpoints[0].id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

# ECR image layers are served from S3 through the gateway endpoint.
resource "aws_vpc_security_group_egress_rule" "playground_to_s3" {
  count = local.playground_enabled ? 1 : 0

  security_group_id = aws_security_group.playground[0].id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.s3[0].id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}
