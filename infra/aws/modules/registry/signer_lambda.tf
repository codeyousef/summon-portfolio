# Signers on Lambda, so they cost nothing between signing runs. They keep their
# existing roles, so the KMS key policies do not change. The function URL takes
# no IAM credentials: the presigned STS caller token and its role binding remain
# the authorization boundary, as they are behind the internal load balancer.
locals {
  signer_lambda         = local.signers_ready && var.signer_lambda_enabled
  signer_lambda_serving = local.signer_lambda && var.signer_lambda_serving

  signer_environment = {
    for role in local.signers_ready ? local.online_roles : [] : role => {
      PORT                                       = "8080"
      REGISTRY_ENVIRONMENT                       = local.runtime_env
      REGISTRY_REPOSITORY_ID                     = local.identity_environment.REGISTRY_REPOSITORY_ID
      REGISTRY_TUF_SIGNER_PROVIDER               = "aws-kms"
      REGISTRY_TUF_SIGNER_SERVICE_NAME           = "seen-tuf-${role}-${var.environment}"
      REGISTRY_TUF_SIGNER_ROLE                   = role
      "REGISTRY_AWS_KMS_${upper(role)}_KEY_ARN"  = aws_kms_key.signing[role].arn
      REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX         = var.online_public_keys_hex[role]
      REGISTRY_TUF_SIGNER_OBJECT_STORE_PROVIDER  = "s3"
      REGISTRY_TUF_SIGNER_METADATA_BUCKET        = local.bucket_names.metadata
      REGISTRY_TUF_SIGNER_OBJECT_PREFIX          = local.object_prefix
      REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256 = var.trusted_root_v1_sha256
      REGISTRY_TUF_SIGNER_AUDIENCE               = local.signer_audiences[role]
      REGISTRY_TUF_SIGNER_CALLER_BINDINGS        = local.signer_caller_bindings[role]
    }
  }
}

resource "aws_iam_role_policy_attachment" "signer_lambda_logs" {
  for_each = local.signer_lambda ? toset(local.online_roles) : toset([])

  role       = aws_iam_role.signer[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_cloudwatch_log_group" "signer_lambda" {
  for_each = local.signer_lambda ? toset(local.online_roles) : toset([])

  name              = "/aws/lambda/${local.name}-signer-${each.key}"
  retention_in_days = 30
}

resource "aws_lambda_function" "signer" {
  for_each = local.signer_lambda ? toset(local.online_roles) : toset([])

  function_name = "${local.name}-signer-${each.key}"
  package_type  = "Image"
  image_uri     = var.signer_image
  role          = aws_iam_role.signer[each.key].arn
  architectures = ["x86_64"]
  memory_size   = 1024
  timeout       = 30

  image_config {
    command = ["serve-tuf-signer"]
  }

  environment {
    variables = merge(local.signer_environment[each.key], {
      AWS_LWA_READINESS_CHECK_PATH = "/healthz"
      JAVA_TOOL_OPTIONS            = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
    })
  }

  depends_on = [
    aws_cloudwatch_log_group.signer_lambda,
    aws_iam_role_policy_attachment.signer_lambda_logs,
  ]
}

resource "aws_lambda_function_url" "signer" {
  for_each = local.signer_lambda ? toset(local.online_roles) : toset([])

  function_name      = aws_lambda_function.signer[each.key].function_name
  authorization_type = "NONE"
}

resource "aws_lambda_permission" "signer_url" {
  for_each = local.signer_lambda ? toset(local.online_roles) : toset([])

  statement_id           = "PublicFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.signer[each.key].function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

resource "aws_lambda_permission" "signer_invoke" {
  for_each = local.signer_lambda ? toset(local.online_roles) : toset([])

  statement_id             = "PublicFunctionUrlInvoke"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.signer[each.key].function_name
  principal                = "*"
  invoked_via_function_url = true
}
