mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/mock"
    }
  }

  mock_resource "aws_sqs_queue" {
    defaults = {
      arn = "arn:aws:sqs:eu-north-1:123456789012:mock"
    }
  }

  mock_resource "aws_s3_bucket" {
    defaults = {
      arn = "arn:aws:s3:::mock"
    }
  }

  mock_resource "aws_cloudfront_distribution" {
    defaults = {
      arn = "arn:aws:cloudfront::123456789012:distribution/MOCK"
    }
  }

  mock_resource "aws_cloudfront_function" {
    defaults = {
      arn = "arn:aws:cloudfront::123456789012:function/mock"
    }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:loadbalancer/app/mock/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:targetgroup/mock/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_listener" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:listener/app/mock/0123456789abcdef/0123456789abcdef"
    }
  }

  mock_resource "aws_cognito_user_pool" {
    defaults = {
      arn = "arn:aws:cognito-idp:eu-north-1:123456789012:userpool/eu-north-1_mock"
    }
  }

  mock_resource "aws_acm_certificate" {
    defaults = {
      arn = "arn:aws:acm:eu-north-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
    }
  }

  mock_resource "aws_secretsmanager_secret" {
    defaults = {
      arn = "arn:aws:secretsmanager:eu-north-1:123456789012:secret:mock"
    }
  }

  mock_resource "aws_dynamodb_table" {
    defaults = {
      arn = "arn:aws:dynamodb:eu-north-1:123456789012:table/mock"
    }
  }

  mock_resource "aws_ecs_cluster" {
    defaults = {
      arn = "arn:aws:ecs:eu-north-1:123456789012:cluster/mock"
    }
  }

  mock_resource "aws_ecs_task_definition" {
    defaults = {
      arn = "arn:aws:ecs:eu-north-1:123456789012:task-definition/mock:1"
    }
  }
}

mock_provider "aws" {
  alias = "us_east_1"

  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/mock"
    }
  }

  mock_resource "aws_sqs_queue" {
    defaults = {
      arn = "arn:aws:sqs:eu-north-1:123456789012:mock"
    }
  }

  mock_resource "aws_s3_bucket" {
    defaults = {
      arn = "arn:aws:s3:::mock"
    }
  }

  mock_resource "aws_cloudfront_distribution" {
    defaults = {
      arn = "arn:aws:cloudfront::123456789012:distribution/MOCK"
    }
  }

  mock_resource "aws_cloudfront_function" {
    defaults = {
      arn = "arn:aws:cloudfront::123456789012:function/mock"
    }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:loadbalancer/app/mock/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:targetgroup/mock/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_listener" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:listener/app/mock/0123456789abcdef/0123456789abcdef"
    }
  }

  mock_resource "aws_cognito_user_pool" {
    defaults = {
      arn = "arn:aws:cognito-idp:eu-north-1:123456789012:userpool/eu-north-1_mock"
    }
  }

  mock_resource "aws_acm_certificate" {
    defaults = {
      arn = "arn:aws:acm:eu-north-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
    }
  }

  mock_resource "aws_secretsmanager_secret" {
    defaults = {
      arn = "arn:aws:secretsmanager:eu-north-1:123456789012:secret:mock"
    }
  }

  mock_resource "aws_dynamodb_table" {
    defaults = {
      arn = "arn:aws:dynamodb:eu-north-1:123456789012:table/mock"
    }
  }

  mock_resource "aws_ecs_cluster" {
    defaults = {
      arn = "arn:aws:ecs:eu-north-1:123456789012:cluster/mock"
    }
  }

  mock_resource "aws_ecs_task_definition" {
    defaults = {
      arn = "arn:aws:ecs:eu-north-1:123456789012:task-definition/mock:1"
    }
  }
}

variables {
  aws_account_id = "123456789012"
}

run "development_is_inert_by_default" {
  command = plan

  variables {
    environment = "dev"
  }

  assert {
    condition     = output.resources_enabled == false && output.cluster == null
    error_message = "Development must not create AWS resources by default."
  }

  assert {
    condition     = length(aws_vpc.main) == 0 && length(aws_cloudfront_distribution.site) == 0
    error_message = "No network or edge resources may exist while the gate is closed."
  }
}

run "development_is_gated_and_complete" {
  command = plan

  variables {
    environment          = "dev"
    resources_enabled    = true
    dev_gate_owner_email = "owner@example.com"
    use_fargate_spot     = true
    images = {
      ssr        = "123456789012.dkr.ecr.eu-north-1.amazonaws.com/portfolio-ssr@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      playground = "123456789012.dkr.ecr.eu-north-1.amazonaws.com/seen-playground@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
  }

  assert {
    condition = toset(values(output.hosts)) == toset([
      "dev.yousef.codes",
      "summon.dev.yousef.codes",
      "materia.dev.yousef.codes",
      "sigil.dev.yousef.codes",
      "aether.dev.yousef.codes",
      "seen.dev.yousef.codes",
      "building.dev.yousef.codes",
    ])
    error_message = "Dev must serve exactly the Kotlin host map's dev hosts."
  }

  assert {
    condition     = output.dev_gate_enabled && length(aws_cognito_user_pool.dev_gate) == 1
    error_message = "Every dev host must sit behind the owner-only Cognito gate."
  }

  assert {
    condition     = aws_cognito_user_pool.dev_gate[0].mfa_configuration == "ON"
    error_message = "The dev gate must require MFA."
  }

  assert {
    condition     = alltrue([for bucket in values(output.buckets) : !bucket.served_via_cdn])
    error_message = "Dev media must not bypass the gate through CloudFront S3 origins."
  }

  assert {
    condition     = alltrue([for name, schedule in output.schedules : schedule.state == "DISABLED"])
    error_message = "Schedules must be created disabled."
  }

  assert {
    condition     = aws_ecs_service.playground[0].network_configuration[0].assign_public_ip == false
    error_message = "The Seen playground must not receive internet egress."
  }

  assert {
    condition     = length(aws_vpc_endpoint.interface) == 3
    error_message = "The isolated playground needs ECR and Logs interface endpoints."
  }

  assert {
    condition     = length(aws_lb_listener_rule.rewrite) == 6
    error_message = "Seen and the four ecosystem rewrite routes must be reproduced."
  }
}

run "production_shape" {
  command = plan

  variables {
    environment       = "prod"
    resources_enabled = true
    images = {
      ssr = "123456789012.dkr.ecr.eu-north-1.amazonaws.com/portfolio-ssr@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  assert {
    condition = (
      output.hosts.apex == "yousef.codes" &&
      output.hosts.main == "www.yousef.codes" &&
      output.hosts.docs == "docs.yousef.codes" &&
      length(output.hosts) == 9
    )
    error_message = "Production must serve the apex, www, docs, and six ecosystem hosts."
  }

  assert {
    condition     = !output.dev_gate_enabled && length(aws_cognito_user_pool.dev_gate) == 0
    error_message = "Production must not create the dev gate."
  }

  assert {
    condition     = output.buckets.media.served_via_cdn && output.buckets.docs.served_via_cdn && !output.buckets.receipts.served_via_cdn
    error_message = "Only media and docs are public through CloudFront; receipts stay private."
  }

  assert {
    condition     = length(aws_lb_listener_rule.rewrite) == 4
    error_message = "Without a playground image the Seen routes fall through to SSR."
  }

  assert {
    condition     = aws_lb.main[0].enable_deletion_protection
    error_message = "The production ALB must be deletion protected."
  }
}

run "rejects_mutable_image_tags" {
  command = plan

  variables {
    environment       = "prod"
    resources_enabled = true
    images = {
      ssr = "123456789012.dkr.ecr.eu-north-1.amazonaws.com/portfolio-ssr:latest"
    }
  }

  expect_failures = [var.images]
}

run "rejects_other_regions" {
  command = plan

  variables {
    environment = "prod"
    region      = "us-west-2"
  }

  expect_failures = [var.region]
}
