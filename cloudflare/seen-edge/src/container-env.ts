import { ContractError } from "./contracts";
import type { Env, MaintenanceCommand, TufRole } from "./types";

export type RegistryJob =
  | { id: string; kind: "promotion" }
  | { id: string; kind: "maintenance"; command: MaintenanceCommand };

export interface RegistryJobLaunch {
  entrypoint: string[];
  envVars: Record<string, string>;
  labels: Record<string, string>;
}

const JAR = "/app/seen-registry-service.jar";
const SIGNER_ORIGINS: Record<TufRole, string> = {
  releases: "https://seen-signer-releases.internal",
  security: "https://seen-signer-security.internal",
  snapshot: "https://seen-signer-snapshot.internal",
  timestamp: "https://seen-signer-timestamp.internal",
};

export function registryJobLaunch(job: RegistryJob, env: Env): RegistryJobLaunch {
  validateCommonEnv(env);
  if (!/^[A-Za-z0-9][A-Za-z0-9:_-]{7,127}$/.test(job.id)) throw new ContractError("job id is invalid");
  const common: Record<string, string> = {
    GOOGLE_CLOUD_PROJECT: env.FIRESTORE_PROJECT_ID,
    REGISTRY_ENVIRONMENT: env.SEEN_ENVIRONMENT,
    REGISTRY_REPOSITORY_ID: env.SEEN_REPOSITORY_ID,
    REGISTRY_ORIGIN: env.SEEN_REGISTRY_ORIGIN,
    REGISTRY_OBJECT_PREFIX: env.SEEN_OBJECT_PREFIX,
    REGISTRY_STORAGE_MODE: "gcp",
    REGISTRY_OBJECT_STORE_PROVIDER: "r2",
    REGISTRY_R2_ENDPOINT: env.SEEN_R2_ENDPOINT,
    REGISTRY_R2_REGION: "auto",
    REGISTRY_KMS_RELEASES_PUBLIC_KEY_HEX: publicKey(env.SEEN_RELEASES_PUBLIC_KEY_HEX, "releases"),
    REGISTRY_KMS_SECURITY_PUBLIC_KEY_HEX: publicKey(env.SEEN_SECURITY_PUBLIC_KEY_HEX, "security"),
    REGISTRY_KMS_SNAPSHOT_PUBLIC_KEY_HEX: publicKey(env.SEEN_SNAPSHOT_PUBLIC_KEY_HEX, "snapshot"),
    REGISTRY_KMS_TIMESTAMP_PUBLIC_KEY_HEX: publicKey(env.SEEN_TIMESTAMP_PUBLIC_KEY_HEX, "timestamp"),
  };
  const externalCredential = env.FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64?.trim();
  const serviceAccountCredential = env.FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64?.trim();
  if (externalCredential && serviceAccountCredential) throw new ContractError("configure exactly one Firestore credential");
  if (externalCredential) {
    common.FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64 = requiredSecret(externalCredential, "FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64");
  } else {
    common.FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64 = requiredSecret(serviceAccountCredential ?? "", "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64");
  }
  if (job.kind === "promotion") {
    if (env.SEEN_ENVIRONMENT !== "development") throw new ContractError("review promotion workers are development-only");
    return {
      entrypoint: ["java", "-jar", JAR, "promote-once"],
      envVars: {
        ...common,
        REGISTRY_FIRESTORE_DATABASE: env.FIRESTORE_DATABASE_ID,
        REGISTRY_QUARANTINE_BUCKET: env.SEEN_QUARANTINE_BUCKET_NAME,
        REGISTRY_PUBLIC_BUCKET: env.SEEN_PUBLIC_BUCKET_NAME,
        REGISTRY_METADATA_BUCKET: env.SEEN_METADATA_BUCKET_NAME,
        REGISTRY_R2_ACCESS_KEY_ID: requiredSecret(env.SEEN_PROMOTION_R2_ACCESS_KEY_ID, "SEEN_PROMOTION_R2_ACCESS_KEY_ID"),
        REGISTRY_R2_SECRET_ACCESS_KEY: requiredSecret(env.SEEN_PROMOTION_R2_SECRET_ACCESS_KEY, "SEEN_PROMOTION_R2_SECRET_ACCESS_KEY"),
        ...signerTargets(["releases", "snapshot", "timestamp"]),
        SEEN_SIGNER_CALL_TOKEN: requiredSignerToken(env.SEEN_SIGNER_CALL_TOKEN),
      },
      labels: { workload: "seen-registry-job", kind: "promotion", jobId: job.id },
    };
  }

  const signingRoles = maintenanceSigningRoles(job.command);
  const envVars: Record<string, string> = {
    ...common,
    REGISTRY_METADATA_BUCKET: env.SEEN_METADATA_BUCKET_NAME,
    REGISTRY_R2_ACCESS_KEY_ID: requiredSecret(env.SEEN_MAINTENANCE_R2_ACCESS_KEY_ID, "SEEN_MAINTENANCE_R2_ACCESS_KEY_ID"),
    REGISTRY_R2_SECRET_ACCESS_KEY: requiredSecret(env.SEEN_MAINTENANCE_R2_SECRET_ACCESS_KEY, "SEEN_MAINTENANCE_R2_SECRET_ACCESS_KEY"),
    ...signerTargets(signingRoles),
  };
  if (job.command !== "verify-root-chain") envVars.REGISTRY_FIRESTORE_DATABASE = env.FIRESTORE_DATABASE_ID;
  if (signingRoles.length > 0) envVars.SEEN_SIGNER_CALL_TOKEN = requiredSignerToken(env.SEEN_SIGNER_CALL_TOKEN);
  return {
    entrypoint: ["java", "-jar", JAR, job.command],
    envVars,
    labels: { workload: "seen-registry-job", kind: "maintenance", command: job.command, jobId: job.id },
  };
}

export function maintenanceSigningRoles(command: MaintenanceCommand): TufRole[] {
  switch (command) {
    case "refresh-releases-once":
    case "recover-expired-releases-once":
      return ["releases", "snapshot", "timestamp"];
    case "refresh-security-once":
    case "recover-expired-security-once":
      return ["security", "snapshot", "timestamp"];
    case "verify-root-chain":
      return [];
  }
}

function signerTargets(roles: TufRole[]): Record<string, string> {
  const values: Record<string, string> = {};
  for (const role of roles) {
    const prefix = `REGISTRY_TUF_${role.toUpperCase()}_SIGNER`;
    values[`${prefix}_URL`] = `${SIGNER_ORIGINS[role]}/sign`;
    values[`${prefix}_AUDIENCE`] = SIGNER_ORIGINS[role];
  }
  return values;
}

function validateCommonEnv(env: Env): void {
  if (env.SEEN_ENVIRONMENT !== "development" && env.SEEN_ENVIRONMENT !== "production") throw new ContractError("SEEN_ENVIRONMENT is invalid");
  if (!/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(env.FIRESTORE_PROJECT_ID)) throw new ContractError("FIRESTORE_PROJECT_ID is invalid");
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$/.test(env.FIRESTORE_DATABASE_ID)) throw new ContractError("FIRESTORE_DATABASE_ID is invalid");
  if (!/^https:\/\/[a-z0-9.-]+\.r2\.cloudflarestorage\.com$/.test(env.SEEN_R2_ENDPOINT)) throw new ContractError("SEEN_R2_ENDPOINT is invalid");
  for (const bucket of [env.SEEN_QUARANTINE_BUCKET_NAME, env.SEEN_PUBLIC_BUCKET_NAME, env.SEEN_METADATA_BUCKET_NAME]) {
    if (!/^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])$/.test(bucket)) throw new ContractError("R2 bucket name is invalid");
  }
}

function publicKey(value: string, role: string): string {
  if (!/^[0-9a-f]{64}$/.test(value)) throw new ContractError(`${role} public key is invalid`);
  return value;
}

function requiredSecret(value: string, label: string): string {
  if (!value || value.length > 131_072 || /[\u0000]/.test(value)) throw new ContractError(`${label} is unavailable`);
  return value;
}

function requiredSignerToken(value: string): string {
  const token = requiredSecret(value, "SEEN_SIGNER_CALL_TOKEN");
  if (token.length < 32 || token.length > 512 || /[\u0000-\u001f\u007f]/.test(token)) {
    throw new ContractError("SEEN_SIGNER_CALL_TOKEN is invalid");
  }
  return token;
}
