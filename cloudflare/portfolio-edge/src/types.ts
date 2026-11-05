import type { PortfolioSessionDO } from "./durable-object";
import type { PortfolioSsrContainer, SeenPlaygroundContainer } from "./containers";
import type { PortfolioMigrationParams } from "./workflow";

export interface Env {
  ASSETS: Fetcher;
  PORTFOLIO_MEDIA: R2Bucket;
  PORTFOLIO_DOCS: R2Bucket;
  PORTFOLIO_LOGS: R2Bucket;
  FINOPS_RECEIPTS?: R2Bucket;
  PORTFOLIO_ASYNC_QUEUE: Queue<MutationEnvelope>;
  FINOPS_INGEST_QUEUE: Queue<MutationEnvelope>;
  FINOPS_INGEST_DLQ: Queue<DeadLetterReceipt>;
  PORTFOLIO_ASYNC_DLQ: Queue<DeadLetterReceipt>;
  PORTFOLIO_ASYNC_PARKING: Queue<DeadLetterReceipt>;
  PORTFOLIO_ASYNC_DLQ_NAME: string;
  FINOPS_INGEST_QUEUE_NAME: string;
  PORTFOLIO_SESSIONS: DurableObjectNamespace<PortfolioSessionDO>;
  PORTFOLIO_SSR: DurableObjectNamespace<PortfolioSsrContainer>;
  SEEN_PLAYGROUND: DurableObjectNamespace<SeenPlaygroundContainer>;
  PORTFOLIO_MIGRATION_WORKFLOW: Workflow<PortfolioMigrationParams>;
  PORTFOLIO_ENV: "dev" | "prod";
  PORTFOLIO_CONTAINER_POOL_VERSION: string;
  PORTFOLIO_CANONICAL_ORIGIN: string;
  FIRESTORE_PROJECT_ID: string;
  FIRESTORE_DATABASE_ID: string;
  FIRESTORE_WRITE_MODE: "source" | "dual" | "target";
  FIRESTORE_MIGRATION_PROOF_ID?: string;
  PORTFOLIO_SESSION_COOKIE: string;
  PORTFOLIO_MIGRATION_EXECUTION_ENABLED: "true" | "false";
  PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED: "true" | "false";
  FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY: "true" | "false";
  FINOPS_SHARDED_ROLLUP_WRITES_ENABLED: "true" | "false";
  FINOPS_SHARDED_ROLLUPS_READY: "true" | "false";
  FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED: "true" | "false";
  FINOPS_COVERAGE_START_DATE: string;
  PHOTOGRAPHY_WRITE_MODE: "source" | "dual" | "target";
  PHOTOGRAPHY_UPLOAD_BUCKET: string;
  PHOTOGRAPHY_UPLOAD_PREFIX: string;
  PHOTOGRAPHY_MAX_UPLOAD_BYTES: string;
  PHOTOGRAPHY_R2_REVERSE_MIRROR: "true" | "false";
  FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64: string;
  EDGE_ORIGIN_TOKEN: string;
  FINOPS_INTERNAL_INGEST_TOKEN: string;
  CLOUDFLARE_ACCOUNT_ID?: string;
  CLOUDFLARE_BILLING_API_TOKEN?: string;
  STRIPE_FINOPS_READ_KEY?: string;
  GCP_FINOPS_BILLING_PROJECT_ID?: string;
  GCP_FINOPS_BILLING_DATASET?: string;
  GCP_FINOPS_BILLING_TABLE?: string;
  GCP_FINOPS_BILLING_LOCATION?: string;
  GCP_FINOPS_MAX_BYTES_BILLED?: string;
  GCP_WIF_ACCESS_BROKER_URL?: string;
  GCP_WIF_ACCESS_CLIENT_ID?: string;
  GCP_WIF_ACCESS_CLIENT_SECRET?: string;
  GCP_WIF_ACCESS_ISSUER?: string;
  GCP_WIF_ACCESS_AUDIENCE?: string;
  GCP_WIF_PROVIDER_AUDIENCE?: string;
  GCP_WIF_SERVICE_ACCOUNT?: string;
  FINOPS_IDENTITY_READ_TOKEN?: string;
  SAMURAI_FINOPS_IDENTITY_URL?: string;
  SAMURAI_FINOPS_ACCESS_CLIENT_ID?: string;
  SAMURAI_FINOPS_ACCESS_CLIENT_SECRET?: string;
}

export interface MutationEnvelope {
  id: string;
  aggregateType: string;
  aggregateId: string;
  expectedRevision: number;
  authorityEpoch: number;
  occurredAt: string;
  payloadSha256: string;
  payload: unknown;
}

export interface MutationReceipt {
  id: string;
  committedEpoch: number;
  newRevision: number;
  firestoreCommitTime: string;
  mirrorState: "pending" | "mirrored" | "failed";
}

export interface PhotographyStagingReceipt {
  id: string;
  committedEpoch: number;
  newRevision: number;
  stagedAt: string;
  mirrorState: "staged";
  assets: PhotographyStagingAssetReceipt[];
}

export interface PhotographyStagingAssetReceipt {
  photoId: string;
  sourceStorageKey: string;
  contentAddressedStorageKey: string;
  sha256: string;
  sizeBytes: number;
  contentType: string;
}

export interface DeadLetterReceipt {
  envelopeId: string | null;
  aggregateType: string | null;
  payloadSha256: string | null;
  sourceMessageId: string;
  attempts: number;
  reason: string;
  failedAt: string;
}
