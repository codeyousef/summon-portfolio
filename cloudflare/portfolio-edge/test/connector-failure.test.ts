import { describe, expect, it } from "vitest";
import {
  CONNECTOR_FAILURE_CODES,
  ConnectorFailure,
  highestPriorityFailureCode,
  parseBoundedJson,
} from "../src/connector-failure";

describe("connector failure contract", () => {
  it("keeps the cross-runtime persisted taxonomy fixed", () => {
    expect(CONNECTOR_FAILURE_CODES).toEqual([
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
    ]);
  });

  it("selects one deterministic reason when several coverage gaps coexist", () => {
    expect(highestPriorityFailureCode([
      "cost_unavailable",
      "restricted_api_unavailable",
      "provider_authorization",
      "record_validation",
    ])).toBe("provider_authorization");
    expect(highestPriorityFailureCode([])).toBeUndefined();
  });

  it("stops streaming provider JSON at the configured byte boundary", async () => {
    const response = new Response(new TextEncoder().encode("{\"oversized\":true}"));

    await expect(parseBoundedJson(response, 8, "provider_schema")).rejects.toMatchObject({
      name: "ConnectorFailure",
      failureCode: "provider_schema",
    } satisfies Partial<ConnectorFailure>);
  });
});
