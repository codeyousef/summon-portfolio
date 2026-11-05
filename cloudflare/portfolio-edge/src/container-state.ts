export function requiresExtendedColdStart(status: string): boolean {
  return status !== "healthy";
}

export interface ContainerTiming {
  stateMs: number;
  startMs?: number;
  originMs: number;
}

export function appendContainerServerTiming(headers: Headers, timing: ContainerTiming): void {
  const metrics = [
    `cf_container_state;dur=${boundedDuration(timing.stateMs)}`,
    timing.startMs === undefined ? null : `cf_container_start;dur=${boundedDuration(timing.startMs)}`,
    `cf_container_origin;dur=${boundedDuration(timing.originMs)}`,
  ].filter((metric): metric is string => metric !== null);
  headers.append("server-timing", metrics.join(", "));
}

function boundedDuration(value: number): string {
  if (!Number.isFinite(value) || value < 0) return "0.0";
  return Math.min(value, 120_000).toFixed(1);
}
