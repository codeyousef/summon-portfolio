data "aws_cloudfront_cache_policy" "origin_controlled" {
  count = local.enabled

  name = "UseOriginCacheControlHeaders-QueryStrings"
}

data "aws_cloudfront_cache_policy" "optimized" {
  count = local.enabled

  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_origin_request_policy" "all_viewer" {
  count = local.enabled

  name = "Managed-AllViewer"
}

# A function URL rejects any Host header but its own.
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  count = local.enabled

  name = "Managed-AllViewerExceptHostHeader"
}

resource "aws_cloudfront_origin_access_control" "buckets" {
  count = local.enabled

  name                              = "${local.name}-buckets"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

locals {
  # Dev media stays behind the Cognito gate: in dev the application serves
  # /cdn/* from S3 through the gated ALB instead of CloudFront reading S3.
  cdn_buckets = local.is_dev || !local.site_distribution ? {} : { for purpose, spec in local.buckets : purpose => spec if spec.public_via_cdn }
}

# /cdn/media/<key> and /cdn/docs/<key> address bucket objects by <key>,
# matching the R2 layout the Worker served.
resource "aws_cloudfront_function" "strip_cdn_prefix" {
  count = local.enabled

  name    = "${local.name}-strip-cdn-prefix"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = <<-EOT
    function handler(event) {
      var request = event.request;
      request.uri = request.uri.replace(/^\/cdn\/(media|docs)\//, "/");
      return request;
    }
  EOT
}

resource "aws_cloudfront_distribution" "site" {
  count = local.site_distribution ? 1 : 0

  enabled         = true
  is_ipv6_enabled = true
  http_version    = "http2and3"
  price_class     = "PriceClass_200"
  comment         = local.name
  aliases         = local.host_names

  dynamic "origin" {
    for_each = local.lb_enabled ? [data.aws_lb.edge[0].dns_name] : []

    content {
      origin_id   = "alb"
      domain_name = origin.value

      custom_origin_config {
        http_port                = 80
        https_port               = 443
        origin_protocol_policy   = "https-only"
        origin_ssl_protocols     = ["TLSv1.2"]
        origin_read_timeout      = 60
        origin_keepalive_timeout = 60
      }
    }
  }

  dynamic "origin" {
    for_each = local.site_lambda ? [aws_lambda_function_url.site[0].function_url] : []

    content {
      origin_id   = "site-lambda"
      domain_name = trimsuffix(trimprefix(origin.value, "https://"), "/")

      custom_header {
        name  = "X-Origin-Verify"
        value = var.site_lambda_origin_secret
      }

      custom_origin_config {
        http_port                = 80
        https_port               = 443
        origin_protocol_policy   = "https-only"
        origin_ssl_protocols     = ["TLSv1.2"]
        origin_read_timeout      = 60
        origin_keepalive_timeout = 60
      }
    }
  }

  dynamic "origin" {
    for_each = local.cdn_buckets

    content {
      origin_id                = "s3-${origin.key}"
      domain_name              = aws_s3_bucket.app[origin.key].bucket_regional_domain_name
      origin_access_control_id = aws_cloudfront_origin_access_control.buckets[0].id
    }
  }

  default_cache_behavior {
    target_origin_id         = local.site_lambda_serving ? "site-lambda" : "alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = data.aws_cloudfront_cache_policy.origin_controlled[0].id
    origin_request_policy_id = local.site_lambda_serving ? data.aws_cloudfront_origin_request_policy.all_viewer_except_host[0].id : data.aws_cloudfront_origin_request_policy.all_viewer[0].id

    dynamic "function_association" {
      for_each = local.site_lambda_serving ? [aws_cloudfront_function.site_request[0].arn] : []

      content {
        event_type   = "viewer-request"
        function_arn = function_association.value
      }
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = local.cdn_buckets

    content {
      path_pattern           = "/cdn/${ordered_cache_behavior.key}/*"
      target_origin_id       = "s3-${ordered_cache_behavior.key}"
      viewer_protocol_policy = "redirect-to-https"
      allowed_methods        = ["GET", "HEAD"]
      cached_methods         = ["GET", "HEAD"]
      compress               = true
      cache_policy_id        = data.aws_cloudfront_cache_policy.optimized[0].id

      function_association {
        event_type   = "viewer-request"
        function_arn = aws_cloudfront_function.strip_cdn_prefix[0].arn
      }
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.cloudfront[0].certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}
