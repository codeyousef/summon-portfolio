locals {
  enabled = var.resources_enabled ? 1 : 0
  is_dev  = var.environment == "dev"
  name    = "portfolio-${var.environment}"

  # Hostname suffix shared by every ecosystem host in this environment.
  host_suffix = local.is_dev ? "dev.${var.zone_name}" : var.zone_name

  ecosystem_projects = ["summon", "materia", "sigil", "aether"]

  # Every hostname the Kotlin host map serves (Application.kt). docs.* exists
  # only in production; dev uses dev.<zone> as its main host.
  hosts = merge(
    local.is_dev ? {
      main = "dev.${var.zone_name}"
      } : {
      apex = var.zone_name
      main = "www.${var.zone_name}"
      docs = "docs.${var.zone_name}"
    },
    { for project in concat(local.ecosystem_projects, ["seen", "building"]) : project => "${project}.${local.host_suffix}" },
  )

  host_names = sort(values(local.hosts))

  vpc_cidr = coalesce(var.vpc_cidr, local.is_dev ? "10.80.0.0/16" : "10.81.0.0/16")

  public_subnets = {
    for index, zone in var.availability_zones : zone => cidrsubnet(local.vpc_cidr, 4, index)
  }

  isolated_subnets = {
    for index, zone in var.availability_zones : zone => cidrsubnet(local.vpc_cidr, 4, index + 8)
  }

  # Listeners, CloudFront, public records, and services need issued certificates.
  edge_enabled       = var.resources_enabled && var.certificates_validated
  edge               = local.edge_enabled ? 1 : 0
  ssr_enabled        = local.edge_enabled && var.images.ssr != null
  playground_enabled = local.edge_enabled && var.images.playground != null

  # The FinOps jobs the Cloudflare Worker ran on cron. EventBridge Scheduler
  # delivers each as a message on the jobs queue.
  schedules = {
    "finops-gcp-billing"        = { cron = "cron(1 5 * * ? *)", job = "finops.gcp-billing" }
    "finops-cloudflare-billing" = { cron = "cron(11 5 * * ? *)", job = "finops.cloudflare-billing" }
    "finops-stripe"             = { cron = "cron(21 5 * * ? *)", job = "finops.stripe" }
    "finops-recurring-expenses" = { cron = "cron(31 5 * * ? *)", job = "finops.recurring-expenses" }
    "finops-reconcile"          = { cron = "cron(51 5 * * ? *)", job = "finops.reconcile" }
  }

  buckets = {
    media    = { public_via_cdn = true, versioned = true }
    docs     = { public_via_cdn = true, versioned = false }
    receipts = { public_via_cdn = false, versioned = true }
    logs     = { public_via_cdn = false, versioned = false }
  }

  bucket_names = {
    for purpose in keys(local.buckets) : purpose => "${local.name}-${purpose}-${var.aws_account_id}"
  }
}
