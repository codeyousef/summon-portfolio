#!/usr/bin/env node
import { writeFile } from "node:fs/promises";
import { parseArgs } from "node:util";
import { buildConfig } from "./config-lib.mjs";

const { values } = parseArgs({
  options: {
    environment: { type: "string" },
    "name-prefix": { type: "string", default: "yousef" },
    "canonical-origin": { type: "string" },
    "container-pool-version": { type: "string", default: "v1" },
    "firestore-project": { type: "string" },
    "firestore-database": { type: "string" },
    "firestore-write-mode": { type: "string", default: "source" },
    "firestore-migration-proof-id": { type: "string" },
    "photography-write-mode": { type: "string", default: "source" },
    "photography-upload-bucket": { type: "string" },
    "photography-upload-prefix": { type: "string", default: "photography" },
    "photography-max-upload-bytes": { type: "string", default: "15728640" },
    "photography-reverse-mirror": { type: "boolean", default: false },
    "photography-migration-execution-enabled": { type: "boolean", default: false },
    "finops-sharded-rollup-writes-enabled": { type: "boolean", default: false },
    "samurai-finops-identity-url": { type: "string" },
    "gcp-finops-billing-project": { type: "string" },
    "gcp-finops-billing-dataset": { type: "string" },
    "gcp-finops-billing-table": { type: "string" },
    "gcp-finops-billing-location": { type: "string" },
    "gcp-finops-max-bytes-billed": { type: "string" },
    "gcp-wif-access-broker-url": { type: "string" },
    "gcp-wif-access-client-id": { type: "string" },
    "gcp-wif-access-issuer": { type: "string" },
    "gcp-wif-access-audience": { type: "string" },
    "gcp-wif-provider-audience": { type: "string" },
    "gcp-wif-service-account": { type: "string" },
    "workers-dev": { type: "boolean", default: false },
    output: { type: "string" },
  },
  strict: true,
});

for (const required of ["environment", "canonical-origin", "firestore-project", "firestore-database"]) {
  if (!values[required]) throw new Error(`--${required} is required`);
}

const environment = values.environment;
const output = values.output ?? `wrangler.generated.${environment}.json`;
const { rendered } = await buildConfig({
  environment,
  namePrefix: values["name-prefix"],
  canonicalOrigin: values["canonical-origin"],
  containerPoolVersion: values["container-pool-version"],
  firestoreProject: values["firestore-project"],
  firestoreDatabase: values["firestore-database"],
  firestoreWriteMode: values["firestore-write-mode"],
  firestoreMigrationProofId: values["firestore-migration-proof-id"],
  photographyWriteMode: values["photography-write-mode"],
  photographyUploadBucket: values["photography-upload-bucket"],
  photographyUploadPrefix: values["photography-upload-prefix"],
  photographyMaxUploadBytes: Number(values["photography-max-upload-bytes"]),
  photographyReverseMirror: values["photography-reverse-mirror"],
  photographyMigrationExecutionEnabled: values["photography-migration-execution-enabled"],
  finOpsShardedRollupWritesEnabled: values["finops-sharded-rollup-writes-enabled"],
  samuraiFinOpsIdentityUrl: values["samurai-finops-identity-url"],
  gcpFinOpsBillingProject: values["gcp-finops-billing-project"],
  gcpFinOpsBillingDataset: values["gcp-finops-billing-dataset"],
  gcpFinOpsBillingTable: values["gcp-finops-billing-table"],
  gcpFinOpsBillingLocation: values["gcp-finops-billing-location"],
  gcpFinOpsMaxBytesBilled: values["gcp-finops-max-bytes-billed"] === undefined
    ? undefined
    : Number(values["gcp-finops-max-bytes-billed"]),
  gcpWifAccessBrokerUrl: values["gcp-wif-access-broker-url"],
  gcpWifAccessClientId: values["gcp-wif-access-client-id"],
  gcpWifAccessIssuer: values["gcp-wif-access-issuer"],
  gcpWifAccessAudience: values["gcp-wif-access-audience"],
  gcpWifProviderAudience: values["gcp-wif-provider-audience"],
  gcpWifServiceAccount: values["gcp-wif-service-account"],
  workersDev: values["workers-dev"],
});
await writeFile(output, rendered, { encoding: "utf8", flag: "wx" });
process.stdout.write(`Generated ${output}; no Cloudflare API was called.\n`);
