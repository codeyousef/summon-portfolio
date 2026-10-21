locals {
  ci_plan_roles   = { for key, role in local.ci_roles : key => role if role.kind == "plan" }
  ci_apply_roles  = { for key, role in local.ci_roles : key => role if role.kind == "apply" }
  ci_deploy_roles = { for key, role in local.ci_roles : key => role if contains(["deploy", "signer-deploy"], role.kind) }

  # Application data and signing operations that no infrastructure identity
  # may perform directly. Runtime task roles own these.
  denied_data_plane_actions = [
    "dynamodb:BatchGetItem",
    "dynamodb:BatchWriteItem",
    "dynamodb:DeleteItem",
    "dynamodb:ExecuteStatement",
    "dynamodb:ExecuteTransaction",
    "dynamodb:BatchExecuteStatement",
    "dynamodb:GetItem",
    "dynamodb:PutItem",
    "dynamodb:Query",
    "dynamodb:Scan",
    "dynamodb:TransactGetItems",
    "dynamodb:TransactWriteItems",
    "dynamodb:UpdateItem",
    "kms:Decrypt",
    "kms:GenerateMac",
    "kms:Sign",
    "secretsmanager:GetSecretValue",
    "secretsmanager:PutSecretValue",
    "sqs:ReceiveMessage",
    "sqs:SendMessage",
    "ecs:ExecuteCommand",
    "ssm:StartSession",
  ]
}

data "aws_iam_policy_document" "ci_state" {
  for_each = merge(local.ci_plan_roles, local.ci_apply_roles)

  statement {
    sid       = "ListState"
    actions   = ["s3:ListBucket"]
    resources = [local.state_bucket_arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["state/${each.value.environment}/*"]
    }
  }

  statement {
    sid     = "ReadState"
    actions = ["s3:GetObject"]
    resources = [
      "${local.state_bucket_arn}/state/${each.value.environment}/*",
    ]
  }

  statement {
    sid     = each.value.kind == "apply" ? "WriteStateAndLock" : "WriteLockOnly"
    actions = ["s3:PutObject", "s3:DeleteObject"]
    resources = each.value.kind == "apply" ? [
      "${local.state_bucket_arn}/state/${each.value.environment}/*",
      ] : [
      "${local.state_bucket_arn}/state/${each.value.environment}/*.tflock",
    ]
  }
}

data "aws_iam_policy_document" "ci_guardrails" {
  for_each = merge(local.ci_plan_roles, local.ci_apply_roles)

  statement {
    sid       = "DenyDataPlaneAndSigning"
    effect    = "Deny"
    actions   = local.denied_data_plane_actions
    resources = ["*"]
  }

  statement {
    sid           = "DenyApplicationObjectAccess"
    effect        = "Deny"
    actions       = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:DeleteObject", "s3:DeleteObjectVersion"]
    not_resources = ["${local.state_bucket_arn}/*"]
  }

  statement {
    sid    = "DenyCiIdentityTampering"
    effect = "Deny"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:PutRolePermissionsBoundary",
      "iam:PutRolePolicy",
      "iam:UpdateAssumeRolePolicy",
      "iam:UpdateRole",
      "iam:TagRole",
      "iam:UntagRole",
    ]
    resources = ["arn:aws:iam::${var.aws_account_id}:role/portfolio-ci-*"]
  }

  statement {
    sid       = "DenyOidcProviderTampering"
    effect    = "Deny"
    actions   = ["iam:*OpenIDConnectProvider*"]
    resources = [local.oidc_provider_arn]
  }

  statement {
    sid       = "DenyOrganizationAndAccountChanges"
    effect    = "Deny"
    actions   = ["organizations:*", "account:*"]
    resources = ["*"]
  }

  statement {
    sid       = "DenySharedFoundationChanges"
    effect    = "Deny"
    actions   = ["route53:DeleteHostedZone", "ecr:DeleteRepository", "ecr:PutLifecyclePolicy", "ecr:SetRepositoryPolicy"]
    resources = ["*"]
  }

  statement {
    sid     = "DenyOtherEnvironmentState"
    effect  = "Deny"
    actions = ["s3:PutObject", "s3:DeleteObject"]
    not_resources = [
      "${local.state_bucket_arn}/state/${each.value.environment}/*",
    ]
  }
}

resource "aws_iam_role_policy" "ci_state" {
  for_each = var.resources_enabled ? merge(local.ci_plan_roles, local.ci_apply_roles) : {}

  name   = "state"
  role   = aws_iam_role.ci[each.key].id
  policy = data.aws_iam_policy_document.ci_state[each.key].json
}

resource "aws_iam_role_policy" "ci_guardrails" {
  for_each = var.resources_enabled ? merge(local.ci_plan_roles, local.ci_apply_roles) : {}

  name   = "guardrails"
  role   = aws_iam_role.ci[each.key].id
  policy = data.aws_iam_policy_document.ci_guardrails[each.key].json
}

resource "aws_iam_role_policy_attachment" "ci_plan_read_only" {
  for_each = var.resources_enabled ? local.ci_plan_roles : {}

  role       = aws_iam_role.ci[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

# Apply identities can change any infrastructure the reviewed saved plan
# contains, within the guardrails above. Protected review of that exact plan
# is the production security boundary.
resource "aws_iam_role_policy_attachment" "ci_apply_administrator" {
  for_each = var.resources_enabled ? local.ci_apply_roles : {}

  role       = aws_iam_role.ci[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}

data "aws_iam_policy_document" "ci_deploy" {
  for_each = local.ci_deploy_roles

  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PushImages"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeImageScanFindings",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [
      for repository in(each.value.kind == "signer-deploy" ? local.privileged_repositories : local.routine_repositories) :
      "arn:aws:ecr:${var.region}:${var.aws_account_id}:repository/${repository}"
    ]
  }

  statement {
    sid       = "ReadTaskDefinitions"
    actions   = ["ecs:DescribeTaskDefinition", "ecs:ListTaskDefinitionFamilies", "ecs:ListTaskDefinitions", "ecs:DescribeClusters"]
    resources = ["*"]
  }

  statement {
    sid     = "RegisterOwnTaskDefinitions"
    actions = ["ecs:RegisterTaskDefinition", "ecs:TagResource"]
    resources = [
      "arn:aws:ecs:${var.region}:${var.aws_account_id}:task-definition/portfolio-${each.value.environment}-${each.value.kind == "signer-deploy" ? "signer" : "app"}-*:*",
    ]
  }

  statement {
    sid     = "RolloutOwnServices"
    actions = ["ecs:DescribeServices", "ecs:UpdateService"]
    resources = [
      "arn:aws:ecs:${var.region}:${var.aws_account_id}:service/portfolio-${each.value.environment}/${each.value.kind == "signer-deploy" ? "signer" : "app"}-*",
    ]
  }

  statement {
    sid     = "PassOwnRuntimeRoles"
    actions = ["iam:PassRole"]
    resources = [
      "arn:aws:iam::${var.aws_account_id}:role/portfolio-${each.value.environment}-${each.value.kind == "signer-deploy" ? "signer" : "app"}-*",
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "ci_deploy" {
  for_each = var.resources_enabled ? local.ci_deploy_roles : {}

  name   = "deploy"
  role   = aws_iam_role.ci[each.key].id
  policy = data.aws_iam_policy_document.ci_deploy[each.key].json
}
