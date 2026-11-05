resource "aws_dynamodb_table" "registry" {
  for_each = var.enabled ? local.tables : {}

  name                        = local.table_names[each.key]
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "pk"
  range_key                   = each.value.range_key
  deletion_protection_enabled = true

  attribute {
    name = "pk"
    type = "S"
  }

  dynamic "attribute" {
    for_each = each.value.range_key == null ? [] : [each.value.range_key]

    content {
      name = attribute.value
      type = "S"
    }
  }

  dynamic "ttl" {
    for_each = each.value.ttl == null ? [] : [each.value.ttl]

    content {
      attribute_name = ttl.value
      enabled        = true
    }
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket" "registry" {
  for_each = var.enabled ? toset(local.bucket_roles) : toset([])

  bucket = local.bucket_names[each.key]

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_ownership_controls" "registry" {
  for_each = var.enabled ? toset(local.bucket_roles) : toset([])

  bucket = aws_s3_bucket.registry[each.key].id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "registry" {
  for_each = var.enabled ? toset(local.bucket_roles) : toset([])

  bucket                  = aws_s3_bucket.registry[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "registry" {
  for_each = var.enabled ? toset(local.bucket_roles) : toset([])

  bucket = aws_s3_bucket.registry[each.key].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Versioning makes operator overwrites and deletes recoverable; quarantine is
# transient upload staging and stays unversioned.
resource "aws_s3_bucket_versioning" "registry" {
  for_each = var.enabled ? toset([for role in local.bucket_roles : role if role != "quarantine"]) : toset([])

  bucket = aws_s3_bucket.registry[each.key].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "quarantine" {
  count = var.enabled ? 1 : 0

  bucket = aws_s3_bucket.registry["quarantine"].id

  rule {
    id     = "abort-incomplete-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

data "aws_iam_policy_document" "registry_bucket" {
  for_each = var.enabled ? toset(local.bucket_roles) : toset([])

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.registry[each.key].arn,
      "${aws_s3_bucket.registry[each.key].arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  # Archives are content-addressed and create-only.
  dynamic "statement" {
    for_each = each.key == "public" ? [1] : []

    content {
      sid       = "DenyUnconditionalArchiveWrites"
      effect    = "Deny"
      actions   = ["s3:PutObject"]
      resources = ["${aws_s3_bucket.registry[each.key].arn}/*"]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "Null"
        variable = "s3:if-none-match"
        values   = ["true"]
      }
    }
  }

  # Versioned metadata is create-only; root.json and timestamp.json are
  # compare-and-set pointers.
  dynamic "statement" {
    for_each = each.key == "metadata" ? [1] : []

    content {
      sid     = "DenyUnconditionalVersionedMetadataWrites"
      effect  = "Deny"
      actions = ["s3:PutObject"]
      not_resources = [
        for pointer in local.metadata_pointers : "${aws_s3_bucket.registry[each.key].arn}/${local.object_prefix}/metadata/${pointer}"
      ]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "Null"
        variable = "s3:if-none-match"
        values   = ["true"]
      }
    }
  }

  dynamic "statement" {
    for_each = each.key == "metadata" ? [1] : []

    content {
      sid     = "DenyUnconditionalPointerWrites"
      effect  = "Deny"
      actions = ["s3:PutObject"]
      resources = [
        for pointer in local.metadata_pointers : "${aws_s3_bucket.registry[each.key].arn}/${local.object_prefix}/metadata/${pointer}"
      ]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "Null"
        variable = "s3:if-match"
        values   = ["true"]
      }

      condition {
        test     = "Null"
        variable = "s3:if-none-match"
        values   = ["true"]
      }
    }
  }
}

resource "aws_s3_bucket_policy" "registry" {
  for_each = var.enabled ? toset(local.bucket_roles) : toset([])

  bucket = aws_s3_bucket.registry[each.key].id
  policy = data.aws_iam_policy_document.registry_bucket[each.key].json

  depends_on = [aws_s3_bucket_public_access_block.registry]
}

# Key administrators (the account principal) manage keys but cannot sign;
# because the key policy never grants kms:Sign to the account, no IAM policy
# can extend signing beyond the role-locked signer task role.
data "aws_iam_policy_document" "signing_key" {
  for_each = local.kms_ready ? toset(local.online_roles) : toset([])

  statement {
    sid = "KeyAdministration"
    actions = [
      "kms:CancelKeyDeletion",
      "kms:CreateAlias",
      "kms:DeleteAlias",
      "kms:DescribeKey",
      "kms:DisableKey",
      "kms:EnableKey",
      "kms:GetKeyPolicy",
      "kms:GetKeyRotationStatus",
      "kms:GetPublicKey",
      "kms:ListAliases",
      "kms:ListResourceTags",
      "kms:PutKeyPolicy",
      "kms:ScheduleKeyDeletion",
      "kms:TagResource",
      "kms:UntagResource",
      "kms:UpdateAlias",
      "kms:UpdateKeyDescription",
    ]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${var.aws_account_id}:root"]
    }
  }

  statement {
    sid       = "RoleLockedSigner"
    actions   = ["kms:Sign"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.signer[each.key].arn]
    }

    condition {
      test     = "StringEquals"
      variable = "kms:SigningAlgorithm"
      values   = ["ED25519_SHA_512"]
    }

    condition {
      test     = "StringEquals"
      variable = "kms:MessageType"
      values   = ["RAW"]
    }
  }

  statement {
    sid       = "SignerPublicKey"
    actions   = ["kms:GetPublicKey", "kms:DescribeKey"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.signer[each.key].arn]
    }
  }
}

resource "aws_kms_key" "signing" {
  for_each = local.kms_ready ? toset(local.online_roles) : toset([])

  description              = "${local.prefix} ${each.key} online TUF signing key"
  customer_master_key_spec = "ECC_NIST_EDWARDS25519"
  key_usage                = "SIGN_VERIFY"
  multi_region             = false
  deletion_window_in_days  = 30
  policy                   = data.aws_iam_policy_document.signing_key[each.key].json

  tags = { TufRole = each.key }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_kms_alias" "signing" {
  for_each = aws_kms_key.signing

  name          = "alias/${local.prefix}-${each.key}"
  target_key_id = each.value.key_id
}

resource "aws_secretsmanager_secret" "registry" {
  for_each = var.enabled ? local.runtime_secret_names : toset([])

  name                    = "/${local.name}/registry/${each.key}"
  recovery_window_in_days = 7
}
