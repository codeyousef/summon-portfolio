import type { Env } from "./types";

type HealthEnv = Pick<
  Env,
  "PORTFOLIO_ENV" | "FIRESTORE_DATABASE_ID" | "FIRESTORE_WRITE_MODE" | "FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY"
>;

export function healthResponseBody(env: HealthEnv, pathname: string) {
  return {
    status: "ok",
    environment: env.PORTFOLIO_ENV,
    firestoreDatabase: env.FIRESTORE_DATABASE_ID,
    firestoreConfiguredTargetDatabase: env.FIRESTORE_DATABASE_ID,
    firestoreAuthorityDatabase: env.FIRESTORE_WRITE_MODE === "target" ? env.FIRESTORE_DATABASE_ID : "(default)",
    firestoreWriteMode: env.FIRESTORE_WRITE_MODE,
    finOpsAllocationEntryProjectionsReady: env.FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY === "true",
    regionPolicy: ["ME", "WEUR"],
    sessionContract: "portfolio.edge-session.v1",
    ready: pathname === "/health/ready",
  };
}

export function queueHealthResponseBody(
  work: QueueMetrics,
  finOpsWork: QueueMetrics,
  deadLetter: QueueMetrics,
  parking: QueueMetrics,
) {
  return {
    status: deadLetter.backlogCount === 0 && parking.backlogCount === 0 ? "ok" : "degraded",
    work: queueMetricsBody(work),
    finOpsWork: queueMetricsBody(finOpsWork),
    deadLetter: queueMetricsBody(deadLetter),
    parking: queueMetricsBody(parking),
  };
}

function queueMetricsBody(metrics: QueueMetrics) {
  return {
    backlogCount: metrics.backlogCount,
    backlogBytes: metrics.backlogBytes,
    oldestMessageTimestamp: metrics.oldestMessageTimestamp?.toISOString() ?? null,
  };
}
