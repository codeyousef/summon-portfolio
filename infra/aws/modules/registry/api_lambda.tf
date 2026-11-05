# The public read-only API on Lambda, so it costs nothing while idle. The data
# it serves is public; the function URL needs no credentials.
locals {
  api_lambda         = local.routine_ready && !local.is_dev && var.api_lambda_enabled
  api_lambda_serving = local.api_lambda && var.api_lambda_serving
}

data "aws_iam_policy_document" "lambda_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "api_lambda" {
  count = local.api_lambda ? 1 : 0

  name               = "${local.name}-app-registry-api-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_trust.json
}

resource "aws_iam_role_policy" "api_lambda" {
  count = local.api_lambda ? 1 : 0

  name   = "runtime"
  role   = aws_iam_role.api_lambda[0].id
  policy = data.aws_iam_policy_document.runtime["read-only-api"].json
}

resource "aws_iam_role_policy_attachment" "api_lambda_logs" {
  count = local.api_lambda ? 1 : 0

  role       = aws_iam_role.api_lambda[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_cloudwatch_log_group" "api_lambda" {
  count = local.api_lambda ? 1 : 0

  name              = "/aws/lambda/${local.name}-registry-api"
  retention_in_days = 30
}

resource "aws_lambda_function" "api" {
  count = local.api_lambda ? 1 : 0

  function_name = "${local.name}-registry-api"
  package_type  = "Image"
  image_uri     = var.image
  role          = aws_iam_role.api_lambda[0].arn
  architectures = ["x86_64"]
  memory_size   = 2048
  timeout       = 30

  image_config {
    command = local.runtimes["read-only-api"].command
  }

  environment {
    variables = merge(
      { for key, value in local.runtime_environment["read-only-api"] : key => value if key != "AWS_REGION" },
      { JAVA_TOOL_OPTIONS = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError" },
    )
  }

  depends_on = [
    aws_cloudwatch_log_group.api_lambda,
    aws_iam_role_policy.api_lambda,
    aws_iam_role_policy_attachment.api_lambda_logs,
  ]
}

resource "aws_lambda_function_url" "api" {
  count = local.api_lambda ? 1 : 0

  function_name      = aws_lambda_function.api[0].function_name
  authorization_type = "NONE"
}

resource "aws_lambda_permission" "api_url" {
  count = local.api_lambda ? 1 : 0

  statement_id           = "PublicFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.api[0].function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

resource "aws_lambda_permission" "api_invoke" {
  count = local.api_lambda ? 1 : 0

  statement_id             = "PublicFunctionUrlInvoke"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.api[0].function_name
  principal                = "*"
  invoked_via_function_url = true
}
