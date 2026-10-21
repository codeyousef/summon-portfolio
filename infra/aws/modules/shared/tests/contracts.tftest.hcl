mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

variables {
  aws_account_id = "123456789012"
}

run "inert_by_default" {
  command = plan

  assert {
    condition     = output.zone == null && length(aws_iam_role.ci) == 0
    error_message = "The shared foundation must not create resources by default."
  }
}

run "separate_ci_identities" {
  command = plan

  variables {
    resources_enabled = true
  }

  assert {
    condition     = length(aws_iam_role.ci) == 8
    error_message = "Each environment needs separate plan, apply, routine deploy, and signer deploy identities."
  }

  assert {
    condition = (
      output.ci_roles["prod-plan"].github_environment == "portfolio-aws-prod-plan" &&
      output.ci_roles["prod-apply"].github_environment == "portfolio-aws-prod-apply" &&
      output.ci_roles["prod-deploy"].github_environment == "portfolio-production" &&
      output.ci_roles["prod-signer-deploy"].github_environment == "portfolio-aws-prod-signer"
    )
    error_message = "Production plan, apply, deploy, and signer identities must map to distinct protected environments."
  }

  assert {
    condition     = length(aws_iam_role_policy_attachment.ci_apply_administrator) == 2 && length(aws_iam_role_policy_attachment.ci_plan_read_only) == 2
    error_message = "Only apply identities receive write access; plan identities stay read-only."
  }

  assert {
    condition     = aws_ecr_repository.images["seen-registry-signer"].image_tag_mutability == "IMMUTABLE"
    error_message = "Signer images must be immutable."
  }
}
