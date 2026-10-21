resource "aws_ecs_cluster" "main" {
  count = local.enabled

  name = local.name

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  count = local.enabled

  cluster_name       = aws_ecs_cluster.main[0].name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = var.use_fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }
}

resource "aws_cloudwatch_log_group" "services" {
  for_each = var.resources_enabled ? toset(["ssr", "playground"]) : toset([])

  name              = "/${local.name}/${each.key}"
  retention_in_days = var.log_retention_days
}

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

# Routine application roles are named portfolio-<env>-app-*; the routine
# deploy identity may pass only these. Signer roles live in registry stacks
# under portfolio-<env>-signer-* and are passed only by the signer identity.
resource "aws_iam_role" "app_execution" {
  count = local.enabled

  name               = "${local.name}-app-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy_attachment" "app_execution" {
  count = local.enabled

  role       = aws_iam_role.app_execution[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "app_execution_secrets" {
  count = var.resources_enabled && length(var.app_secret_names) > 0 ? 1 : 0

  name = "read-app-secrets"
  role = aws_iam_role.app_execution[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = [for secret in aws_secretsmanager_secret.app : secret.arn]
    }]
  })
}

resource "aws_iam_role" "ssr_task" {
  count = local.enabled

  name               = "${local.name}-app-ssr-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

data "aws_iam_policy_document" "ssr_task" {
  count = local.enabled

  statement {
    sid = "AppObjects"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = [
      for purpose in ["media", "docs", "receipts"] : "${aws_s3_bucket.app[purpose].arn}/*"
    ]
  }

  statement {
    sid       = "ListAppBuckets"
    actions   = ["s3:ListBucket"]
    resources = [for purpose in ["media", "docs", "receipts"] : aws_s3_bucket.app[purpose].arn]
  }

  statement {
    sid = "Jobs"
    actions = [
      "sqs:ChangeMessageVisibility",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:ReceiveMessage",
      "sqs:SendMessage",
    ]
    resources = [aws_sqs_queue.jobs[0].arn]
  }

  statement {
    sid = "AppTables"
    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:BatchWriteItem",
      "dynamodb:ConditionCheckItem",
      "dynamodb:DeleteItem",
      "dynamodb:DescribeTable",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:Query",
      "dynamodb:Scan",
      "dynamodb:TransactWriteItems",
      "dynamodb:UpdateItem",
    ]
    resources = flatten([
      for table in aws_dynamodb_table.app : [table.arn, "${table.arn}/index/*"]
    ])
  }

  # Keyless GCP access for the BigQuery billing export: the task's AWS
  # identity is federated into the GCP workload identity pool.
  statement {
    sid       = "GcpFederationIdentity"
    actions   = ["sts:GetCallerIdentity"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "ssr_task" {
  count = local.enabled

  name   = "runtime"
  role   = aws_iam_role.ssr_task[0].id
  policy = data.aws_iam_policy_document.ssr_task[0].json
}

# The playground runs untrusted programs and has no AWS permissions at all.
resource "aws_iam_role" "playground_task" {
  count = local.playground_enabled ? 1 : 0

  name               = "${local.name}-app-playground-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

locals {
  ssr_environment = merge(
    {
      PORT                               = "8080"
      ENVIRONMENT                        = var.environment
      AWS_REGION                         = var.region
      PORTFOLIO_DATA_BACKEND             = "dynamodb"
      PORTFOLIO_SESSION_BACKEND          = "dynamodb"
      PORTFOLIO_DYNAMODB_DOCUMENTS_TABLE = local.table_names.documents
      PORTFOLIO_DYNAMODB_SESSIONS_TABLE  = local.table_names.sessions
      PORTFOLIO_S3_MEDIA_BUCKET          = local.bucket_names.media
      PORTFOLIO_S3_DOCS_BUCKET           = local.bucket_names.docs
      PORTFOLIO_S3_RECEIPTS_BUCKET       = local.bucket_names.receipts
      PORTFOLIO_SQS_JOBS_QUEUE_URL       = var.resources_enabled ? aws_sqs_queue.jobs[0].url : ""
      PORTFOLIO_ENV                      = var.environment
      PHOTOGRAPHY_WRITE_MODE             = "target"
      PORTFOLIO_CDN_PROXY_ENABLED        = tostring(local.is_dev)
      FIRESTORE_SEED_ON_START            = "false"
    },
    var.app_environment,
  )
}

resource "aws_ecs_task_definition" "ssr" {
  count = local.ssr_enabled ? 1 : 0

  family                   = "${local.name}-app-ssr"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.service_sizes.ssr_cpu
  memory                   = var.service_sizes.ssr_memory
  execution_role_arn       = aws_iam_role.app_execution[0].arn
  task_role_arn            = aws_iam_role.ssr_task[0].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "ssr"
    image        = var.images.ssr
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment  = [for name in sort(keys(local.ssr_environment)) : { name = name, value = local.ssr_environment[name] }]
    secrets = [
      for name in sort(tolist(var.app_secret_names)) : {
        name      = name
        valueFrom = aws_secretsmanager_secret.app[name].arn
      }
    ]
    readonlyRootFilesystem = false
    stopTimeout            = 30
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.services["ssr"].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ssr"
      }
    }
  }])
}

resource "aws_ecs_service" "ssr" {
  count = local.ssr_enabled ? 1 : 0

  name                              = "app-ssr"
  cluster                           = aws_ecs_cluster.main[0].id
  task_definition                   = aws_ecs_task_definition.ssr[0].arn
  desired_count                     = var.service_sizes.ssr_desired_count
  health_check_grace_period_seconds = 120
  enable_execute_command            = false
  propagate_tags                    = "SERVICE"

  capacity_provider_strategy {
    capacity_provider = var.use_fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets          = [for subnet in aws_subnet.public : subnet.id]
    security_groups  = [aws_security_group.ssr[0].id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.ssr[0].arn
    container_name   = "ssr"
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Image rollouts register new revisions through the protected deploy
  # identity; OpenTofu owns only the initial revision and the service shape.
  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.https]
}

resource "aws_ecs_task_definition" "playground" {
  count = local.playground_enabled ? 1 : 0

  family                   = "${local.name}-app-playground"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.service_sizes.playground_cpu
  memory                   = var.service_sizes.playground_memory
  execution_role_arn       = aws_iam_role.app_execution[0].arn
  task_role_arn            = aws_iam_role.playground_task[0].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "playground"
    image        = var.images.playground
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      { name = "ENVIRONMENT", value = var.environment },
      { name = "PORT", value = "8080" },
      { name = "USE_LOCAL_STORE", value = "true" },
    ]
    stopTimeout = 15
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.services["playground"].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "playground"
      }
    }
  }])
}

resource "aws_ecs_service" "playground" {
  count = local.playground_enabled ? 1 : 0

  name                              = "app-playground"
  cluster                           = aws_ecs_cluster.main[0].id
  task_definition                   = aws_ecs_task_definition.playground[0].arn
  desired_count                     = var.service_sizes.playground_desired_count
  health_check_grace_period_seconds = 180
  enable_execute_command            = false
  propagate_tags                    = "SERVICE"

  capacity_provider_strategy {
    capacity_provider = var.use_fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets          = [for subnet in aws_subnet.isolated : subnet.id]
    security_groups  = [aws_security_group.playground[0].id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.playground[0].arn
    container_name   = "playground"
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.https, aws_vpc_endpoint.interface]
}
