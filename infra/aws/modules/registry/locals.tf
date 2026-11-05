locals {
  is_dev        = var.environment == "dev"
  name          = "portfolio-${var.environment}"
  prefix        = "seen-registry-${var.environment}"
  runtime_env   = local.is_dev ? "development" : "production"
  object_prefix = "v1"
  online_roles  = ["releases", "security", "snapshot", "timestamp"]

  kms_ready = var.enabled && var.kms_keys_enabled
  # Runtimes need the ceremony's public keys; signers also need the pinned root.
  active        = var.enabled && var.running
  edge_ready    = local.active && var.certificates_validated
  routine_ready = local.edge_ready && var.image != null && length(var.online_public_keys_hex) == 4
  signers_ready = local.edge_ready && local.kms_ready && var.signer_image != null && length(var.online_public_keys_hex) == 4 && var.trusted_root_v1_sha256 != null

  # One table per former Firestore collection (registry-service/docs/aws-operations.md).
  tables = {
    packages                = { range_key = null, ttl = null }
    releases                = { range_key = "sk", ttl = null }
    "release-uploads"       = { range_key = null, ttl = null }
    state                   = { range_key = null, ttl = null }
    idempotency             = { range_key = null, ttl = "ttl" }
    "review-artifacts"      = { range_key = null, ttl = null }
    "review-artifact-index" = { range_key = "sk", ttl = null }
  }

  table_names = { for table in keys(local.tables) : table => "${local.prefix}-${table}-v1" }

  bucket_roles = ["quarantine", "public", "metadata", "private", "evidence", "backup"]
  bucket_names = { for role in local.bucket_roles : role => "${local.prefix}-${role}-${var.aws_account_id}" }

  metadata_pointers = ["root.json", "timestamp.json"]

  internal_hosts = {
    api                = "api.registry.${try(var.network.host_suffix, "")}"
    "release-actions"  = "release-actions.registry.${try(var.network.host_suffix, "")}"
    "security-actions" = "security-actions.registry.${try(var.network.host_suffix, "")}"
    releases           = "signer-releases.registry.${try(var.network.host_suffix, "")}"
    security           = "signer-security.registry.${try(var.network.host_suffix, "")}"
    snapshot           = "signer-snapshot.registry.${try(var.network.host_suffix, "")}"
    timestamp          = "signer-timestamp.registry.${try(var.network.host_suffix, "")}"
  }

  signer_audiences = { for role in local.online_roles : role => "https://${local.internal_hosts[role]}" }

  all_tables_rw = { for table in keys(local.tables) : table => "rw" }
  review_tables = {
    packages                = "rw"
    releases                = "rw"
    "review-artifacts"      = "rw"
    "review-artifact-index" = "rw"
  }

  # Publishing rebuilds targets from current releases and their review evidence.
  coordinator_tables = {
    state                   = "lease"
    packages                = "r"
    releases                = "r"
    "review-artifacts"      = "r"
    "review-artifact-index" = "r"
  }

  # Grants: q/p/m = quarantine/public/metadata; r read, put, del delete;
  # m:put covers versioned metadata only; m:root and m:timestamp are pointers.
  runtime_catalog = {
    "public-api" = {
      kind      = "service"
      command   = ["serve-public-api"]
      envs      = ["dev"]
      host      = "api"
      operation = null
      roles     = []
      tables    = local.all_tables_rw
      grants    = ["q:r", "q:put", "p:r", "m:r"]
      cpu       = 512
      memory    = 1024
      schedule  = null
      secrets   = ["REGISTRY_WRITER_TOKEN", "REGISTRY_TRUST_AND_SAFETY_TOKEN"]
      extra_env = {
        REGISTRY_PUBLIC_DELAY_SECONDS = tostring(var.public_delay_seconds)
        REGISTRY_WRITER_MODE          = "opaque-dev"
        REGISTRY_WRITER_PRINCIPAL     = "internal-${var.environment}-publisher"
        REGISTRY_OWNER_ALLOWLIST      = "seen"
        REGISTRY_WRITERS_ENABLED      = "true"
        REGISTRY_PROMOTION_MODE       = "disabled"
      }
    }
    "read-only-api" = {
      kind      = "service"
      command   = ["serve-read-only-public-api"]
      envs      = ["prod"]
      host      = "api"
      operation = null
      roles     = []
      tables = {
        packages                = "r"
        releases                = "r"
        "review-artifacts"      = "r"
        "review-artifact-index" = "r"
      }
      grants    = ["p:r", "m:r"]
      cpu       = 512
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    "release-actions" = {
      kind      = "service"
      command   = ["serve-release-actions"]
      envs      = ["dev"]
      host      = "release-actions"
      operation = "release"
      roles     = ["releases", "snapshot", "timestamp"]
      tables    = merge(local.all_tables_rw, { "release-uploads" = "r" })
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = ["REGISTRY_WRITER_TOKEN"]
      extra_env = {}
    }
    "security-actions" = {
      kind      = "service"
      command   = ["serve-security-actions"]
      envs      = ["dev"]
      host      = "security-actions"
      operation = "security"
      roles     = ["security", "snapshot", "timestamp"]
      tables    = merge(local.all_tables_rw, { "release-uploads" = "r" })
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = ["REGISTRY_SECURITY_TOKEN"]
      extra_env = {}
    }
    "source" = {
      kind      = "job"
      command   = ["verify-source-once"]
      envs      = ["dev"]
      host      = null
      operation = null
      roles     = []
      tables    = local.review_tables
      grants    = ["q:r"]
      cpu       = 512
      memory    = 1024
      schedule  = { expression = "cron(0/5 * * * ? *)", group = "review" }
      secrets   = ["REGISTRY_GITHUB_APP_ID", "REGISTRY_GITHUB_APP_PRIVATE_KEY_PEM", "REGISTRY_GITLAB_FORGE_TOKEN"]
      extra_env = { REGISTRY_PUBLIC_DELAY_SECONDS = tostring(var.public_delay_seconds) }
    }
    "scanner" = {
      kind      = "job"
      command   = ["scan-once"]
      envs      = ["dev"]
      host      = null
      operation = null
      roles     = []
      tables    = local.review_tables
      grants    = ["q:r"]
      cpu       = 512
      memory    = 1024
      schedule  = { expression = "cron(2/5 * * * ? *)", group = "review" }
      secrets   = []
      extra_env = { REGISTRY_PUBLIC_DELAY_SECONDS = tostring(var.public_delay_seconds) }
    }
    "promoter" = {
      kind      = "job"
      command   = ["promote-once"]
      envs      = ["dev"]
      host      = null
      operation = "release"
      roles     = ["releases", "snapshot", "timestamp"]
      tables    = local.all_tables_rw
      grants    = ["q:r", "q:del", "p:r", "p:put", "m:r", "m:put"]
      cpu       = 512
      memory    = 1024
      schedule  = { expression = "cron(0 * * * ? *)", group = "review" }
      secrets   = []
      extra_env = { REGISTRY_PUBLIC_DELAY_SECONDS = tostring(var.public_delay_seconds) }
    }
    "release-refresh" = {
      kind      = "job"
      command   = ["refresh-releases-once"]
      envs      = ["dev", "prod"]
      host      = null
      operation = "release"
      roles     = ["releases", "snapshot", "timestamp"]
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = { expression = "cron(20 0 * * ? *)", group = "refresh" }
      secrets   = []
      extra_env = {}
    }
    "security-refresh" = {
      kind      = "job"
      command   = ["refresh-security-once"]
      envs      = ["dev", "prod"]
      host      = null
      operation = "security"
      roles     = ["security", "snapshot", "timestamp"]
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = { expression = "cron(10 * * * ? *)", group = "refresh" }
      secrets   = []
      extra_env = {}
    }
    "release-recovery" = {
      kind      = "job"
      command   = ["recover-expired-releases-once"]
      envs      = ["dev", "prod"]
      host      = null
      operation = "release"
      roles     = ["releases", "snapshot", "timestamp"]
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    "security-recovery" = {
      kind      = "job"
      command   = ["recover-expired-security-once"]
      envs      = ["dev", "prod"]
      host      = null
      operation = "security"
      roles     = ["security", "snapshot", "timestamp"]
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    "online-bootstrap" = {
      kind      = "job"
      command   = ["bootstrap-online"]
      envs      = ["dev", "prod"]
      host      = null
      operation = "bootstrap"
      roles     = local.online_roles
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    # Envelopes arrive as REGISTRY_BOOTSTRAP_*_ENVELOPE_BASE64 run-task
    # overrides during the approved ceremony; nothing schedules this job.
    "offline-bootstrap-import" = {
      kind      = "job"
      command   = ["import-offline-bootstrap"]
      envs      = ["dev", "prod"]
      host      = null
      operation = null
      roles     = []
      tables    = {}
      grants    = ["m:r", "m:put", "m:root"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    # Ceremony imports read REGISTRY_MAINTENANCE_ENVELOPE_BASE64 supplied for
    # one approved run; nothing schedules them.
    "targets-renewal" = {
      kind      = "job"
      command   = ["import-offline-targets-renewal"]
      envs      = ["dev", "prod"]
      host      = null
      operation = "targets-renewal"
      roles     = ["snapshot", "timestamp"]
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    "targets-rotation-releases" = {
      kind      = "job"
      command   = ["import-offline-targets-rotation", "releases"]
      envs      = ["dev", "prod"]
      host      = null
      operation = "targets-rotation:releases"
      roles     = ["releases", "snapshot", "timestamp"]
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    "targets-rotation-security" = {
      kind      = "job"
      command   = ["import-offline-targets-rotation", "security"]
      envs      = ["dev", "prod"]
      host      = null
      operation = "targets-rotation:security"
      roles     = ["security", "snapshot", "timestamp"]
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    "root-import" = {
      kind      = "job"
      command   = ["import-offline-root-rotation"]
      envs      = ["dev", "prod"]
      host      = null
      operation = null
      roles     = []
      tables    = local.coordinator_tables
      grants    = ["m:r", "m:put", "m:root"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
    "root-verifier" = {
      kind      = "job"
      command   = ["verify-root-chain"]
      envs      = ["dev", "prod"]
      host      = null
      operation = null
      roles     = []
      tables    = {}
      grants    = ["m:r"]
      cpu       = 256
      memory    = 1024
      schedule  = null
      secrets   = []
      extra_env = {}
    }
  }

  # Development-only GCP -> AWS copy. It accepts only its own variables, so it
  # bypasses the standard runtime environment below.
  copy_catalog = local.is_dev && var.data_copy != null ? {
    "gcp-copy" = {
      kind      = "job"
      command   = ["copy-gcp-to-aws"]
      envs      = ["dev"]
      host      = null
      operation = null
      roles     = []
      tables    = { for table in keys(local.tables) : table => "copy" }
      grants    = ["q:r", "q:put", "p:r", "p:put", "m:r", "m:put", "m:root", "m:timestamp"]
      cpu       = 1024
      memory    = 2048
      schedule  = null
      secrets   = ["FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64"]
      extra_env = {}
    }
  } : {}

  runtimes = merge(
    { for name, runtime in local.runtime_catalog : name => runtime if contains(runtime.envs, var.environment) },
    local.copy_catalog,
  )

  services = { for name, runtime in local.runtimes : name => runtime if runtime.kind == "service" }
  jobs     = { for name, runtime in local.runtimes : name => runtime if runtime.kind == "job" }

  runtime_role_names = { for name in keys(local.runtimes) : name => "${local.name}-app-registry-${name}-task" }

  # Signers accept each operation only from the exact coordinator roles that
  # perform it (TufSigningOperation.permitsRole).
  signer_caller_bindings = {
    for role in local.online_roles : role => join(",", sort([
      for name, runtime in local.runtimes :
      "${runtime.operation}=arn:aws:iam::${var.aws_account_id}:role/${local.runtime_role_names[name]}"
      if runtime.operation != null && contains(runtime.roles, role)
    ]))
  }

  bucket_for_grant = { q = "quarantine", p = "public", m = "metadata" }

  public_key_environment = {
    for role, key in var.online_public_keys_hex : "REGISTRY_KMS_${upper(role)}_PUBLIC_KEY_HEX" => key
  }

  identity_environment = {
    REGISTRY_STORAGE_MODE          = "dynamodb"
    REGISTRY_OBJECT_STORE_PROVIDER = "s3"
    REGISTRY_DYNAMODB_TABLE_PREFIX = local.prefix
    REGISTRY_ENVIRONMENT           = local.runtime_env
    REGISTRY_REPOSITORY_ID         = coalesce(var.repository_id, "seen-${var.environment}-registry-v1")
    REGISTRY_ORIGIN                = coalesce(var.registry_origin, "https://seen.${try(var.network.host_suffix, "invalid")}/packages")
    REGISTRY_OBJECT_PREFIX         = local.object_prefix
  }

  copy_environment = var.data_copy == null ? {} : {
    REGISTRY_ENVIRONMENT                    = "development"
    GOOGLE_CLOUD_PROJECT                    = var.data_copy.gcp_project
    REGISTRY_COPY_SOURCE_FIRESTORE_DATABASE = var.data_copy.firestore_database
    REGISTRY_COPY_SOURCE_QUARANTINE_BUCKET  = var.data_copy.source_buckets.quarantine
    REGISTRY_COPY_SOURCE_PUBLIC_BUCKET      = var.data_copy.source_buckets.public
    REGISTRY_COPY_SOURCE_METADATA_BUCKET    = var.data_copy.source_buckets.metadata
    REGISTRY_QUARANTINE_BUCKET              = local.bucket_names.quarantine
    REGISTRY_PUBLIC_BUCKET                  = local.bucket_names.public
    REGISTRY_METADATA_BUCKET                = local.bucket_names.metadata
    REGISTRY_DYNAMODB_TABLE_PREFIX          = local.prefix
    REGISTRY_OBJECT_PREFIX                  = local.object_prefix
  }

  runtime_environment = {
    for name, runtime in local.runtimes : name => name == "gcp-copy" ? tomap(local.copy_environment) : tomap(merge(
      # Jobs without tables reject the table prefix (rejectMaintenanceEnvironment).
      { for key, value in local.identity_environment : key => value if key != "REGISTRY_DYNAMODB_TABLE_PREFIX" || length(runtime.tables) > 0 },
      local.public_key_environment,
      {
        for bucket in distinct([for grant in runtime.grants : local.bucket_for_grant[split(":", grant)[0]]]) :
        "REGISTRY_${upper(bucket)}_BUCKET" => local.bucket_names[bucket]
      },
      merge([
        for role in runtime.roles : {
          "REGISTRY_TUF_${upper(role)}_SIGNER_URL"      = "${local.signer_audiences[role]}/sign"
          "REGISTRY_TUF_${upper(role)}_SIGNER_AUDIENCE" = local.signer_audiences[role]
        }
      ]...),
      runtime.extra_env,
    ))
  }

  runtime_secret_names = toset(flatten([for runtime in values(local.runtimes) : runtime.secrets]))
}
