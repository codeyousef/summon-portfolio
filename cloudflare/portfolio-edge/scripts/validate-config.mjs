#!/usr/bin/env node
import { buildConfig } from "./config-lib.mjs";

const common = {
  namePrefix: "yousef",
  firestoreProject: "portfolio-476219",
  photographyUploadBucket: "portfolio-476219-portfolio-uploads",
};
const dev = await buildConfig({
  ...common,
  environment: "dev",
  canonicalOrigin: "https://dev.yousef.codes",
  firestoreDatabase: "portfolio-me-dev",
  workersDev: true,
});
const prod = await buildConfig({
  ...common,
  environment: "prod",
  canonicalOrigin: "https://yousef.codes",
  firestoreDatabase: "portfolio-me-prod",
});
const dual = await buildConfig({
  ...common,
  environment: "dev",
  canonicalOrigin: "https://dev.yousef.codes",
  firestoreDatabase: "portfolio-me-dev",
  photographyWriteMode: "dual",
});
const targetWithRollbackMirror = await buildConfig({
  ...common,
  environment: "prod",
  canonicalOrigin: "https://yousef.codes",
  firestoreDatabase: "portfolio-me-prod",
  photographyWriteMode: "target",
  photographyReverseMirror: true,
});
const firestoreDual = await buildConfig({
  ...common,
  environment: "dev",
  canonicalOrigin: "https://dev.yousef.codes",
  firestoreDatabase: "portfolio-me-dev",
  firestoreWriteMode: "dual",
  firestoreMigrationProofId: "portfolio-me-dev-initial-v1",
});
const photographyMigration = await buildConfig({
  ...common,
  environment: "dev",
  canonicalOrigin: "https://dev.yousef.codes",
  firestoreDatabase: "portfolio-me-dev",
  photographyMigrationExecutionEnabled: true,
});
const gcpFinOps = await buildConfig({
  ...common,
  environment: "dev",
  canonicalOrigin: "https://dev.yousef.codes",
  firestoreDatabase: "portfolio-me-dev",
  workersDev: true,
  gcpFinOpsBillingProject: "felidai-dev",
  gcpFinOpsBillingDataset: "billing_export",
  gcpFinOpsBillingTable: "gcp_billing_export_v1_ABCDEF_123456_ABCDEF",
  gcpFinOpsBillingLocation: "US",
  gcpFinOpsMaxBytesBilled: 50_000_000,
  gcpWifAccessBrokerUrl: "https://dev.yousef.codes/internal/gcp-wif/assertion",
  gcpWifAccessClientId: `${"a".repeat(32)}.access`,
  gcpWifAccessIssuer: "https://felidai.cloudflareaccess.com",
  gcpWifAccessAudience: "b".repeat(64),
  gcpWifProviderAudience: "//iam.googleapis.com/projects/123456789/locations/global/workloadIdentityPools/cloudflare-dev/providers/access-dev",
  gcpWifServiceAccount: "finops-reader@felidai-dev.iam.gserviceaccount.com",
});
const futureOnlyShardedWrites = await buildConfig({
  ...common,
  environment: "dev",
  canonicalOrigin: "https://dev.yousef.codes",
  firestoreDatabase: "portfolio-me-dev",
  finOpsShardedRollupWritesEnabled: true,
});
if (dev.config.name === prod.config.name) throw new Error("dev and prod scripts must be isolated");
if (dev.config.workers_dev !== true || prod.config.workers_dev !== false) {
  throw new Error("workers.dev must be enabled only for dev");
}
if (dev.config.containers.some((container) =>
  container.rollout_active_grace_period !== 300 || JSON.stringify(container.rollout_step_percentage) !== "[100]")) {
  throw new Error("dev Containers must use a five-minute single-step rollout");
}
const prodPortfolio = prod.config.containers.find((container) => container.class_name === "PortfolioSsrContainer");
const prodSeen = prod.config.containers.find((container) => container.class_name === "SeenPlaygroundContainer");
if (prodPortfolio.rollout_active_grace_period !== 86400 ||
    JSON.stringify(prodPortfolio.rollout_step_percentage) !== "[10,25,50,100]" ||
    prodSeen.rollout_active_grace_period !== 3600 || JSON.stringify(prodSeen.rollout_step_percentage) !== "[50,100]") {
  throw new Error("production Container rollout policy changed");
}
if (dev.rendered.includes("__") || prod.rendered.includes("__")) throw new Error("generated config has placeholders");
if (dual.config.vars.PHOTOGRAPHY_WRITE_MODE !== "dual") throw new Error("dual photography config was not preserved");
if (targetWithRollbackMirror.config.vars.PHOTOGRAPHY_R2_REVERSE_MIRROR !== "true") {
  throw new Error("target reverse-mirror config was not preserved");
}
if (firestoreDual.config.vars.FIRESTORE_WRITE_MODE !== "dual") {
  throw new Error("dual Firestore config was not preserved");
}
if (firestoreDual.config.vars.FIRESTORE_MIGRATION_PROOF_ID !== "portfolio-me-dev-initial-v1") {
  throw new Error("dual Firestore migration proof was not preserved");
}
if (gcpFinOps.config.vars.GCP_FINOPS_BILLING_PROJECT_ID !== "felidai-dev" ||
    gcpFinOps.config.vars.GCP_WIF_ACCESS_CLIENT_SECRET !== undefined) {
  throw new Error("keyless GCP FinOps config was not preserved securely");
}
if (futureOnlyShardedWrites.config.vars.FINOPS_COVERAGE_START_DATE !== "2026-08-24" ||
    futureOnlyShardedWrites.config.vars.FINOPS_SHARDED_ROLLUP_WRITES_ENABLED !== "true" ||
    futureOnlyShardedWrites.config.vars.FINOPS_SHARDED_ROLLUPS_READY !== "false") {
  throw new Error("future-only sharded-rollup write config was not preserved safely");
}
if (!futureOnlyShardedWrites.config.queues.producers.some((producer) =>
  producer.binding === "FINOPS_INGEST_QUEUE" && producer.queue === "yousef-finops-ingest-dev")) {
  throw new Error("future-only FinOps queue producer binding is missing");
}
if (!futureOnlyShardedWrites.config.queues.producers.some((producer) =>
  producer.binding === "FINOPS_INGEST_DLQ" && producer.queue === "yousef-finops-ingest-dlq-dev") ||
    !futureOnlyShardedWrites.config.queues.consumers.some((consumer) =>
      consumer.queue === "yousef-finops-ingest-dev" &&
      consumer.dead_letter_queue === "yousef-finops-ingest-dlq-dev")) {
  throw new Error("future-only FinOps dead-letter isolation is missing");
}
if (dev.config.vars.PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED !== "false" ||
    photographyMigration.config.vars.PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED !== "true") {
  throw new Error("photography migration execution must be explicit and default off");
}
let productionPhotographyMigrationRejected = false;
try {
  await buildConfig({
    ...common,
    environment: "prod",
    canonicalOrigin: "https://yousef.codes",
    firestoreDatabase: "portfolio-me-prod",
    photographyMigrationExecutionEnabled: true,
  });
} catch (error) {
  productionPhotographyMigrationRejected = String(error).includes("only in dev");
}
if (!productionPhotographyMigrationRejected) throw new Error("production photography migration execution was not rejected");

let productionShardedWritesRejected = false;
try {
  await buildConfig({
    ...common,
    environment: "prod",
    canonicalOrigin: "https://yousef.codes",
    firestoreDatabase: "portfolio-me-prod",
    finOpsShardedRollupWritesEnabled: true,
  });
} catch (error) {
  productionShardedWritesRejected = String(error).includes("only be enabled in dev");
}
if (!productionShardedWritesRejected) throw new Error("production sharded-rollup writes were not rejected");

process.stdout.write("Portfolio Worker config contract is valid; workers.dev is dev-only.\n");
