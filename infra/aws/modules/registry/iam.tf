data "aws_iam_policy_document" "ecs_tasks_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [var.aws_account_id]
    }
  }
}

# Routine registry roles follow portfolio-<env>-app-*; signer roles follow
# portfolio-<env>-signer-* so only the signer deploy identity can pass them.
resource "aws_iam_role" "registry_execution" {
  count = var.enabled ? 1 : 0

  name               = "${local.name}-app-registry-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy_attachment" "registry_execution" {
  count = var.enabled ? 1 : 0

  role       = aws_iam_role.registry_execution[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "registry_execution_secrets" {
  count = var.enabled && length(local.runtime_secret_names) > 0 ? 1 : 0

  name = "read-registry-secrets"
  role = aws_iam_role.registry_execution[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = [for secret in aws_secretsmanager_secret.registry : secret.arn]
    }]
  })
}

resource "aws_iam_role" "signer_execution" {
  count = local.kms_ready ? 1 : 0

  name               = "${local.name}-signer-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy_attachment" "signer_execution" {
  count = local.kms_ready ? 1 : 0

  role       = aws_iam_role.signer_execution[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "runtime" {
  for_each = var.enabled ? toset(keys(local.runtimes)) : toset([])

  name               = local.runtime_role_names[each.key]
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

locals {
  table_actions = {
    r     = ["dynamodb:GetItem", "dynamodb:Query", "dynamodb:Scan", "dynamodb:DescribeTable"]
    rw    = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:Query", "dynamodb:Scan", "dynamodb:ConditionCheckItem", "dynamodb:DescribeTable"]
    lease = ["dynamodb:GetItem", "dynamodb:UpdateItem", "dynamodb:DescribeTable"]
    copy  = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Scan", "dynamodb:DescribeTable"]
  }

  bucket_arn = { for role in local.bucket_roles : role => "arn:aws:s3:::${local.bucket_names[role]}" }

  metadata_pointer_arns = {
    for pointer in local.metadata_pointers : pointer => "${local.bucket_arn.metadata}/${local.object_prefix}/metadata/${pointer}"
  }
}

data "aws_iam_policy_document" "runtime" {
  for_each = var.enabled ? toset(keys(local.runtimes)) : toset([])

  dynamic "statement" {
    for_each = { for access in distinct(values(local.runtimes[each.key].tables)) : access => [for table, mode in local.runtimes[each.key].tables : table if mode == access] }

    content {
      sid       = "Tables${title(statement.key)}"
      actions   = local.table_actions[statement.key]
      resources = [for table in statement.value : aws_dynamodb_table.registry[table].arn]
    }
  }

  # Prefix-scoped ListBucket lets S3 answer absent keys with 404, which the
  # service relies on when probing for missing objects.
  dynamic "statement" {
    for_each = toset(distinct([for grant in local.runtimes[each.key].grants : local.bucket_for_grant[split(":", grant)[0]]]))

    content {
      sid       = "List${title(statement.key)}"
      actions   = ["s3:ListBucket"]
      resources = [local.bucket_arn[statement.key]]

      condition {
        test     = "StringLike"
        variable = "s3:prefix"
        values   = ["${local.object_prefix}/*"]
      }
    }
  }

  dynamic "statement" {
    for_each = toset([for grant in local.runtimes[each.key].grants : split(":", grant)[0] if split(":", grant)[1] == "r"])

    content {
      sid       = "Read${title(local.bucket_for_grant[statement.key])}"
      actions   = ["s3:GetObject"]
      resources = ["${local.bucket_arn[local.bucket_for_grant[statement.key]]}/${local.object_prefix}/*"]
    }
  }

  dynamic "statement" {
    for_each = toset([for grant in local.runtimes[each.key].grants : split(":", grant)[0] if split(":", grant)[1] == "put"])

    content {
      sid       = "Write${title(local.bucket_for_grant[statement.key])}"
      actions   = ["s3:PutObject"]
      resources = ["${local.bucket_arn[local.bucket_for_grant[statement.key]]}/${local.object_prefix}/*"]
    }
  }

  dynamic "statement" {
    for_each = contains(local.runtimes[each.key].grants, "q:del") ? [1] : []

    content {
      sid       = "DeletePromotedQuarantine"
      actions   = ["s3:DeleteObject"]
      resources = ["${local.bucket_arn.quarantine}/${local.object_prefix}/*"]
    }
  }

  dynamic "statement" {
    for_each = contains(local.runtimes[each.key].grants, "m:root") ? [1] : []

    content {
      sid       = "WriteRootPointer"
      actions   = ["s3:PutObject"]
      resources = [local.metadata_pointer_arns["root.json"]]
    }
  }

  dynamic "statement" {
    for_each = contains(local.runtimes[each.key].grants, "m:timestamp") ? [1] : []

    content {
      sid       = "WriteTimestampPointer"
      actions   = ["s3:PutObject"]
      resources = [local.metadata_pointer_arns["timestamp.json"]]
    }
  }

  # Versioned-metadata writers never move the pointers; only root importers
  # write root.json, and only the timestamp signer (or the one-shot copy)
  # writes timestamp.json.
  dynamic "statement" {
    for_each = {
      for denied in [concat(
        contains(local.runtimes[each.key].grants, "m:timestamp") ? [] : [local.metadata_pointer_arns["timestamp.json"]],
        contains(local.runtimes[each.key].grants, "m:root") ? [] : [local.metadata_pointer_arns["root.json"]],
      )] : "pointers" => denied
      if length(denied) > 0 && (contains(local.runtimes[each.key].grants, "m:put") || contains(local.runtimes[each.key].grants, "m:root"))
    }

    content {
      sid       = "DenyForeignPointerWrites"
      effect    = "Deny"
      actions   = ["s3:PutObject"]
      resources = statement.value
    }
  }

  statement {
    sid       = "NoSigning"
    effect    = "Deny"
    actions   = ["kms:Sign"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "runtime" {
  for_each = var.enabled ? toset(keys(local.runtimes)) : toset([])

  name   = "runtime"
  role   = aws_iam_role.runtime[each.key].id
  policy = data.aws_iam_policy_document.runtime[each.key].json
}

resource "aws_iam_role" "signer" {
  for_each = local.kms_ready ? toset(local.online_roles) : toset([])

  name               = "${local.name}-signer-${each.key}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json

  tags = { TufRole = each.key }
}

data "aws_iam_policy_document" "signer" {
  for_each = local.kms_ready ? toset(local.online_roles) : toset([])

  statement {
    sid       = "OwnKeyOnly"
    actions   = ["kms:Sign", "kms:GetPublicKey", "kms:DescribeKey"]
    resources = [aws_kms_key.signing[each.key].arn]
  }

  statement {
    sid       = "ListMetadata"
    actions   = ["s3:ListBucket"]
    resources = [local.bucket_arn.metadata]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${local.object_prefix}/*"]
    }
  }

  statement {
    sid       = "ReadMetadata"
    actions   = ["s3:GetObject"]
    resources = ["${local.bucket_arn.metadata}/${local.object_prefix}/*"]
  }

  dynamic "statement" {
    for_each = each.key == "timestamp" ? [1] : []

    content {
      sid       = "WriteTimestampPointer"
      actions   = ["s3:PutObject"]
      resources = [local.metadata_pointer_arns["timestamp.json"]]
    }
  }
}

resource "aws_iam_role_policy" "signer" {
  for_each = local.kms_ready ? toset(local.online_roles) : toset([])

  name   = "signer"
  role   = aws_iam_role.signer[each.key].id
  policy = data.aws_iam_policy_document.signer[each.key].json
}
