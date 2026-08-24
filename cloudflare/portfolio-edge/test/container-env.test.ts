import { describe, expect, it } from "vitest";
import { portfolioContainerEnvironment } from "../src/container-env";
import type { Env } from "../src/types";

describe("Portfolio Container environment", () => {
  it("forwards canonical in-memory Firestore credentials and migration mode", () => {
    const environment = portfolioContainerEnvironment({
      PORTFOLIO_ENV: "dev",
      PORTFOLIO_CONTAINER_POOL_VERSION: "v2",
      FIRESTORE_PROJECT_ID: "portfolio-476219",
      FIRESTORE_DATABASE_ID: "portfolio-me-prod",
      FIRESTORE_WRITE_MODE: "target",
      FIRESTORE_MIGRATION_PROOF_ID: "portfolio-me-prod-initial-v1",
      FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64: "opaque-base64-secret",
      PORTFOLIO_CANONICAL_ORIGIN: "https://yousef.codes",
      EDGE_ORIGIN_TOKEN: "opaque-origin-token",
      PORTFOLIO_MIGRATION_EXECUTION_ENABLED: "false",
      PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED: "false",
      FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY: "false",
      FINOPS_SHARDED_ROLLUPS_READY: "false",
      FINOPS_SHARDED_ROLLUP_WRITES_ENABLED: "false",
      FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED: "false",
      FINOPS_COVERAGE_START_DATE: "2026-08-24",
      PHOTOGRAPHY_WRITE_MODE: "target",
      PHOTOGRAPHY_UPLOAD_BUCKET: "portfolio-uploads",
      PHOTOGRAPHY_UPLOAD_PREFIX: "photography",
      PHOTOGRAPHY_MAX_UPLOAD_BYTES: "15728640",
      PHOTOGRAPHY_R2_REVERSE_MIRROR: "true",
      FINOPS_RECEIPTS: {} as R2Bucket,
      SAMURAI_FINOPS_IDENTITY_URL: "https://samurai.example/internal/portfolio/finops/identities",
      FINOPS_IDENTITY_READ_TOKEN: "identity-read-token-0000000000000000000000000000",
    } as Env);

    expect(environment.FIRESTORE_WRITE_MODE).toBe("target");
    expect(environment.FIRESTORE_MIGRATION_PROOF_ID).toBe("portfolio-me-prod-initial-v1");
    expect(environment.FIRESTORE_SEED_ON_START).toBe("false");
    expect(environment.ENVIRONMENT).toBe("dev");
    expect(environment.PORTFOLIO_DEBUG_ERRORS).toBe("true");
    expect(environment.SUMMON_MARKETING_URL).toBe("https://summon.dev.yousef.codes");
    expect(environment.DOCS_BASE_URL).toBe("https://summon.dev.yousef.codes/docs");
    expect(environment.MATERIA_MARKETING_URL).toBe("https://materia.dev.yousef.codes");
    expect(environment.MATERIA_DOCS_BASE_URL).toBe("https://materia.dev.yousef.codes/docs");
    expect(environment.SIGIL_MARKETING_URL).toBe("https://sigil.dev.yousef.codes");
    expect(environment.SIGIL_DOCS_BASE_URL).toBe("https://sigil.dev.yousef.codes/docs");
    expect(environment.AETHER_MARKETING_URL).toBe("https://aether.dev.yousef.codes");
    expect(environment.AETHER_DOCS_BASE_URL).toBe("https://aether.dev.yousef.codes/docs");
    expect(environment.SEEN_MARKETING_URL).toBe("https://seen.dev.yousef.codes");
    expect(environment.SEEN_DOCS_BASE_URL).toBe("https://seen.dev.yousef.codes/docs");
    expect(environment.FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64).toBe("opaque-base64-secret");
    expect(environment.GOOGLE_CLOUD_PROJECT).toBe("portfolio-476219");
    expect(environment.PORTFOLIO_MIGRATION_EXECUTION_ENABLED).toBe("false");
    expect(environment.PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED).toBe("false");
    expect(environment.FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY).toBe("false");
    expect(environment.FINOPS_SHARDED_ROLLUPS_READY).toBe("false");
    expect(environment.FINOPS_SHARDED_ROLLUP_WRITES_ENABLED).toBe("false");
    expect(environment.FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED).toBe("false");
    expect(environment.FINOPS_COVERAGE_START_DATE).toBe("2026-08-24");
    expect(environment.FINOPS_RECEIPT_BASE_URL).toBe("https://yousef.codes/internal/finops");
    expect(environment.SAMURAI_FINOPS_IDENTITY_URL).toBe("https://samurai.example/internal/portfolio/finops/identities");
    expect(environment.FINOPS_IDENTITY_READ_TOKEN).toBe("identity-read-token-0000000000000000000000000000");
    expect(environment).not.toHaveProperty("GOOGLE_SERVICE_ACCOUNT_JSON_B64");
    expect(environment).not.toHaveProperty("GOOGLE_APPLICATION_CREDENTIALS");
  });

  it("uses canonical production project hosts only for the production container", () => {
    const environment = portfolioContainerEnvironment({
      PORTFOLIO_ENV: "prod",
      FIRESTORE_PROJECT_ID: "portfolio-476219",
      FIRESTORE_DATABASE_ID: "portfolio-me-prod",
      FIRESTORE_WRITE_MODE: "target",
      FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64: "opaque-base64-secret",
      PORTFOLIO_CANONICAL_ORIGIN: "https://www.yousef.codes",
      EDGE_ORIGIN_TOKEN: "opaque-origin-token",
      FINOPS_INTERNAL_INGEST_TOKEN: "opaque-ingest-token",
      PORTFOLIO_MIGRATION_EXECUTION_ENABLED: "false",
      PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED: "false",
      FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY: "false",
      FINOPS_SHARDED_ROLLUPS_READY: "false",
      FINOPS_SHARDED_ROLLUP_WRITES_ENABLED: "false",
      FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED: "false",
      FINOPS_COVERAGE_START_DATE: "2026-08-24",
      PHOTOGRAPHY_WRITE_MODE: "target",
      PHOTOGRAPHY_UPLOAD_BUCKET: "portfolio-uploads",
      PHOTOGRAPHY_UPLOAD_PREFIX: "photography",
      PHOTOGRAPHY_MAX_UPLOAD_BYTES: "15728640",
      PHOTOGRAPHY_R2_REVERSE_MIRROR: "false",
    } as Env);

    expect(environment.SUMMON_MARKETING_URL).toBe("https://summon.yousef.codes");
    expect(environment.MATERIA_MARKETING_URL).toBe("https://materia.yousef.codes");
    expect(environment.SIGIL_MARKETING_URL).toBe("https://sigil.yousef.codes");
    expect(environment.AETHER_MARKETING_URL).toBe("https://aether.yousef.codes");
    expect(environment.SEEN_MARKETING_URL).toBe("https://seen.yousef.codes");
  });

  it("does not advertise receipt storage to the Container before its binding exists", () => {
    const environment = portfolioContainerEnvironment({
      PORTFOLIO_ENV: "dev",
      PORTFOLIO_CONTAINER_POOL_VERSION: "v2",
      FIRESTORE_PROJECT_ID: "portfolio-476219",
      FIRESTORE_DATABASE_ID: "portfolio-me-dev",
      FIRESTORE_WRITE_MODE: "source",
      FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64: "opaque-base64-secret",
      PORTFOLIO_CANONICAL_ORIGIN: "https://yousef.codes",
      EDGE_ORIGIN_TOKEN: "opaque-origin-token",
      FINOPS_INTERNAL_INGEST_TOKEN: "opaque-ingest-token",
      PORTFOLIO_MIGRATION_EXECUTION_ENABLED: "false",
      PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED: "false",
      FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY: "false",
      FINOPS_SHARDED_ROLLUPS_READY: "false",
      FINOPS_SHARDED_ROLLUP_WRITES_ENABLED: "false",
      FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED: "false",
      FINOPS_COVERAGE_START_DATE: "2026-08-24",
      PHOTOGRAPHY_WRITE_MODE: "source",
      PHOTOGRAPHY_UPLOAD_BUCKET: "portfolio-uploads",
      PHOTOGRAPHY_UPLOAD_PREFIX: "photography",
      PHOTOGRAPHY_MAX_UPLOAD_BYTES: "15728640",
      PHOTOGRAPHY_R2_REVERSE_MIRROR: "true",
    } as Env);

    expect(environment).not.toHaveProperty("FINOPS_RECEIPT_BASE_URL");
    expect(environment).not.toHaveProperty("FINOPS_IDENTITY_READ_TOKEN");
    expect(environment).not.toHaveProperty("FIRESTORE_MIGRATION_PROOF_ID");
  });
});
