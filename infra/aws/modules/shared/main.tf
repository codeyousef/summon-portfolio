locals {
  enabled = var.resources_enabled ? 1 : 0

  # GitHub environments that may assume each CI identity. Protected
  # production environments must allow only master, require the operator's
  # approval, and keep administrator bypass disabled; IAM cannot see the ref,
  # so that branch rule is enforced by the GitHub environment itself.
  ci_roles = merge([
    for environment in var.environments : {
      "${environment}-plan" = {
        environment        = environment
        kind               = "plan"
        github_environment = "portfolio-aws-${environment}-plan"
      }
      "${environment}-apply" = {
        environment        = environment
        kind               = "apply"
        github_environment = "portfolio-aws-${environment}-apply"
      }
      "${environment}-deploy" = {
        environment        = environment
        kind               = "deploy"
        github_environment = environment == "prod" ? "portfolio-production" : "portfolio-development"
      }
      "${environment}-signer-deploy" = {
        environment        = environment
        kind               = "signer-deploy"
        github_environment = "portfolio-aws-${environment}-signer"
      }
    }
  ]...)

  routine_repositories    = [for name, spec in var.image_repositories : name if !spec.privileged]
  privileged_repositories = [for name, spec in var.image_repositories : name if spec.privileged]

  state_bucket_arn = "arn:aws:s3:::${var.state_bucket_name}"
  oidc_provider_arn = (
    "arn:aws:iam::${var.aws_account_id}:oidc-provider/token.actions.githubusercontent.com"
  )
}

resource "aws_route53_zone" "primary" {
  count = local.enabled

  name    = var.zone_name
  comment = "Portfolio public zone; migrated from Cloudflare"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_route53_record" "extra" {
  for_each = var.resources_enabled ? var.dns_records : {}

  zone_id = aws_route53_zone.primary[0].zone_id
  name    = each.value.name
  type    = each.value.type
  ttl     = each.value.ttl
  records = each.value.records
}

resource "aws_ecr_repository" "images" {
  for_each = var.resources_enabled ? var.image_repositories : {}

  name                 = each.key
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Privileged = tostring(each.value.privileged)
  }
}

resource "aws_ecr_lifecycle_policy" "images" {
  for_each = aws_ecr_repository.images

  repository = each.value.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged layers after 14 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 14
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep the 100 most recent tagged images for rollback"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = 100
        }
        action = { type = "expire" }
      },
    ]
  })
}

resource "aws_iam_openid_connect_provider" "github" {
  count = local.enabled

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_policy_document" "ci_trust" {
  for_each = local.ci_roles

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${each.value.github_environment}"]
    }
  }
}

resource "aws_iam_role" "ci" {
  for_each = var.resources_enabled ? local.ci_roles : {}

  name                 = "portfolio-ci-${each.key}"
  assume_role_policy   = data.aws_iam_policy_document.ci_trust[each.key].json
  max_session_duration = 3600

  tags = {
    Environment       = each.value.environment
    CiKind            = each.value.kind
    GithubEnvironment = each.value.github_environment
  }

  depends_on = [aws_iam_openid_connect_provider.github]
}
