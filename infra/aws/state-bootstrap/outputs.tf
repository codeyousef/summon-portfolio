output "state_bucket" {
  value = var.bootstrap_enabled ? aws_s3_bucket.state[0].bucket : null
}

output "region" {
  value = var.region
}
