import { describe, expect, it } from "vitest";
import { maintenanceSigningRoles, registryJobLaunch } from "../src/container-env";
import type { Env } from "../src/types";

const env = {
  SEEN_ENVIRONMENT: "development",
  SEEN_REPOSITORY_ID: "seen-dev-registry-v1",
  SEEN_REGISTRY_ORIGIN: "https://seen.dev.yousef.codes/packages",
  SEEN_OBJECT_PREFIX: "v1",
  FIRESTORE_PROJECT_ID: "portfolio-476219",
  FIRESTORE_DATABASE_ID: "seen-registry-dev",
  SEEN_QUARANTINE_BUCKET_NAME: "yousef-seen-quarantine-dev",
  SEEN_PUBLIC_BUCKET_NAME: "yousef-seen-public-dev",
  SEEN_METADATA_BUCKET_NAME: "yousef-seen-metadata-dev",
  SEEN_R2_ENDPOINT: "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com",
  SEEN_RELEASES_PUBLIC_KEY_HEX: "1".repeat(64),
  SEEN_SECURITY_PUBLIC_KEY_HEX: "2".repeat(64),
  SEEN_SNAPSHOT_PUBLIC_KEY_HEX: "3".repeat(64),
  SEEN_TIMESTAMP_PUBLIC_KEY_HEX: "4".repeat(64),
  FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64: "encoded-service-account",
  SEEN_PROMOTION_R2_ACCESS_KEY_ID: "promotion-access",
  SEEN_PROMOTION_R2_SECRET_ACCESS_KEY: "promotion-secret",
  SEEN_MAINTENANCE_R2_ACCESS_KEY_ID: "maintenance-access",
  SEEN_MAINTENANCE_R2_SECRET_ACCESS_KEY: "maintenance-secret",
  SEEN_SIGNER_CALL_TOKEN: "s".repeat(32),
} as Env;

describe("registry Container capability environments", () => {
  it("gives promotion exactly quarantine, public, and metadata buckets", () => {
    const launch = registryJobLaunch({ id: "promotion:12345678", kind: "promotion" }, env);
    expect(launch.entrypoint.at(-1)).toBe("promote-once");
    expect(launch.envVars).toMatchObject({
      REGISTRY_QUARANTINE_BUCKET: "yousef-seen-quarantine-dev",
      REGISTRY_PUBLIC_BUCKET: "yousef-seen-public-dev",
      REGISTRY_METADATA_BUCKET: "yousef-seen-metadata-dev",
      REGISTRY_R2_ACCESS_KEY_ID: "promotion-access",
    });
    expect(Object.keys(launch.envVars)).not.toContain("REGISTRY_PRIVATE_BUCKET");
    expect(Object.keys(launch.envVars)).not.toContain("REGISTRY_EVIDENCE_BUCKET");
    expect(Object.keys(launch.envVars)).not.toContain("REGISTRY_BACKUP_BUCKET");
    expect(Object.keys(launch.envVars).filter((key) => key.endsWith("_SIGNER_URL"))).toHaveLength(3);
  });

  it("keeps maintenance metadata-only and role-scoped", () => {
    const launch = registryJobLaunch({
      id: "maintenance:12345678",
      kind: "maintenance",
      command: "refresh-security-once",
    }, env);
    expect(launch.envVars.REGISTRY_METADATA_BUCKET).toBe("yousef-seen-metadata-dev");
    expect(launch.envVars.REGISTRY_QUARANTINE_BUCKET).toBeUndefined();
    expect(launch.envVars.REGISTRY_PUBLIC_BUCKET).toBeUndefined();
    expect(maintenanceSigningRoles("refresh-security-once")).toEqual(["security", "snapshot", "timestamp"]);
    expect(launch.envVars.REGISTRY_TUF_RELEASES_SIGNER_URL).toBeUndefined();
  });

  it("does not give root verification Firestore or signer authority", () => {
    const launch = registryJobLaunch({
      id: "maintenance:root1234",
      kind: "maintenance",
      command: "verify-root-chain",
    }, env);
    expect(launch.envVars.REGISTRY_FIRESTORE_DATABASE).toBeUndefined();
    expect(Object.keys(launch.envVars).some((key) => key.endsWith("_SIGNER_URL"))).toBe(false);
    expect(launch.envVars.SEEN_SIGNER_CALL_TOKEN).toBeUndefined();
  });

  it("prefers a single keyless Firestore credential and rejects mixed credentials", () => {
    const { FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64: _removedServiceAccount, ...base } = env;
    const keyless = {
      ...base,
      FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64: "encoded-external-account",
    } as Env;
    const launch = registryJobLaunch({ id: "promotion:keyless1", kind: "promotion" }, keyless);

    expect(launch.envVars.FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64).toBe("encoded-external-account");
    expect(launch.envVars.FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64).toBeUndefined();
    expect(() => registryJobLaunch({ id: "promotion:mixed123", kind: "promotion" }, {
      ...env,
      FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64: "encoded-external-account",
    })).toThrow(/exactly one Firestore credential/);
  });
});
