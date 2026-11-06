# The site on Lambda, so it costs nothing while idle. CloudFront reaches it
# through a function URL; the application rejects requests without the origin
# secret. It runs outside the VPC and needs no public IPv4 address.
locals {
  site_lambda         = local.ssr_enabled && var.site_lambda.enabled
  site_lambda_serving = local.site_lambda && var.site_lambda.serving
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

resource "aws_iam_role" "site_lambda" {
  count = local.site_lambda ? 1 : 0

  name               = "${local.name}-site-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_trust.json
}

resource "aws_iam_role_policy" "site_lambda" {
  count = local.site_lambda ? 1 : 0

  name   = "runtime"
  role   = aws_iam_role.site_lambda[0].id
  policy = data.aws_iam_policy_document.ssr_task[0].json
}

resource "aws_iam_role_policy_attachment" "site_lambda_logs" {
  count = local.site_lambda ? 1 : 0

  role       = aws_iam_role.site_lambda[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_cloudwatch_log_group" "site_lambda" {
  count = local.site_lambda ? 1 : 0

  name              = "/aws/lambda/${local.name}-site"
  retention_in_days = 30
}

resource "aws_lambda_function" "site" {
  count = local.site_lambda ? 1 : 0

  function_name = "${local.name}-site"
  package_type  = "Image"
  image_uri     = var.images.ssr
  role          = aws_iam_role.site_lambda[0].arn
  architectures = ["x86_64"]
  memory_size   = 2048
  timeout       = 30

  environment {
    variables = merge(
      # Lambda reserves AWS_REGION. The SQS consumer needs a long-running
      # process, and only development FinOps work uses it.
      {
        for key, value in local.ssr_environment : key => value
        if !contains(["AWS_REGION", "PORTFOLIO_SQS_JOBS_QUEUE_URL"], key)
      },
      {
        JAVA_TOOL_OPTIONS            = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
        PORTFOLIO_EDGE_ORIGIN_SECRET = var.site_lambda_origin_secret
      },
    )
  }

  depends_on = [
    aws_cloudwatch_log_group.site_lambda,
    aws_iam_role_policy.site_lambda,
    aws_iam_role_policy_attachment.site_lambda_logs,
  ]
}

resource "aws_lambda_function_url" "site" {
  count = local.site_lambda ? 1 : 0

  function_name      = aws_lambda_function.site[0].function_name
  authorization_type = "NONE"
}

resource "aws_lambda_permission" "site_url" {
  count = local.site_lambda ? 1 : 0

  statement_id           = "PublicFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.site[0].function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

resource "aws_lambda_permission" "site_invoke" {
  count = local.site_lambda ? 1 : 0

  statement_id             = "PublicFunctionUrlInvoke"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.site[0].function_name
  principal                = "*"
  invoked_via_function_url = true
}

# Replaces the load balancer's path rules: hides /internal/*, sends project
# paths to their subdomain site, and carries the viewer host to the origin.
resource "aws_cloudfront_function" "site_request" {
  count = local.site_lambda ? 1 : 0

  name    = "${local.name}-site-request"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = <<-EOT
    function handler(event) {
      var request = event.request;
      var host = request.headers.host.value;
      if (request.uri.indexOf("${local.site_check_path}") === 0) {
        request.uri = request.uri.slice(${length(local.site_check_path)}) || "/";
      }
      if (request.uri.indexOf("/internal/") === 0) {
        return { statusCode: 404, statusDescription: "Not Found" };
      }
      var project = request.uri.match(/^\/(${join("|", local.ecosystem_projects)})(?:\/(.*))?$/);
      if (project) {
        host = project[1] + ".${local.host_suffix}";
        request.uri = "/" + (project[2] || "");
      }
      request.headers["x-viewer-host"] = { value: host };
      return request;
    }
  EOT
}

output "site_lambda_url" {
  description = "Function URL of the site, when it exists."
  value       = local.site_lambda ? aws_lambda_function_url.site[0].function_url : null
}
