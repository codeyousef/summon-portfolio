import type { SeenRegistryJobContainer } from "./containers";
import type { SeenMaintenanceWorkflow, SeenPromotionWorkflow } from "./workflows";
import type { SeenPromotionDO, SeenReservationDO, SeenSigningOrderDO } from "./coordinators";

export type SeenEnvironment = "development" | "production";
export type SeenLocationHint = "enam" | "wnam";

export interface Env {
  SEEN_QUARANTINE: R2Bucket;
  SEEN_PUBLIC: R2Bucket;
  SEEN_METADATA: R2Bucket;
  SEEN_PRIVATE: R2Bucket;
  SEEN_EVIDENCE: R2Bucket;
  SEEN_BACKUP: R2Bucket;
  SEEN_OPERATIONS_QUEUE: Queue<SeenOperation>;
  SEEN_OPERATIONS_DLQ: Queue<SeenDeadLetter>;
  SEEN_RESERVATIONS: DurableObjectNamespace<SeenReservationDO>;
  SEEN_PROMOTIONS: DurableObjectNamespace<SeenPromotionDO>;
  SEEN_SIGNING_ORDER: DurableObjectNamespace<SeenSigningOrderDO>;
  SEEN_REGISTRY_JOBS: DurableObjectNamespace<SeenRegistryJobContainer>;
  SEEN_PROMOTION_WORKFLOW: Workflow<PromotionWorkflowParams>;
  SEEN_MAINTENANCE_WORKFLOW: Workflow<MaintenanceWorkflowParams>;
  SEEN_RELEASES_SIGNER: Fetcher;
  SEEN_SECURITY_SIGNER: Fetcher;
  SEEN_SNAPSHOT_SIGNER: Fetcher;
  SEEN_TIMESTAMP_SIGNER: Fetcher;

  SEEN_ENVIRONMENT: SeenEnvironment;
  SEEN_REPOSITORY_ID: string;
  SEEN_REGISTRY_ORIGIN: string;
  SEEN_OBJECT_PREFIX: string;
  FIRESTORE_PROJECT_ID: string;
  FIRESTORE_DATABASE_ID: string;
  SEEN_QUARANTINE_BUCKET_NAME: string;
  SEEN_PUBLIC_BUCKET_NAME: string;
  SEEN_METADATA_BUCKET_NAME: string;
  SEEN_R2_ENDPOINT: string;
  SEEN_RELEASES_PUBLIC_KEY_HEX: string;
  SEEN_SECURITY_PUBLIC_KEY_HEX: string;
  SEEN_SNAPSHOT_PUBLIC_KEY_HEX: string;
  SEEN_TIMESTAMP_PUBLIC_KEY_HEX: string;

  SEEN_INTERNAL_API_TOKEN: string;
  FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64?: string;
  FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64?: string;
  SEEN_PROMOTION_R2_ACCESS_KEY_ID: string;
  SEEN_PROMOTION_R2_SECRET_ACCESS_KEY: string;
  SEEN_MAINTENANCE_R2_ACCESS_KEY_ID: string;
  SEEN_MAINTENANCE_R2_SECRET_ACCESS_KEY: string;
  SEEN_SIGNER_CALL_TOKEN: string;
}

export type SeenOperation =
  | {
      kind: "promotion";
      id: string;
      aggregateId: string;
      expectedRevision: number;
      requestedAt: string;
    }
  | {
      kind: "maintenance";
      id: string;
      command: MaintenanceCommand;
      requestedAt: string;
    };

export type MaintenanceCommand =
  | "refresh-releases-once"
  | "refresh-security-once"
  | "recover-expired-releases-once"
  | "recover-expired-security-once"
  | "verify-root-chain";

export interface SeenDeadLetter {
  messageId: string;
  failedAt: string;
  reason: string;
  body: unknown;
}

export interface PromotionWorkflowParams {
  operation: Extract<SeenOperation, { kind: "promotion" }>;
}

export interface MaintenanceWorkflowParams {
  operation: Extract<SeenOperation, { kind: "maintenance" }>;
}

export interface SignerBaseEnv {
  SEEN_ENVIRONMENT: SeenEnvironment;
  SEEN_REPOSITORY_ID: string;
  SEEN_SIGNER_ROLE: TufRole;
  SEEN_SIGNER_KEY_ID: string;
  SEEN_SIGNER_AUDIENCE: string;
  SEEN_SIGNER_CALL_TOKEN: string;
  SEEN_SIGNER_GUARD: Fetcher;
  SEEN_SIGNING_ORDER: DurableObjectNamespace<SeenSigningOrderDO>;
}

export interface ReleasesSignerEnv extends SignerBaseEnv {
  SEEN_RELEASES_SIGNING_KEY: CryptoKey;
}

export interface SecuritySignerEnv extends SignerBaseEnv {
  SEEN_SECURITY_SIGNING_KEY: CryptoKey;
}

export interface SnapshotSignerEnv extends SignerBaseEnv {
  SEEN_SNAPSHOT_SIGNING_KEY: CryptoKey;
}

export interface TimestampSignerEnv extends SignerBaseEnv {
  SEEN_TIMESTAMP_SIGNING_KEY: CryptoKey;
  SEEN_METADATA: R2Bucket;
  SEEN_SIGNING_ORDER: DurableObjectNamespace<SeenSigningOrderDO>;
  SEEN_OBJECT_PREFIX: string;
}

export type TufRole = "releases" | "security" | "snapshot" | "timestamp";
export type TufOperation =
  | "release"
  | "security"
  | "bootstrap"
  | "targets-renewal"
  | "targets-rotation:releases"
  | "targets-rotation:security";
