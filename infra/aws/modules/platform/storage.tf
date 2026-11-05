resource "aws_s3_bucket" "app" {
  for_each = var.resources_enabled ? local.buckets : {}

  bucket = local.bucket_names[each.key]

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_ownership_controls" "app" {
  for_each = var.resources_enabled ? local.buckets : {}

  bucket = aws_s3_bucket.app[each.key].id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "app" {
  for_each = var.resources_enabled ? local.buckets : {}

  bucket                  = aws_s3_bucket.app[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "app" {
  for_each = var.resources_enabled ? local.buckets : {}

  bucket = aws_s3_bucket.app[each.key].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_versioning" "app" {
  for_each = { for purpose, spec in local.buckets : purpose => spec if var.resources_enabled && spec.versioned }

  bucket = aws_s3_bucket.app[each.key].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "app" {
  for_each = var.resources_enabled ? local.buckets : {}

  bucket = aws_s3_bucket.app[each.key].id

  rule {
    id     = "housekeeping"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }

    dynamic "noncurrent_version_expiration" {
      for_each = local.buckets[each.key].versioned ? [1] : []

      content {
        noncurrent_days = 90
      }
    }

    dynamic "expiration" {
      for_each = each.key == "logs" ? [1] : []

      content {
        days = 90
      }
    }
  }
}

data "aws_iam_policy_document" "app_bucket" {
  for_each = var.resources_enabled ? local.buckets : {}

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.app[each.key].arn,
      "${aws_s3_bucket.app[each.key].arn}/*",
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

  dynamic "statement" {
    for_each = contains(keys(local.cdn_buckets), each.key) ? [1] : []

    content {
      sid       = "CloudFrontReadOnly"
      actions   = ["s3:GetObject"]
      resources = ["${aws_s3_bucket.app[each.key].arn}/*"]

      principals {
        type        = "Service"
        identifiers = ["cloudfront.amazonaws.com"]
      }

      condition {
        test     = "StringEquals"
        variable = "AWS:SourceArn"
        values   = [aws_cloudfront_distribution.site[0].arn]
      }
    }
  }
}

resource "aws_s3_bucket_policy" "app" {
  for_each = var.resources_enabled ? local.buckets : {}

  bucket = aws_s3_bucket.app[each.key].id
  policy = data.aws_iam_policy_document.app_bucket[each.key].json

  depends_on = [aws_s3_bucket_public_access_block.app]
}

resource "aws_sqs_queue" "jobs_dlq" {
  count = local.enabled

  name                      = "${local.name}-jobs-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "jobs" {
  count = local.enabled

  name                       = "${local.name}-jobs"
  visibility_timeout_seconds = 900
  message_retention_seconds  = 345600
  receive_wait_time_seconds  = 20
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.jobs_dlq[0].arn
    maxReceiveCount     = 5
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "jobs_dlq" {
  count = local.enabled

  queue_url = aws_sqs_queue.jobs_dlq[0].id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.jobs[0].arn]
  })
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

resource "aws_iam_role" "scheduler" {
  count = local.enabled

  name               = "${local.name}-scheduler"
  assume_role_policy = data.aws_iam_policy_document.scheduler_trust.json
}

resource "aws_iam_role_policy" "scheduler" {
  count = local.enabled

  name = "send-jobs"
  role = aws_iam_role.scheduler[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "sqs:SendMessage"
      Resource = aws_sqs_queue.jobs[0].arn
    }]
  })
}

resource "aws_scheduler_schedule_group" "jobs" {
  count = local.enabled

  name = local.name
}

resource "aws_scheduler_schedule" "jobs" {
  for_each = var.resources_enabled ? local.schedules : {}

  name                         = each.key
  group_name                   = aws_scheduler_schedule_group.jobs[0].name
  schedule_expression          = each.value.cron
  schedule_expression_timezone = "UTC"
  state                        = var.schedules_enabled ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_sqs_queue.jobs[0].arn
    role_arn = aws_iam_role.scheduler[0].arn
    input = jsonencode({
      job         = each.value.job
      scheduledAt = "<aws.scheduler.scheduled-time>"
    })

    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 3
    }
  }
}

resource "aws_secretsmanager_secret" "app" {
  for_each = var.resources_enabled ? var.app_secret_names : toset([])

  name                    = "/${local.name}/app/${each.key}"
  recovery_window_in_days = 7
}
