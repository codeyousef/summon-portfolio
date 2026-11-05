mock_provider "aws" {
  mock_resource "aws_acm_certificate" {
    defaults = {
      arn = "arn:aws:acm:eu-north-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
    }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-north-1:123456789012:loadbalancer/app/portfolio-edge/0123456789abcdef"
    }
  }
}

variables {
  aws_account_id = "123456789012"
}

run "inert_by_default" {
  command = plan

  assert {
    condition     = length(aws_vpc.main) == 0 && length(aws_lb.public) == 0
    error_message = "The shared edge must create nothing by default."
  }
}

run "network_before_certificates" {
  command = plan

  variables {
    resources_enabled = true
  }

  assert {
    condition     = length(aws_lb.public) == 1 && length(aws_lb_listener.https) == 0 && length(aws_acm_certificate_validation.default) == 0
    error_message = "The listener waits for its validated default certificate."
  }

  assert {
    condition     = length(aws_vpc_endpoint.interface) == 0
    error_message = "Hourly interface endpoints are opt-in."
  }

  assert {
    condition     = aws_lb.public[0].enable_deletion_protection && !aws_lb.public[0].internal
    error_message = "The shared public ALB must be deletion protected."
  }
}

run "complete_edge" {
  command = plan

  variables {
    resources_enabled          = true
    certificates_validated     = true
    isolated_endpoints_enabled = true
  }

  assert {
    condition     = aws_lb_listener.https[0].default_action[0].fixed_response[0].status_code == "404"
    error_message = "Unknown hosts must get 404 from the shared listener."
  }

  assert {
    condition     = length(aws_vpc_endpoint.interface) == 3 && length(aws_vpc_endpoint.interface["logs"].subnet_ids) == 1
    error_message = "Isolated tasks get ECR and Logs endpoints in one subnet."
  }
}
