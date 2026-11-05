import { readBoundedBody } from "./bounded-body";

export const CONNECTOR_FAILURE_CODES = [
  "configuration",
  "workload_identity",
  "provider_authorization",
  "provider_api",
  "provider_schema",
  "bigquery_authorization",
  "bigquery_query",
  "bigquery_schema",
  "query_limit",
  "pagination_limit",
  "queue_delivery",
  "record_validation",
  "unsupported_currency",
  "cost_unavailable",
  "restricted_api_unavailable",
  "unknown",
] as const;

export type ConnectorFailureCode = typeof CONNECTOR_FAILURE_CODES[number];

export class ConnectorFailure extends Error {
  constructor(
    readonly failureCode: ConnectorFailureCode,
    message = "FinOps connector failed",
  ) {
    super(message);
    this.name = "ConnectorFailure";
  }
}

export function connectorFailure(
  code: ConnectorFailureCode,
  error: unknown,
  message = "FinOps connector failed",
): ConnectorFailure {
  if (error instanceof ConnectorFailure) return error;
  return new ConnectorFailure(code, message);
}

export function connectorFailureCode(error: unknown): ConnectorFailureCode {
  return error instanceof ConnectorFailure ? error.failureCode : "unknown";
}

const FAILURE_PRIORITY: Readonly<Record<ConnectorFailureCode, number>> = {
  configuration: 0,
  workload_identity: 1,
  provider_authorization: 2,
  bigquery_authorization: 3,
  query_limit: 4,
  pagination_limit: 5,
  provider_api: 6,
  bigquery_query: 7,
  provider_schema: 8,
  bigquery_schema: 9,
  restricted_api_unavailable: 10,
  record_validation: 11,
  unsupported_currency: 12,
  cost_unavailable: 13,
  queue_delivery: 14,
  unknown: 15,
};

export function highestPriorityFailureCode(
  codes: Iterable<ConnectorFailureCode>,
): ConnectorFailureCode | undefined {
  return [...codes].sort((left, right) => FAILURE_PRIORITY[left] - FAILURE_PRIORITY[right])[0];
}

export async function parseBoundedJson(
  response: Response,
  maximumBytes: number,
  failureCode: ConnectorFailureCode,
): Promise<unknown> {
  if (!Number.isSafeInteger(maximumBytes) || maximumBytes < 1) {
    throw new Error("invalid response byte limit");
  }
  const declaredLength = response.headers.get("content-length");
  if (declaredLength !== null) {
    const length = Number(declaredLength);
    if (!Number.isSafeInteger(length) || length < 0 || length > maximumBytes) {
      throw new ConnectorFailure(failureCode, "Provider response exceeds its byte limit");
    }
  }
  if (response.body === null) throw new ConnectorFailure(failureCode, "Provider response body is missing");
  const bytes = await readBoundedBody(response.body, maximumBytes);
  if (bytes === null) throw new ConnectorFailure(failureCode, "Provider response exceeds its byte limit");
  try {
    return JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes)) as unknown;
  } catch {
    throw new ConnectorFailure(failureCode, "Provider response is not valid JSON");
  }
}
