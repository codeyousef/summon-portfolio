import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = resolve(packageRoot, "../..");
const contractPath = resolve(repositoryRoot, "infra/cloudflare/modules/platform/contract.json");
const templatePath = resolve(packageRoot, "wrangler.template.json");
const packagePath = resolve(packageRoot, "package.json");

export async function loadInputs() {
  const [contractText, template, packageText] = await Promise.all([
    readFile(contractPath, "utf8"),
    readFile(templatePath, "utf8"),
    readFile(packagePath, "utf8"),
  ]);
  return {
    contract: JSON.parse(contractText),
    template,
    packageJson: JSON.parse(packageText),
  };
}

export async function buildConfig(options) {
  const normalized = normalizeOptions(options);
  const { contract, template, packageJson } = await loadInputs();
  const worker = contract.workers?.portfolio_edge;
  if (!worker) throw new Error("platform contract is missing workers.portfolio_edge");
  const resourceName = (suffix) => `${normalized.namePrefix}-${suffix}-${normalized.environment}`;
  const replacements = {
    SCRIPT_NAME: resourceName(worker.name_suffix),
    ENVIRONMENT: normalized.environment,
    CANONICAL_ORIGIN: normalized.canonicalOrigin,
    CONTAINER_POOL_VERSION: normalized.containerPoolVersion,
    FIRESTORE_PROJECT_ID: normalized.firestoreProject,
    FIRESTORE_DATABASE_ID: normalized.firestoreDatabase,
    FIRESTORE_WRITE_MODE: normalized.firestoreWriteMode,
    WORKERS_DEV: String(normalized.workersDev),
    PHOTOGRAPHY_WRITE_MODE: normalized.photographyWriteMode,
    PHOTOGRAPHY_UPLOAD_BUCKET: normalized.photographyUploadBucket,
    PHOTOGRAPHY_UPLOAD_PREFIX: normalized.photographyUploadPrefix,
    PHOTOGRAPHY_MAX_UPLOAD_BYTES: String(normalized.photographyMaxUploadBytes),
    PHOTOGRAPHY_R2_REVERSE_MIRROR: String(normalized.photographyReverseMirror),
    PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED: String(normalized.photographyMigrationExecutionEnabled),
    SAMURAI_FINOPS_IDENTITY_URL: normalized.samuraiFinOpsIdentityUrl,
    PORTFOLIO_MEDIA_BUCKET: resourceName(contract.r2_buckets.portfolio_media.name_suffix),
    PORTFOLIO_DOCS_BUCKET: resourceName(contract.r2_buckets.portfolio_docs.name_suffix),
    PORTFOLIO_LOGS_BUCKET: resourceName(contract.r2_buckets.portfolio_logs.name_suffix),
    FINOPS_RECEIPTS_BUCKET: resourceName(contract.r2_buckets.finops_receipts.name_suffix),
    PORTFOLIO_ASYNC_QUEUE: resourceName(contract.queues.portfolio_async.name_suffix),
    PORTFOLIO_ASYNC_DLQ: resourceName(contract.queues.portfolio_async_dlq.name_suffix),
    PORTFOLIO_ASYNC_PARKING: resourceName(contract.queues.portfolio_async_parking.name_suffix),
    FINOPS_INGEST_QUEUE: resourceName(contract.queues.finops_ingest.name_suffix),
    FINOPS_INGEST_DLQ: resourceName(contract.queues.finops_ingest_dlq.name_suffix),
    PORTFOLIO_SSR_CONTAINER: resourceName("portfolio-ssr"),
    SEEN_PLAYGROUND_CONTAINER: resourceName("seen-playground"),
    PORTFOLIO_MIGRATION_WORKFLOW: resourceName(worker.workflows[0].name_suffix),
  };
  const rendered = template.replace(/__([A-Z0-9_]+)__/g, (placeholder, token) => {
    if (!(token in replacements)) throw new Error(`unknown template placeholder ${placeholder}`);
    return replacements[token];
  });
  const unresolved = rendered.match(/__[A-Z0-9_]+__/g);
  if (unresolved) throw new Error(`unresolved template placeholders: ${unresolved.join(", ")}`);
  const config = JSON.parse(rendered);
  if (normalized.firestoreMigrationProofId !== null) {
    config.vars.FIRESTORE_MIGRATION_PROOF_ID = normalized.firestoreMigrationProofId;
  }
  config.vars.FINOPS_SHARDED_ROLLUP_WRITES_ENABLED = String(normalized.finOpsShardedRollupWritesEnabled);
  if (normalized.gcpFinOps !== null) {
    Object.assign(config.vars, {
      GCP_FINOPS_BILLING_PROJECT_ID: normalized.gcpFinOps.billingProject,
      GCP_FINOPS_BILLING_DATASET: normalized.gcpFinOps.billingDataset,
      GCP_FINOPS_BILLING_TABLE: normalized.gcpFinOps.billingTable,
      GCP_FINOPS_BILLING_LOCATION: normalized.gcpFinOps.billingLocation,
      GCP_FINOPS_MAX_BYTES_BILLED: String(normalized.gcpFinOps.maximumBytesBilled),
      GCP_WIF_ACCESS_BROKER_URL: normalized.gcpFinOps.accessBrokerUrl,
      GCP_WIF_ACCESS_CLIENT_ID: normalized.gcpFinOps.accessClientId,
      GCP_WIF_ACCESS_ISSUER: normalized.gcpFinOps.accessIssuer,
      GCP_WIF_ACCESS_AUDIENCE: normalized.gcpFinOps.accessAudience,
      GCP_WIF_PROVIDER_AUDIENCE: normalized.gcpFinOps.providerAudience,
      GCP_WIF_SERVICE_ACCOUNT: normalized.gcpFinOps.serviceAccount,
    });
  }
  if (normalized.environment === "dev") {
    // Dev imports can contain tens of thousands of overlapping immutable
    // billing entries. Keep their rollup transactions sequential until the
    // production sharded-rollup acceptance gate is complete.
    for (const consumer of config.queues.consumers) {
      if (consumer.queue !== resourceName(contract.queues.portfolio_async_dlq.name_suffix)) {
        consumer.max_concurrency = 1;
      }
    }
    for (const container of config.containers) {
      container.rollout_active_grace_period = 300;
      container.rollout_step_percentage = [100];
    }
  }
  validateConfig(config, contract, packageJson, normalized);
  return { config, rendered: `${JSON.stringify(config, null, 2)}\n` };
}

export function validateConfig(config, contract, packageJson, options) {
  const worker = contract.workers.portfolio_edge;
  assert(contract.authoritative_database === "firestore", "Firestore must remain authoritative");
  assert(packageJson.devDependencies.wrangler === contract.toolchain.wrangler, "Wrangler pin differs from platform contract");
  assert(packageJson.dependencies["@cloudflare/containers"] === "0.3.7", "Container SDK must stay pinned to 0.3.7");
  assert(config.compatibility_date === contract.toolchain.compatibility_date, "compatibility date differs from contract");
  assert(config.name === `${options.namePrefix}-${worker.name_suffix}-${options.environment}`, "script name differs from contract");
  assert(config.workers_dev === options.workersDev, "workers.dev setting differs from requested mode");
  assert(!config.workers_dev || options.environment === "dev", "workers.dev is allowed only for dev");
  assert(config.preview_urls === false, "version preview URLs must stay disabled");
  assert(
    JSON.stringify(config.triggers?.crons) === JSON.stringify(["1 5 * * *", "11 5 * * *", "21 5 * * *", "31 5 * * *", "51 5 * * *"]),
    "FinOps cron schedule differs from the reviewed daily sequence",
  );
  assert(config.routes === undefined && config.route === undefined, "routes are a separately approved phase");
  assert(config.account_id === undefined && config.zone_id === undefined, "account and zone identifiers must not be tracked");
  assert(config.assets?.binding === "ASSETS", "Static Assets binding is missing");

  const expectedR2 = new Map(worker.r2_bucket_keys.map((key) => {
    const spec = contract.r2_buckets[key];
    return [spec.binding, `${options.namePrefix}-${spec.name_suffix}-${options.environment}`];
  }));
  assertBindingMap(config.r2_buckets, "binding", "bucket_name", expectedR2, "R2");

  const expectedQueues = new Map(worker.queue_keys.map((key) => {
    const spec = contract.queues[key];
    return [spec.binding, `${options.namePrefix}-${spec.name_suffix}-${options.environment}`];
  }));
  assertBindingMap(config.queues?.producers, "binding", "queue", expectedQueues, "Queue");
  const primaryQueue = expectedQueues.get("PORTFOLIO_ASYNC_QUEUE");
  const dlq = expectedQueues.get("PORTFOLIO_ASYNC_DLQ");
  const parking = expectedQueues.get("PORTFOLIO_ASYNC_PARKING");
  const finOpsQueue = expectedQueues.get("FINOPS_INGEST_QUEUE");
  const finOpsDlq = expectedQueues.get("FINOPS_INGEST_DLQ");
  assert(config.queues?.consumers?.length === 3, "exactly three queue consumers are required");
  const primaryConsumer = config.queues.consumers.find((consumer) => consumer.queue === primaryQueue);
  const finOpsConsumer = config.queues.consumers.find((consumer) => consumer.queue === finOpsQueue);
  const deadLetterConsumer = config.queues.consumers.find((consumer) => consumer.queue === dlq);
  assert(primaryConsumer?.dead_letter_queue === dlq, "queue consumer DLQ differs from contract");
  assert(finOpsConsumer?.dead_letter_queue === finOpsDlq, "FinOps queue consumer DLQ differs from contract");
  if (options.environment === "dev") {
    assert(primaryConsumer?.max_concurrency === 1, "dev queue consumer concurrency must remain one");
    assert(finOpsConsumer?.max_concurrency === 1, "dev FinOps queue consumer concurrency must remain one");
  } else {
    assert(primaryConsumer?.max_concurrency === undefined,
      "production queue concurrency must remain platform-managed until its scale plan is reviewed");
    assert(finOpsConsumer?.max_concurrency === undefined,
      "production FinOps queue concurrency must remain platform-managed until its scale plan is reviewed");
  }
  assert(deadLetterConsumer?.max_batch_size === 10, "redrive consumer batch size must remain bounded");
  assert(deadLetterConsumer?.max_concurrency === 1, "redrive consumer concurrency must remain one");
  assert(deadLetterConsumer?.dead_letter_queue === parking, "redrive parking queue differs from contract");

  const expectedDo = [...worker.durable_objects]
    .map(({ binding, class_name, storage }) => `${binding}:${class_name}:${storage}`)
    .sort();
  const actualDo = [...config.durable_objects.bindings]
    .map(({ name, class_name }) => `${name}:${class_name}:sqlite`)
    .sort();
  assert(JSON.stringify(actualDo) === JSON.stringify(expectedDo), "Durable Object bindings differ from contract");

  assert(config.containers.length === worker.containers.length, "container count differs from contract");
  for (const spec of worker.containers) {
    const container = config.containers.find((candidate) => candidate.class_name === spec.class_name);
    assert(container, `container ${spec.class_name} is missing`);
    assert(container.instance_type === spec.instance_type, `${spec.class_name} instance type differs from contract`);
    assert(container.max_instances === spec.max_instances, `${spec.class_name} max_instances differs from contract`);
    assert(JSON.stringify(container.constraints?.regions) === JSON.stringify(worker.container_regions), `${spec.class_name} region policy differs from contract`);
  }
  const portfolioSsr = config.containers.find((candidate) => candidate.class_name === "PortfolioSsrContainer");
  const seenPlayground = config.containers.find((candidate) => candidate.class_name === "SeenPlaygroundContainer");
  assert(portfolioSsr?.image === "../../Dockerfile.portfolio",
    "Portfolio SSR must use the lightweight image without the Seen/LLVM toolchain");
  assert(seenPlayground?.image === "../../Dockerfile",
    "Seen playground must retain the isolated compiler/toolchain image");
  if (options.environment === "dev") {
    for (const container of [portfolioSsr, seenPlayground]) {
      assert(container?.rollout_active_grace_period === 300, "dev Container rollout grace must be five minutes");
      assert(JSON.stringify(container?.rollout_step_percentage) === JSON.stringify([100]),
        "dev Container rollout must be a single step");
    }
  } else {
    assert(portfolioSsr?.rollout_active_grace_period === 86400,
      "production Portfolio rollout grace must remain 24 hours");
    assert(JSON.stringify(portfolioSsr?.rollout_step_percentage) === JSON.stringify([10, 25, 50, 100]),
      "production Portfolio rollout must remain staged");
    assert(seenPlayground?.rollout_active_grace_period === 3600,
      "production Seen playground rollout grace must remain one hour");
    assert(JSON.stringify(seenPlayground?.rollout_step_percentage) === JSON.stringify([50, 100]),
      "production Seen playground rollout must remain staged");
  }

  assert(config.workflows.length === worker.workflows.length, "workflow count differs from contract");
  for (const workflow of worker.workflows) {
    const actual = config.workflows.find((candidate) => candidate.binding === workflow.binding);
    assert(actual?.class_name === workflow.class_name, `workflow ${workflow.binding} differs from contract`);
  }
  const serializedVars = JSON.stringify(config.vars).toLowerCase();
  for (const forbidden of ["secret", "credential", "private_key", "api_token"]) {
    assert(!serializedVars.includes(forbidden), `non-secret vars contain forbidden key fragment ${forbidden}`);
  }
  assert(config.vars.PORTFOLIO_MIGRATION_EXECUTION_ENABLED === "false", "migration execution must default off");
  assert(
    config.vars.PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED === String(options.photographyMigrationExecutionEnabled),
    "photography migration execution differs from the requested mode",
  );
  assert(config.vars.FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY === "false", "FinOps allocation projections must default off");
  assert(
    config.vars.FINOPS_SHARDED_ROLLUP_WRITES_ENABLED === String(options.finOpsShardedRollupWritesEnabled),
    "FinOps sharded-rollup write mode differs from the requested mode",
  );
  assert(config.vars.FINOPS_SHARDED_ROLLUPS_READY === "false", "FinOps sharded rollups must default off");
  assert(config.vars.FINOPS_COVERAGE_START_DATE === "2026-08-24", "FinOps coverage cutoff differs from approval");
  assert(
    config.vars.FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED === "false",
    "FinOps sharded-rollup backfill execution must default off",
  );
  assert(config.vars.PORTFOLIO_CONTAINER_POOL_VERSION === options.containerPoolVersion, "Container pool version differs from requested release");
  assert(config.vars.FIRESTORE_WRITE_MODE === options.firestoreWriteMode, "Firestore write mode differs from requested mode");
  assert(
    config.vars.FIRESTORE_MIGRATION_PROOF_ID === (options.firestoreMigrationProofId ?? undefined),
    "Firestore migration proof differs from requested proof",
  );
  assert(config.vars.PHOTOGRAPHY_WRITE_MODE === options.photographyWriteMode, "photography write mode differs from requested mode");
  assert(config.vars.PHOTOGRAPHY_UPLOAD_BUCKET === options.photographyUploadBucket, "photography GCS source differs from requested bucket");
  assert(config.vars.PHOTOGRAPHY_R2_REVERSE_MIRROR === String(options.photographyReverseMirror), "photography reverse mirror differs from requested mode");
  assert(config.vars.SAMURAI_FINOPS_IDENTITY_URL === options.samuraiFinOpsIdentityUrl, "Samurai identity projection endpoint differs from requested mode");
  const gcpKeys = [
    "GCP_FINOPS_BILLING_PROJECT_ID", "GCP_FINOPS_BILLING_DATASET", "GCP_FINOPS_BILLING_TABLE",
    "GCP_FINOPS_BILLING_LOCATION", "GCP_FINOPS_MAX_BYTES_BILLED", "GCP_WIF_ACCESS_BROKER_URL",
    "GCP_WIF_ACCESS_CLIENT_ID", "GCP_WIF_ACCESS_ISSUER", "GCP_WIF_ACCESS_AUDIENCE",
    "GCP_WIF_PROVIDER_AUDIENCE", "GCP_WIF_SERVICE_ACCOUNT",
  ];
  if (options.gcpFinOps === null) {
    assert(gcpKeys.every((key) => config.vars[key] === undefined), "GCP FinOps config must be entirely absent when disabled");
  } else {
    assert(gcpKeys.every((key) => typeof config.vars[key] === "string" && config.vars[key].length > 0), "GCP FinOps config is incomplete");
    assert(config.vars.GCP_WIF_ACCESS_CLIENT_SECRET === undefined, "Cloudflare Access client secret must never be a plain var");
  }
}

function normalizeOptions(options) {
  const environment = options.environment;
  if (environment !== "dev" && environment !== "prod") throw new Error("environment must be dev or prod");
  const finOpsShardedRollupWritesEnabled = options.finOpsShardedRollupWritesEnabled ?? false;
  assert(typeof finOpsShardedRollupWritesEnabled === "boolean", "FinOps sharded-rollup write mode must be boolean");
  assert(!finOpsShardedRollupWritesEnabled || environment === "dev",
    "FinOps sharded-rollup writes may only be enabled in dev before production approval");
  const namePrefix = options.namePrefix ?? "yousef";
  if (!/^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/.test(namePrefix)) throw new Error("namePrefix is invalid");
  if (!/^https:\/\/[A-Za-z0-9.-]+(?::[0-9]+)?$/.test(options.canonicalOrigin)) {
    throw new Error("canonicalOrigin must be an HTTPS origin without a path");
  }
  if (!/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(options.firestoreProject)) throw new Error("firestoreProject is invalid");
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$/.test(options.firestoreDatabase)) {
    throw new Error("firestoreDatabase is invalid");
  }
  const firestoreWriteMode = options.firestoreWriteMode ?? "source";
  assert(["source", "dual", "target"].includes(firestoreWriteMode), "firestoreWriteMode must be source, dual, or target");
  assert(
    firestoreWriteMode === "source" || options.firestoreDatabase !== "(default)",
    "dual and target Firestore modes require a named database",
  );
  const firestoreMigrationProofId = options.firestoreMigrationProofId?.trim() || null;
  assert(
    firestoreMigrationProofId === null || /^[a-z0-9][a-z0-9._-]{2,127}$/.test(firestoreMigrationProofId),
    "firestoreMigrationProofId is invalid",
  );
  assert(
    firestoreWriteMode === "source" || firestoreMigrationProofId !== null,
    "dual and target Firestore modes require a migration proof ID",
  );
  const photographyWriteMode = options.photographyWriteMode ?? "source";
  assert(["source", "dual", "target"].includes(photographyWriteMode), "photographyWriteMode must be source, dual, or target");
  const photographyUploadBucket = options.photographyUploadBucket?.trim() ?? "";
  if (photographyUploadBucket) {
    assert(/^[a-z0-9][a-z0-9._-]{1,220}[a-z0-9]$/.test(photographyUploadBucket), "photographyUploadBucket is invalid");
  }
  assert(photographyWriteMode === "target" || photographyUploadBucket.length > 0, "source and dual photography modes require a durable GCS bucket");
  const photographyUploadPrefix = options.photographyUploadPrefix ?? "photography";
  assert(/^[A-Za-z0-9][A-Za-z0-9._/-]{0,190}$/.test(photographyUploadPrefix) && !photographyUploadPrefix.includes(".."), "photographyUploadPrefix is invalid");
  const photographyMaxUploadBytes = options.photographyMaxUploadBytes ?? 15_728_640;
  assert(
    Number.isSafeInteger(photographyMaxUploadBytes) && photographyMaxUploadBytes > 0 && photographyMaxUploadBytes <= 2_147_483_647,
    "photographyMaxUploadBytes must be a positive JVM byte-array size",
  );
  const photographyReverseMirror = options.photographyReverseMirror ?? false;
  assert(typeof photographyReverseMirror === "boolean", "photographyReverseMirror must be boolean");
  assert(!photographyReverseMirror || photographyWriteMode === "target", "reverse mirroring is only valid in target mode");
  assert(!photographyReverseMirror || photographyUploadBucket.length > 0, "reverse mirroring requires a durable GCS bucket");
  const photographyMigrationExecutionEnabled = options.photographyMigrationExecutionEnabled ?? false;
  assert(typeof photographyMigrationExecutionEnabled === "boolean", "photographyMigrationExecutionEnabled must be boolean");
  assert(
    !photographyMigrationExecutionEnabled || environment === "dev",
    "photography migration execution may be enabled only in dev",
  );
  assert(
    !photographyMigrationExecutionEnabled || photographyWriteMode === "source",
    "photography migration execution requires source-authoritative mode",
  );
  const workersDev = options.workersDev ?? false;
  assert(typeof workersDev === "boolean", "workersDev must be boolean");
  assert(!workersDev || environment === "dev", "workersDev may be enabled only for dev");
  const containerPoolVersion = options.containerPoolVersion ?? "v1";
  assert(/^[a-z0-9][a-z0-9-]{0,31}$/.test(containerPoolVersion), "containerPoolVersion is invalid");
  const samuraiFinOpsIdentityUrl = options.samuraiFinOpsIdentityUrl?.trim() ?? "";
  if (samuraiFinOpsIdentityUrl) {
    assert(
      /^https:\/\/[A-Za-z0-9.-]+(?::[0-9]+)?\/internal\/portfolio\/finops\/identities$/.test(samuraiFinOpsIdentityUrl),
      "samuraiFinOpsIdentityUrl must be the exact HTTPS identity projection endpoint",
    );
  }
  const gcpInputs = {
    billingProject: options.gcpFinOpsBillingProject?.trim() ?? "",
    billingDataset: options.gcpFinOpsBillingDataset?.trim() ?? "",
    billingTable: options.gcpFinOpsBillingTable?.trim() ?? "",
    billingLocation: options.gcpFinOpsBillingLocation?.trim() ?? "",
    maximumBytesBilled: options.gcpFinOpsMaxBytesBilled,
    accessBrokerUrl: options.gcpWifAccessBrokerUrl?.trim() ?? "",
    accessClientId: options.gcpWifAccessClientId?.trim() ?? "",
    accessIssuer: options.gcpWifAccessIssuer?.trim() ?? "",
    accessAudience: options.gcpWifAccessAudience?.trim() ?? "",
    providerAudience: options.gcpWifProviderAudience?.trim() ?? "",
    serviceAccount: options.gcpWifServiceAccount?.trim() ?? "",
  };
  const gcpProvided = Object.entries(gcpInputs).filter(([, value]) => value !== "" && value !== undefined);
  let gcpFinOps = null;
  if (gcpProvided.length > 0) {
    assert(gcpProvided.length === Object.keys(gcpInputs).length, "GCP FinOps and WIF options must be supplied together");
    assert(/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(gcpInputs.billingProject), "GCP FinOps billing project is invalid");
    assert(/^[A-Za-z0-9_]{1,1024}$/.test(gcpInputs.billingDataset), "GCP FinOps billing dataset is invalid");
    assert(/^[A-Za-z0-9_]{1,1024}$/.test(gcpInputs.billingTable), "GCP FinOps billing table is invalid");
    assert(/^[A-Za-z0-9_-]{2,32}$/.test(gcpInputs.billingLocation), "GCP FinOps billing location is invalid");
    assert(Number.isSafeInteger(gcpInputs.maximumBytesBilled) && gcpInputs.maximumBytesBilled > 0 && gcpInputs.maximumBytesBilled <= 1_000_000_000,
      "GCP FinOps maximum bytes must be between 1 and 1,000,000,000");
    assert(/^https:\/\/[A-Za-z0-9.-]+(?::[0-9]+)?\/internal\/gcp-wif\/assertion$/.test(gcpInputs.accessBrokerUrl),
      "GCP WIF broker URL must be the exact HTTPS assertion route");
    assert(/^[a-f0-9]{32}\.access$/.test(gcpInputs.accessClientId), "GCP WIF Access client ID is invalid");
    assert(/^https:\/\/[A-Za-z0-9-]+\.cloudflareaccess\.com$/.test(gcpInputs.accessIssuer), "GCP WIF Access issuer is invalid");
    assert(/^[a-f0-9]{64}$/.test(gcpInputs.accessAudience), "GCP WIF Access audience is invalid");
    assert(/^\/\/iam\.googleapis\.com\/projects\/[0-9]+\/locations\/global\/workloadIdentityPools\/[a-z0-9-]+\/providers\/[a-z0-9-]+$/.test(gcpInputs.providerAudience),
      "GCP WIF provider audience is invalid");
    assert(/^[a-z][a-z0-9-]{4,61}[a-z0-9]@[a-z][a-z0-9-]{4,61}[a-z0-9]\.iam\.gserviceaccount\.com$/.test(gcpInputs.serviceAccount),
      "GCP WIF service account is invalid");
    gcpFinOps = gcpInputs;
  }
  return {
    environment,
    namePrefix,
    canonicalOrigin: options.canonicalOrigin,
    containerPoolVersion,
    finOpsShardedRollupWritesEnabled,
    samuraiFinOpsIdentityUrl,
    firestoreProject: options.firestoreProject,
    firestoreDatabase: options.firestoreDatabase,
    firestoreWriteMode,
    firestoreMigrationProofId,
    photographyWriteMode,
    photographyUploadBucket,
    photographyUploadPrefix,
    photographyMaxUploadBytes,
    photographyReverseMirror,
    photographyMigrationExecutionEnabled,
    workersDev,
    gcpFinOps,
  };
}

function assertBindingMap(values, bindingKey, resourceKey, expected, label) {
  assert(Array.isArray(values), `${label} bindings are missing`);
  assert(values.length === expected.size, `${label} binding count differs from contract`);
  for (const value of values) {
    assert(expected.get(value[bindingKey]) === value[resourceKey], `${label} binding ${value[bindingKey]} differs from contract`);
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}
