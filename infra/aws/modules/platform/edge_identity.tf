data "aws_route53_zone" "primary" {
  count = local.enabled

  name         = var.zone_name
  private_zone = false
}

# CloudFront requires its certificate in us-east-1; the ALB terminates the
# CloudFront-to-origin TLS hop with a regional certificate for the same names,
# because CloudFront forwards the viewer Host and validates the origin against it.
resource "aws_acm_certificate" "cloudfront" {
  count    = local.enabled
  provider = aws.us_east_1

  domain_name               = local.hosts.main
  subject_alternative_names = [for host in local.host_names : host if host != local.hosts.main]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_acm_certificate" "origin" {
  count = local.enabled

  domain_name               = local.hosts.main
  subject_alternative_names = [for host in local.host_names : host if host != local.hosts.main]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

locals {
  certificates = merge(
    var.resources_enabled ? { cloudfront = aws_acm_certificate.cloudfront[0], origin = aws_acm_certificate.origin[0] } : {},
  )
}

# One record set per certificate, keyed by domain name (known at plan time).
# ACM normally issues the same CNAME for a domain in every region of an
# account; when it does, both resources upsert the identical record.
resource "aws_route53_record" "cloudfront_certificate_validation" {
  for_each = var.resources_enabled ? {
    for option in aws_acm_certificate.cloudfront[0].domain_validation_options : option.domain_name => option
  } : {}

  zone_id         = data.aws_route53_zone.primary[0].zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  ttl             = 300
  records         = [each.value.resource_record_value]
  allow_overwrite = true
}

resource "aws_route53_record" "origin_certificate_validation" {
  for_each = var.resources_enabled ? {
    for option in aws_acm_certificate.origin[0].domain_validation_options : option.domain_name => option
  } : {}

  zone_id         = data.aws_route53_zone.primary[0].zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  ttl             = 300
  records         = [each.value.resource_record_value]
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "cloudfront" {
  count    = local.edge
  provider = aws.us_east_1

  certificate_arn         = aws_acm_certificate.cloudfront[0].arn
  validation_record_fqdns = [for record in aws_route53_record.cloudfront_certificate_validation : record.fqdn]
}

resource "aws_acm_certificate_validation" "origin" {
  count = local.edge

  certificate_arn         = aws_acm_certificate.origin[0].arn
  validation_record_fqdns = [for record in aws_route53_record.origin_certificate_validation : record.fqdn]
}

resource "aws_route53_record" "hosts" {
  for_each = local.site_distribution ? {
    for pair in setproduct(keys(local.hosts), ["A", "AAAA"]) : "${pair[0]}-${pair[1]}" => {
      host = local.hosts[pair[0]]
      type = pair[1]
    }
  } : {}

  zone_id = data.aws_route53_zone.primary[0].zone_id
  name    = each.value.host
  type    = each.value.type

  alias {
    name                   = aws_cloudfront_distribution.site[0].domain_name
    zone_id                = aws_cloudfront_distribution.site[0].hosted_zone_id
    evaluate_target_health = false
  }
}

# Owner-only development gate, replacing Cloudflare Access. Self sign-up is
# disabled and MFA is mandatory.
resource "aws_cognito_user_pool" "dev_gate" {
  count = var.resources_enabled && local.is_dev ? 1 : 0

  name                     = "${local.name}-gate"
  deletion_protection      = "ACTIVE"
  mfa_configuration        = "ON"
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  software_token_mfa_configuration {
    enabled = true
  }

  password_policy {
    minimum_length                   = 16
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = true
    temporary_password_validity_days = 3
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

resource "aws_cognito_user_pool_domain" "dev_gate" {
  count = var.resources_enabled && local.is_dev ? 1 : 0

  domain       = "yousef-${local.name}-gate"
  user_pool_id = aws_cognito_user_pool.dev_gate[0].id
}

resource "aws_cognito_user_pool_client" "dev_gate" {
  count = var.resources_enabled && local.is_dev ? 1 : 0

  name                                 = "${local.name}-alb"
  user_pool_id                         = aws_cognito_user_pool.dev_gate[0].id
  generate_secret                      = true
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = [for host in local.host_names : "https://${host}/oauth2/idpresponse"]
  prevent_user_existence_errors        = "ENABLED"
  explicit_auth_flows                  = ["ALLOW_REFRESH_TOKEN_AUTH", "ALLOW_USER_SRP_AUTH"]
  access_token_validity                = 60
  id_token_validity                    = 60
  refresh_token_validity               = 12

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "hours"
  }
}

resource "aws_cognito_user" "dev_gate_owner" {
  count = var.resources_enabled && local.is_dev ? 1 : 0

  user_pool_id             = aws_cognito_user_pool.dev_gate[0].id
  username                 = var.dev_gate_owner_email
  desired_delivery_mediums = ["EMAIL"]

  attributes = {
    email          = var.dev_gate_owner_email
    email_verified = "true"
  }
}
