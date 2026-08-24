import type { DeadLetterReceipt, Env } from "./types";

export function enforceFinOpsCoverageForQueue(
  queueName: string,
  env: Pick<Env, "FINOPS_INGEST_QUEUE_NAME">,
): boolean {
  return queueName === env.FINOPS_INGEST_QUEUE_NAME;
}

export function deadLetterDestination(
  env: Pick<Env, "FINOPS_INGEST_DLQ" | "PORTFOLIO_ASYNC_DLQ" | "PORTFOLIO_ASYNC_PARKING">,
  options: { parking: boolean; finOps: boolean },
): Queue<DeadLetterReceipt> {
  if (options.parking) return env.PORTFOLIO_ASYNC_PARKING;
  return options.finOps ? env.FINOPS_INGEST_DLQ : env.PORTFOLIO_ASYNC_DLQ;
}
