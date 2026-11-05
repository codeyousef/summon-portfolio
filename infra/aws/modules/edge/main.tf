variable "aws_account_id" {
  type = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "region" {
  type    = string
  default = "eu-north-1"

  validation {
    condition     = var.region == "eu-north-1"
    error_message = "The organization region policy allows regional resources only in eu-north-1."
  }
}

variable "resources_enabled" {
  description = "Fail-closed creation gate for the shared network and public ALB."
  type        = bool
  default     = false
}

variable "vpc_cidr" {
  type    = string
  default = "10.80.0.0/16"
}

variable "availability_zones" {
  type    = list(string)
  default = ["eu-north-1a", "eu-north-1b"]

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "Exactly two availability zones are required."
  }
}

variable "isolated_endpoints_enabled" {
  description = "ECR and Logs interface endpoints for isolated (no-internet) tasks such as the Seen playground. Billed hourly."
  type        = bool
  default     = false
}

variable "zone_name" {
  type    = string
  default = "yousef.codes"
}

variable "certificates_validated" {
  description = "Set only after the listener's default certificate reports ISSUED."
  type        = bool
  default     = false
}

locals {
  enabled = var.resources_enabled ? 1 : 0
  edge    = var.resources_enabled && var.certificates_validated ? 1 : 0
  # Default listener certificate; environments attach their own through SNI.
  origin_host = "origin.${var.zone_name}"
  name        = "portfolio-edge"

  public_subnets   = { for index, zone in var.availability_zones : zone => cidrsubnet(var.vpc_cidr, 4, index) }
  isolated_subnets = { for index, zone in var.availability_zones : zone => cidrsubnet(var.vpc_cidr, 4, index + 8) }
}

# One VPC and one public ALB serve dev and prod. Each environment owns its
# certificates, target groups, host-scoped listener rules, clusters, roles,
# and data; the shared listener answers unknown hosts with 404.
resource "aws_vpc" "main" {
  count = local.enabled

  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.name }
}

resource "aws_internet_gateway" "main" {
  count = local.enabled

  vpc_id = aws_vpc.main[0].id
  tags   = { Name = local.name }
}

# Public subnets hold the ALB and internet-facing tasks. Tasks get public IPs
# for egress instead of a NAT gateway; their security groups admit inbound
# traffic only from the ALB.
resource "aws_subnet" "public" {
  for_each = var.resources_enabled ? local.public_subnets : {}

  vpc_id                  = aws_vpc.main[0].id
  availability_zone       = each.key
  cidr_block              = each.value
  map_public_ip_on_launch = false

  tags = { Name = "${local.name}-public-${each.key}", Tier = "public" }
}

# Isolated subnets have no internet route; untrusted Seen programs run here.
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
  for_each = var.resources_enabled ? local.public_subnets : {}

  subnet_id      = aws_subnet.public[each.key].id
  route_table_id = aws_route_table.public[0].id
}

resource "aws_route_table_association" "isolated" {
  for_each = var.resources_enabled ? local.isolated_subnets : {}

  subnet_id      = aws_subnet.isolated[each.key].id
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

# Isolated tasks join this group to reach the interface endpoints.
resource "aws_security_group" "isolated_clients" {
  count = local.enabled

  name        = "${local.name}-isolated-clients"
  description = "Tasks allowed to use the isolated-subnet interface endpoints"
  vpc_id      = aws_vpc.main[0].id
}

resource "aws_security_group" "endpoints" {
  count = local.enabled

  name        = "${local.name}-endpoints"
  description = "Interface endpoints reachable from isolated tasks"
  vpc_id      = aws_vpc.main[0].id
}

resource "aws_vpc_security_group_ingress_rule" "endpoints_from_isolated" {
  count = local.enabled

  security_group_id            = aws_security_group.endpoints[0].id
  referenced_security_group_id = aws_security_group.isolated_clients[0].id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

data "aws_ec2_managed_prefix_list" "s3" {
  count = local.enabled

  name = "com.amazonaws.${var.region}.s3"
}

# Private DNS sends all ECR and Logs traffic in the VPC to these endpoints, so
# tasks in the public subnets have to reach them too.
resource "aws_vpc_security_group_ingress_rule" "endpoints_from_vpc" {
  count = var.resources_enabled && var.isolated_endpoints_enabled ? 1 : 0

  security_group_id = aws_security_group.endpoints[0].id
  description       = "HTTPS from tasks anywhere in the VPC"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = aws_vpc.main[0].cidr_block
}

resource "aws_vpc_security_group_egress_rule" "isolated_to_endpoints" {
  count = local.enabled

  security_group_id            = aws_security_group.isolated_clients[0].id
  referenced_security_group_id = aws_security_group.endpoints[0].id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

# ECR image layers are served from S3 through the gateway endpoint.
resource "aws_vpc_security_group_egress_rule" "isolated_to_s3" {
  count = local.enabled

  security_group_id = aws_security_group.isolated_clients[0].id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.s3[0].id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

# One subnet only: each endpoint ENI is billed hourly, and tasks in the other
# zone reach it across zones.
resource "aws_vpc_endpoint" "interface" {
  for_each = var.resources_enabled && var.isolated_endpoints_enabled ? toset(["ecr.api", "ecr.dkr", "logs"]) : toset([])

  vpc_id              = aws_vpc.main[0].id
  service_name        = "com.amazonaws.${var.region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = [aws_subnet.isolated[var.availability_zones[0]].id]
  security_group_ids  = [aws_security_group.endpoints[0].id]

  tags = { Name = "${local.name}-${each.key}" }
}

data "aws_ec2_managed_prefix_list" "cloudfront_origin" {
  count = local.enabled

  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  count = local.enabled

  name        = "${local.name}-alb"
  description = "Shared public ALB reachable only from CloudFront origin-facing addresses"
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

# Targets live in environment stacks; their own groups admit only this ALB.
resource "aws_vpc_security_group_egress_rule" "alb_to_targets" {
  count = local.enabled

  security_group_id = aws_security_group.alb[0].id
  cidr_ipv4         = var.vpc_cidr
  ip_protocol       = "tcp"
  from_port         = 8080
  to_port           = 8080
}

# The ALB calls the Cognito token and userinfo endpoints for the dev gate.
resource "aws_vpc_security_group_egress_rule" "alb_to_cognito" {
  count = local.enabled

  security_group_id = aws_security_group.alb[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_lb" "public" {
  count = local.enabled

  name                       = local.name
  load_balancer_type         = "application"
  internal                   = false
  security_groups            = [aws_security_group.alb[0].id]
  subnets                    = [for subnet in aws_subnet.public : subnet.id]
  drop_invalid_header_fields = true
  enable_deletion_protection = true
  idle_timeout               = 120
}

data "aws_route53_zone" "primary" {
  count = local.enabled

  name         = var.zone_name
  private_zone = false
}

resource "aws_acm_certificate" "default" {
  count = local.enabled

  domain_name       = local.origin_host
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "certificate_validation" {
  for_each = var.resources_enabled ? {
    for option in aws_acm_certificate.default[0].domain_validation_options : option.domain_name => option
  } : {}

  zone_id         = data.aws_route53_zone.primary[0].zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  ttl             = 300
  records         = [each.value.resource_record_value]
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "default" {
  count = local.edge

  certificate_arn         = aws_acm_certificate.default[0].arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}

# Unknown hosts are rejected; environments add host-scoped rules.
resource "aws_lb_listener" "https" {
  count = local.edge

  load_balancer_arn = aws_lb.public[0].arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.default[0].certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

output "certificate_validation_records" {
  value = var.resources_enabled ? {
    for option in aws_acm_certificate.default[0].domain_validation_options : option.domain_name => {
      name  = option.resource_record_name
      type  = option.resource_record_type
      value = option.resource_record_value
    }
  } : {}
}

output "listener_ready" {
  value = var.resources_enabled && var.certificates_validated
}

output "resources_enabled" {
  value = var.resources_enabled
}

output "names" {
  description = "Names environment stacks use to look up the shared edge."
  value = {
    vpc                     = local.name
    alb                     = local.name
    alb_security_group      = "${local.name}-alb"
    isolated_security_group = "${local.name}-isolated-clients"
  }
}

output "isolated_endpoints_enabled" {
  value = var.resources_enabled && var.isolated_endpoints_enabled
}
