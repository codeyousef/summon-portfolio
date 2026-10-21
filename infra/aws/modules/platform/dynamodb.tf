locals {
  table_names = {
    documents = "${local.name}-documents"
    sessions  = "${local.name}-sessions"
  }

  # Key schema and secondary indexes follow the application's DynamoDB
  # adapters (PORTFOLIO_DATA_BACKEND=dynamodb).
  tables = {
    # pk = collection, sk = document id. Both indexes are sparse: order-index
    # serves ordered collection reads and lookup-index equality lookups.
    documents = {
      hash_key      = "pk"
      range_key     = "sk"
      ttl_attribute = null
      indexes = {
        "order-index"  = { hash_key = "opk", range_key = "osk" }
        "lookup-index" = { hash_key = "lpk", range_key = "sk" }
      }
    }
    # Sessions (session:*) and job idempotency leases (job:*).
    sessions = {
      hash_key      = "pk"
      range_key     = null
      ttl_attribute = "expires_at"
      indexes       = {}
    }
  }
}

resource "aws_dynamodb_table" "app" {
  for_each = var.resources_enabled ? local.tables : {}

  name                        = local.table_names[each.key]
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = each.value.hash_key
  range_key                   = each.value.range_key
  deletion_protection_enabled = !local.is_dev

  dynamic "attribute" {
    for_each = toset(compact(concat(
      [each.value.hash_key, each.value.range_key],
      flatten([for index in values(each.value.indexes) : [index.hash_key, index.range_key]]),
    )))

    content {
      name = attribute.value
      type = "S"
    }
  }

  dynamic "global_secondary_index" {
    for_each = each.value.indexes

    content {
      name            = global_secondary_index.key
      hash_key        = global_secondary_index.value.hash_key
      range_key       = global_secondary_index.value.range_key
      projection_type = "ALL"
    }
  }

  dynamic "ttl" {
    for_each = each.value.ttl_attribute == null ? [] : [each.value.ttl_attribute]

    content {
      attribute_name = ttl.value
      enabled        = true
    }
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  lifecycle {
    prevent_destroy = true
  }
}
