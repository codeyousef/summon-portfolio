import { describe, expect, it } from "vitest";
import { appendContainerServerTiming, requiresExtendedColdStart } from "../src/container-state";

describe("extended Container cold-start decision", () => {
  it("skips the redundant port wait only for a healthy Container", () => {
    expect(requiresExtendedColdStart("healthy")).toBe(false);
    for (const status of ["running", "stopping", "stopped", "stopped_with_code"]) {
      expect(requiresExtendedColdStart(status)).toBe(true);
    }
  });

  it("adds bounded no-payload state, start, and origin timings", () => {
    const headers = new Headers({ "server-timing": "app;dur=12.0" });
    appendContainerServerTiming(headers, { stateMs: 1.24, startMs: 20_345.67, originMs: 812.34 });

    expect(headers.get("server-timing")).toBe(
      "app;dur=12.0, cf_container_state;dur=1.2, cf_container_start;dur=20345.7, cf_container_origin;dur=812.3",
    );
  });

  it("omits warm start timing and bounds invalid durations", () => {
    const headers = new Headers();
    appendContainerServerTiming(headers, { stateMs: Number.NaN, originMs: 200_000 });

    expect(headers.get("server-timing")).toBe(
      "cf_container_state;dur=0.0, cf_container_origin;dur=120000.0",
    );
  });
});
