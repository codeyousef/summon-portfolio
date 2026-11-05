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

  mock_resource "aws_kms_key" {
    defaults = {
      arn = "arn:aws:kms:eu-north-1:123456789012:key/00000000-0000-0000-0000-000000000000"
    }
  }

  mock_resource "aws_dynamodb_table" {
    defaults = {
      arn = "arn:aws:dynamodb:eu-north-1:123456789012:table/mock"
    }
  }

  mock_resource "aws_secretsmanager_secret" {
    defaults = {
      arn = "arn:aws:secretsmanager:eu-north-1:123456789012:secret:mock"
    }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:loadbalancer/app/mock/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_listener" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:listener/app/mock/0123456789abcdef/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:targetgroup/mock/0123456789abcdef"
    }
  }

  mock_resource "aws_acm_certificate" {
    defaults = {
      arn = "arn:aws:acm:eu-north-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
    }
  }

  mock_resource "aws_ecs_task_definition" {
    defaults = {
      arn      = "arn:aws:ecs:eu-north-1:123456789012:task-definition/mock:1"
      revision = 1
    }
  }
}

variables {
  aws_account_id = "123456789012"
  network = {
    vpc_id                 = "vpc-0123456789abcdef0"
    public_subnet_ids      = ["subnet-0123456789abcdef0", "subnet-0123456789abcdef1"]
    isolated_subnet_ids    = ["subnet-0123456789abcdef2", "subnet-0123456789abcdef3"]
    cluster_arn            = "arn:aws:ecs:eu-north-1:123456789012:cluster/portfolio-dev"
    zone_id                = "Z0000000000000000000"
    host_suffix            = "dev.yousef.codes"
    gateway_security_group = "sg-0123456789abcdef0"
  }
  online_public_keys_hex = {
    releases  = "1111111111111111111111111111111111111111111111111111111111111111"
    security  = "2222222222222222222222222222222222222222222222222222222222222222"
    snapshot  = "3333333333333333333333333333333333333333333333333333333333333333"
    timestamp = "4444444444444444444444444444444444444444444444444444444444444444"
  }
  image                  = "123456789012.dkr.ecr.eu-north-1.amazonaws.com/seen-registry@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  signer_image           = "123456789012.dkr.ecr.eu-north-1.amazonaws.com/seen-registry-signer@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  trusted_root_v1_sha256 = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
  certificates_validated = true
}

run "inert_by_default" {
  command = plan

  variables {
    environment = "dev"
  }

  assert {
    condition     = length(aws_dynamodb_table.registry) == 0 && length(aws_kms_key.signing) == 0 && length(aws_ecs_service.registry) == 0
    error_message = "The registry must create nothing until enabled."
  }

  assert {
    condition     = length(output.gateway_environment) == 0
    error_message = "The portfolio gateway must not point at a registry that does not run."
  }
}

run "foundation_without_keys_has_no_signers" {
  command = plan

  variables {
    environment = "dev"
    enabled     = true
  }

  assert {
    condition     = length(aws_dynamodb_table.registry) == 7 && length(aws_s3_bucket.registry) == 6
    error_message = "The registry needs seven tables and six buckets."
  }

  assert {
    condition     = aws_dynamodb_table.registry["idempotency"].ttl[0].attribute_name == "ttl"
    error_message = "Only the idempotency table uses TTL, on attribute ttl."
  }

  assert {
    condition     = length(aws_kms_key.signing) == 0 && length(aws_ecs_service.signer) == 0
    error_message = "Signing keys and signers require the separately approved KMS phase."
  }
}

run "development_shape" {
  command = plan

  variables {
    environment      = "dev"
    enabled          = true
    kms_keys_enabled = true
  }

  assert {
    condition     = toset(keys(aws_ecs_service.registry)) == toset(["public-api", "release-actions", "security-actions"])
    error_message = "Dev runs the public API and both action coordinators."
  }

  assert {
    condition     = length(aws_ecs_service.signer) == 4 && length(aws_kms_key.signing) == 4
    error_message = "Each online role needs its own key and signer service."
  }

  assert {
    condition     = alltrue([for key in aws_kms_key.signing : key.customer_master_key_spec == "ECC_NIST_EDWARDS25519" && key.key_usage == "SIGN_VERIFY" && !key.multi_region])
    error_message = "Signing keys must be single-region Ed25519 SIGN_VERIFY keys."
  }

  assert {
    condition = output.signer_caller_bindings.timestamp == join(",", sort([
      "bootstrap=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-online-bootstrap-task",
      "release=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-promoter-task",
      "release=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-release-actions-task",
      "release=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-release-recovery-task",
      "release=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-release-refresh-task",
      "security=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-security-actions-task",
      "security=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-security-recovery-task",
      "security=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-security-refresh-task",
      "targets-renewal=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-targets-renewal-task",
      "targets-rotation:releases=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-targets-rotation-releases-task",
      "targets-rotation:security=arn:aws:iam::123456789012:role/portfolio-dev-app-registry-targets-rotation-security-task",
    ]))
    error_message = "The timestamp signer must accept exactly the coordinators that publish."
  }

  assert {
    condition     = !strcontains(output.signer_caller_bindings.releases, "security=")
    error_message = "The releases signer must not accept security operations."
  }

  assert {
    condition     = alltrue([for schedule in values(output.schedules) : schedule.state == "DISABLED"])
    error_message = "Registry schedules must be created disabled."
  }

  assert {
    condition     = toset(keys(output.schedules)) == toset(["source", "scanner", "promoter"])
    error_message = "Refresh schedules stay absent until separately gated."
  }

  assert {
    condition     = aws_lb.internal[0].internal
    error_message = "Registry and signer endpoints must be internal."
  }
}

run "production_is_read_only" {
  command = plan

  variables {
    environment      = "prod"
    enabled          = true
    kms_keys_enabled = true
    network = {
      vpc_id                 = "vpc-0123456789abcdef0"
      public_subnet_ids      = ["subnet-0123456789abcdef0", "subnet-0123456789abcdef1"]
      isolated_subnet_ids    = ["subnet-0123456789abcdef2", "subnet-0123456789abcdef3"]
      cluster_arn            = "arn:aws:ecs:eu-north-1:123456789012:cluster/portfolio-prod"
      zone_id                = "Z0000000000000000000"
      host_suffix            = "yousef.codes"
      gateway_security_group = "sg-0123456789abcdef0"
    }
  }

  assert {
    condition     = toset(keys(aws_ecs_service.registry)) == toset(["read-only-api"])
    error_message = "Production runs only the read-only API."
  }

  assert {
    condition     = !contains(keys(output.gateway_environment), "SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL")
    error_message = "Production must not expose action upstreams through the gateway."
  }

  assert {
    condition     = output.gateway_environment.SEEN_REGISTRY_PUBLIC_HOST == "seen.yousef.codes"
    error_message = "The production gateway serves seen.yousef.codes."
  }

  assert {
    condition     = output.gateway_environment.SEEN_REGISTRY_UPSTREAM_IDENTITY == "none"
    error_message = "The AWS gateway must not request Google identity tokens."
  }

  assert {
    condition = alltrue([
      for name in ["offline-bootstrap-import", "root-verifier"] :
      !contains(keys(local.runtime_environment[name]), "REGISTRY_DYNAMODB_TABLE_PREFIX")
    ])
    error_message = "Jobs without tables must not receive the table prefix."
  }

  assert {
    condition     = contains(keys(local.runtime_environment["online-bootstrap"]), "REGISTRY_DYNAMODB_TABLE_PREFIX")
    error_message = "Jobs with a publication lease need the table prefix."
  }

  assert {
    condition = alltrue([
      for name in ["online-bootstrap", "targets-renewal", "release-refresh"] :
      local.runtimes[name].tables.releases == "r" && local.runtimes[name].tables.state == "lease"
    ])
    error_message = "Publishing coordinators read releases and hold only the state lease."
  }
}

run "rejects_wrong_signer_repository" {
  command = plan

  variables {
    environment  = "dev"
    signer_image = "123456789012.dkr.ecr.eu-north-1.amazonaws.com/seen-registry@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  }

  expect_failures = [var.signer_image]
}

run "development_copy_job_is_isolated" {
  command = plan

  variables {
    environment = "dev"
    enabled     = true
    data_copy = {
      gcp_project = "seen-registry-dev-476219"
      source_buckets = {
        quarantine = "seen-registry-dev-quarantine"
        public     = "seen-registry-dev-blobs"
        metadata   = "seen-registry-dev-metadata"
      }
    }
  }

  assert {
    condition     = length([for variable in jsondecode(aws_ecs_task_definition.runtime["gcp-copy"].container_definitions)[0].environment : variable.name if variable.name == "REGISTRY_STORAGE_MODE"]) == 0
    error_message = "The copy job must receive only its own variables."
  }

  assert {
    condition     = output.runtimes["gcp-copy"].command == ["copy-gcp-to-aws"] && length(output.schedules) == 3
    error_message = "The copy job runs only on demand."
  }
}

run "production_refuses_the_copy_job" {
  command = plan

  variables {
    environment = "prod"
    enabled     = true
    data_copy = {
      gcp_project = "seen-registry-dev-476219"
      source_buckets = {
        quarantine = "seen-registry-dev-quarantine"
        public     = "seen-registry-dev-blobs"
        metadata   = "seen-registry-dev-metadata"
      }
    }
    network = {
      vpc_id                 = "vpc-0123456789abcdef0"
      public_subnet_ids      = ["subnet-0123456789abcdef0", "subnet-0123456789abcdef1"]
      isolated_subnet_ids    = ["subnet-0123456789abcdef2", "subnet-0123456789abcdef3"]
      cluster_arn            = "arn:aws:ecs:eu-north-1:123456789012:cluster/portfolio-prod"
      zone_id                = "Z0000000000000000000"
      host_suffix            = "yousef.codes"
      gateway_security_group = "sg-0123456789abcdef0"
    }
  }

  assert {
    condition     = !contains(keys(output.runtimes), "gcp-copy")
    error_message = "Production must never run the GCP copy job."
  }
}

run "development_on_demand_keeps_data_only" {
  command = plan

  variables {
    environment      = "dev"
    enabled          = true
    kms_keys_enabled = true
    running          = false
  }

  assert {
    condition = (
      length(aws_dynamodb_table.registry) == 7 &&
      length(aws_kms_key.signing) == 4 &&
      length(aws_lb.internal) == 0 &&
      length(aws_ecs_service.registry) == 0 &&
      length(aws_ecs_service.signer) == 0 &&
      length(output.schedules) == 0
    )
    error_message = "Stopping the dev registry keeps data and keys but removes the ALB, services, and schedules."
  }

  assert {
    condition     = length(output.gateway_environment) == 0
    error_message = "The gateway must not point at a stopped registry."
  }
}
