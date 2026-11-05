export function isAcknowledgedLegacyGcpRollupConflict(
  environment: "dev" | "prod",
  source: string,
  status: number,
  error: string,
): boolean {
  return environment === "dev"
    && source === "gcp"
    && status === 400
    && error.includes("IdempotencyConflictException: Mutation id finops-rollup:gcp:")
    && error.endsWith("was already used for a different mutation");
}
