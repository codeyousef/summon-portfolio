import { readBoundedBody } from "./bounded-body";

export async function readAllocationBackfillResult(response: Response): Promise<{
  processed: number;
  residualAllocations: number;
  nextCursor: string | null;
}> {
  const bytes = await readBoundedBody(response.body, 16 * 1024);
  if (bytes === null) throw new Error("FinOps reconciliation response is too large");
  const value = JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes)) as Record<string, unknown>;
  if (!Number.isSafeInteger(value.processed) || (value.processed as number) < 0 || (value.processed as number) > 500) {
    throw new Error("FinOps reconciliation returned an invalid processed count");
  }
  if (!Number.isSafeInteger(value.residualAllocations) || (value.residualAllocations as number) < 0 ||
      (value.residualAllocations as number) > (value.processed as number)) {
    throw new Error("FinOps reconciliation returned an invalid residual count");
  }
  if (value.nextCursor !== null && value.nextCursor !== undefined &&
      (typeof value.nextCursor !== "string" || value.nextCursor.length < 1 || value.nextCursor.length > 500)) {
    throw new Error("FinOps reconciliation returned an invalid cursor");
  }
  return {
    processed: value.processed as number,
    residualAllocations: value.residualAllocations as number,
    nextCursor: (value.nextCursor as string | null | undefined) ?? null,
  };
}

export interface AllocationProjectionReconciliation {
  expectedCount: number;
  actualCount: number;
  missingCount: number;
  unexpectedCount: number;
  mismatchedCount: number;
  invalidCount: number;
  orphanedAllocationCount: number;
  expectedHash: string;
  actualHash: string;
  ready: boolean;
}

export function shouldRunAllocationReconciliation(
  environment: string,
  firestoreWriteMode: string,
  projectionsReady: string,
): boolean {
  return environment === "dev" && firestoreWriteMode === "target" && projectionsReady !== "true";
}

export async function readAllocationProjectionReconciliation(response: Response): Promise<AllocationProjectionReconciliation> {
  const bytes = await readBoundedBody(response.body, 16 * 1024);
  if (bytes === null) throw new Error("FinOps projection reconciliation response is too large");
  const value = JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes)) as Record<string, unknown>;
  const countKeys = [
    "expectedCount",
    "actualCount",
    "missingCount",
    "unexpectedCount",
    "mismatchedCount",
    "invalidCount",
    "orphanedAllocationCount",
  ] as const;
  for (const key of countKeys) {
    if (!Number.isSafeInteger(value[key]) || (value[key] as number) < 0 || (value[key] as number) > 100_000_000) {
      throw new Error(`FinOps projection reconciliation returned an invalid ${key}`);
    }
  }
  if (typeof value.expectedHash !== "string" || !/^[a-f0-9]{64}$/.test(value.expectedHash) ||
      typeof value.actualHash !== "string" || !/^[a-f0-9]{64}$/.test(value.actualHash) ||
      typeof value.ready !== "boolean") {
    throw new Error("FinOps projection reconciliation returned invalid proof fields");
  }
  const reconciled = value.missingCount === 0 && value.unexpectedCount === 0 && value.mismatchedCount === 0 &&
    value.invalidCount === 0 && value.orphanedAllocationCount === 0 && value.expectedCount === value.actualCount &&
    value.expectedHash === value.actualHash;
  if (value.ready !== reconciled) throw new Error("FinOps projection reconciliation readiness contradicts its evidence");
  return value as unknown as AllocationProjectionReconciliation;
}
