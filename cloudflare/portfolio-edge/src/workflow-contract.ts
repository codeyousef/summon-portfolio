import { isRecord } from "./contracts";
import type { MutationEnvelope } from "./types";

export type PortfolioMigrationJobType =
  | "portfolio.migration.reconcile"
  | "portfolio.photography.backfill"
  | "portfolio.finops.rollups.v2.backfill"
  | "portfolio.finops.rollups.v2.reconcile";

export interface PhotographyBackfillWorkflowAsset {
  photoId: string;
  sourceStorageKey: string;
  contentType: string;
  expectedSha256: string;
  expectedSizeBytes: number;
}

export interface PortfolioMigrationParams {
  jobType?: PortfolioMigrationJobType;
  planId: string;
  aggregateId: string;
  authorityEpoch: number;
  expectedRevision: number;
  requestedAt: string;
  assets?: PhotographyBackfillWorkflowAsset[];
  from?: number;
  toExclusive?: number;
  limit?: number;
  cursor?: string;
}

export function validatePortfolioMigrationParams(value: Readonly<PortfolioMigrationParams>): PortfolioMigrationParams {
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(value.planId)) throw new Error("invalid planId");
  if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(value.aggregateId)) throw new Error("invalid aggregateId");
  if (!Number.isSafeInteger(value.authorityEpoch) || value.authorityEpoch < 0) throw new Error("invalid authorityEpoch");
  if (!Number.isSafeInteger(value.expectedRevision) || value.expectedRevision < 0) throw new Error("invalid expectedRevision");
  if (!Number.isFinite(Date.parse(value.requestedAt))) throw new Error("invalid requestedAt");
  const jobType = value.jobType ?? "portfolio.migration.reconcile";
  if (!new Set<PortfolioMigrationJobType>([
    "portfolio.migration.reconcile",
    "portfolio.photography.backfill",
    "portfolio.finops.rollups.v2.backfill",
    "portfolio.finops.rollups.v2.reconcile",
  ]).has(jobType)) {
    throw new Error("invalid jobType");
  }
  if (jobType === "portfolio.migration.reconcile") {
    if (value.assets !== undefined || hasRollupFields(value)) throw new Error("reconciliation contains unsupported fields");
    return { ...value, jobType };
  }
  if (jobType.startsWith("portfolio.finops.rollups.v2.")) {
    if (value.assets !== undefined) throw new Error("FinOps rollup jobs cannot contain photography assets");
    if (value.authorityEpoch !== 1) throw new Error("invalid FinOps rollup authority");
    if (!Number.isSafeInteger(value.from) || !Number.isSafeInteger(value.toExclusive) ||
        (value.from as number) < 0 || (value.toExclusive as number) <= (value.from as number) ||
        (value.from as number) % 86_400_000 !== 0 || (value.toExclusive as number) % 86_400_000 !== 0) {
      throw new Error("invalid FinOps rollup UTC day range");
    }
    if (jobType === "portfolio.finops.rollups.v2.reconcile") {
      if (value.expectedRevision !== 0 || value.limit !== undefined || value.cursor !== undefined) {
        throw new Error("invalid FinOps rollup reconciliation request");
      }
      return { ...value, jobType };
    }
    const limit = value.limit ?? 100;
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 500) throw new Error("invalid FinOps rollup page limit");
    if (value.cursor !== undefined &&
        (value.cursor.length < 1 || value.cursor.length > 500 || /[\r\n]/.test(value.cursor))) {
      throw new Error("invalid FinOps rollup cursor");
    }
    return { ...value, jobType, limit };
  }
  if (hasRollupFields(value)) throw new Error("photography staging contains unsupported FinOps fields");
  if (value.authorityEpoch !== 1 || value.expectedRevision !== 0) throw new Error("invalid photography staging authority");
  if (!Array.isArray(value.assets) || value.assets.length < 1 || value.assets.length > 25) {
    throw new Error("invalid photography asset inventory");
  }
  const ids = new Set<string>();
  const assets = value.assets.map((asset) => validatePhotographyAsset(asset, ids));
  return { ...value, jobType, assets };
}

export function portfolioMigrationEnvelopeInput(
  params: PortfolioMigrationParams,
  workflowInstanceId: string,
): Omit<MutationEnvelope, "payloadSha256"> {
  const jobType = params.jobType ?? "portfolio.migration.reconcile";
  return {
    id: jobType === "portfolio.photography.backfill"
      ? `photography:${params.planId}`
      : jobType.startsWith("portfolio.finops.rollups.v2.")
        ? `finops-rollups-v2:${jobType.endsWith("backfill") ? "backfill" : "reconcile"}:${params.planId}`
        : `migration:${params.planId}`,
    aggregateType: jobType,
    aggregateId: params.aggregateId,
    expectedRevision: params.expectedRevision,
    authorityEpoch: params.authorityEpoch,
    occurredAt: params.requestedAt,
    payload: jobType === "portfolio.photography.backfill"
      ? { assets: params.assets }
      : jobType === "portfolio.finops.rollups.v2.backfill"
        ? { from: params.from, toExclusive: params.toExclusive, limit: params.limit, cursor: params.cursor }
        : jobType === "portfolio.finops.rollups.v2.reconcile"
          ? { from: params.from, toExclusive: params.toExclusive }
          : { planId: params.planId, workflowInstanceId },
  };
}

function hasRollupFields(value: Readonly<PortfolioMigrationParams>): boolean {
  return value.from !== undefined || value.toExclusive !== undefined || value.limit !== undefined || value.cursor !== undefined;
}

function validatePhotographyAsset(value: unknown, ids: Set<string>): PhotographyBackfillWorkflowAsset {
  if (!isRecord(value)) throw new Error("invalid photography asset");
  const photoId = typeof value.photoId === "string" ? value.photoId : "";
  const sourceStorageKey = typeof value.sourceStorageKey === "string" ? value.sourceStorageKey : "";
  const contentType = typeof value.contentType === "string" ? value.contentType : "";
  const expectedSha256 = typeof value.expectedSha256 === "string" ? value.expectedSha256 : "";
  const expectedSizeBytes = value.expectedSizeBytes;
  const fileName = sourceStorageKey.split("/").at(-1) ?? "";
  if (
    !/^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(photoId) || ids.has(photoId) ||
    sourceStorageKey.length > 512 || !sourceStorageKey.startsWith("photography/") ||
    sourceStorageKey.split("/").some((segment) => !segment || segment === "." || segment === "..") ||
    !fileName.startsWith(`${photoId}.`) ||
    !new Set(["image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm", "video/quicktime"]).has(contentType) ||
    !/^[a-f0-9]{64}$/.test(expectedSha256) || !Number.isSafeInteger(expectedSizeBytes) ||
    (expectedSizeBytes as number) < 1 || (expectedSizeBytes as number) > 2_147_483_647
  ) throw new Error("invalid photography asset");
  ids.add(photoId);
  return { photoId, sourceStorageKey, contentType, expectedSha256, expectedSizeBytes: expectedSizeBytes as number };
}
