output "resources_enabled" {
  value = var.resources_enabled
}

output "zone" {
  value = var.resources_enabled ? {
    zone_id      = aws_route53_zone.primary[0].zone_id
    name         = aws_route53_zone.primary[0].name
    name_servers = aws_route53_zone.primary[0].name_servers
  } : null
}

output "image_repositories" {
  value = { for name, repository in aws_ecr_repository.images : name => repository.repository_url }
}

output "ci_roles" {
  description = "Role ARNs keyed by <environment>-<kind>, with the GitHub environment allowed to assume each."
  value = {
    for key, role in local.ci_roles : key => {
      arn                = var.resources_enabled ? aws_iam_role.ci[key].arn : null
      github_environment = role.github_environment
      kind               = role.kind
    }
  }
}
