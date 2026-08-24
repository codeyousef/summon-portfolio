locals {
  platform_contract = jsondecode(file("${path.module}/contract.json"))

  r2_bucket_specs = {
    for key, spec in local.platform_contract.r2_buckets : key => merge(spec, {
      name = "${var.name_prefix}-${spec.name_suffix}-${var.environment}"
      location = (
        spec.location_policy == "portfolio" ?
        var.portfolio_r2_location :
        var.seen_r2_location
      )
    })
  }

  queue_specs = {
    for key, spec in local.platform_contract.queues : key => merge(spec, {
      name = "${var.name_prefix}-${spec.name_suffix}-${var.environment}"
    })
  }

  worker_release_contracts = {
    for key, spec in local.platform_contract.workers : key => merge(spec, {
      script_name        = "${var.name_prefix}-${spec.name_suffix}-${var.environment}"
      compatibility_date = local.platform_contract.toolchain.compatibility_date
      wrangler_version   = local.platform_contract.toolchain.wrangler
      r2_bindings = {
        for bucket_key in spec.r2_bucket_keys :
        local.r2_bucket_specs[bucket_key].binding => local.r2_bucket_specs[bucket_key].name
      }
      queue_bindings = {
        for queue_key in spec.queue_keys :
        local.queue_specs[queue_key].binding => local.queue_specs[queue_key].name
      }
      workflows = [
        for workflow in spec.workflows : merge(workflow, {
          name = "${var.name_prefix}-${workflow.name_suffix}-${var.environment}"
        })
      ]
      route_less_workers = try([
        for worker in spec.route_less_workers : merge(worker, {
          script_name = "${var.name_prefix}-${worker.name_suffix}-${var.environment}"
        })
      ], [])
    })
  }
}
