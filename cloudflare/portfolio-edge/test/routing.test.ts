import { describe, expect, it } from "vitest";
import {
  classifyRoute,
  containerRouteUrl,
  shardFor,
  shouldTryStaticAsset,
  withContainerRouteHost,
} from "../src/routing";

describe("edge routing", () => {
  it("keeps queue diagnostics at the edge", () => {
    expect(classifyRoute("/health/queues")).toBe("health");
  });

  it("keeps Seen execution on its isolated container path", () => {
    expect(classifyRoute("/seen")).toBe("seen-playground");
    expect(classifyRoute("/seen/playground")).toBe("seen-playground");
    expect(classifyRoute("/seenish")).toBe("dynamic");
    expect(classifyRoute("/playground")).toBe("seen-playground");
    expect(classifyRoute("/playground/run")).toBe("seen-playground");
    expect(classifyRoute("/api/seen/run")).toBe("seen-playground");
  });

  it("routes every same-origin ecosystem alias through its canonical dev host", () => {
    for (const project of ["summon", "materia", "sigil", "aether"]) {
      expect(classifyRoute(`/${project}`)).toBe("ecosystem");
      expect(classifyRoute(`/${project}/docs`)).toBe("ecosystem");
      expect(classifyRoute(`/${project}ish`)).toBe("dynamic");

      const headers = withContainerRouteHost(new Headers(), "ecosystem", `/${project}/docs`, "dev");
      expect(headers.get("host")).toBe(`${project}.dev.yousef.codes`);
      expect(containerRouteUrl(
        `https://yousef-portfolio-edge-dev.example.workers.dev/${project}/docs?section=start`,
        "ecosystem",
        "dev",
      ).href).toBe(`https://${project}.dev.yousef.codes/docs?section=start`);
    }
  });

  it("uses production project hosts only for the production environment", () => {
    expect(withContainerRouteHost(new Headers(), "ecosystem", "/summon", "prod").get("host"))
      .toBe("summon.yousef.codes");
    expect(containerRouteUrl("https://www.yousef.codes/seen", "seen-playground", "prod").href)
      .toBe("https://seen.yousef.codes/");
  });

  it("selects the Seen JVM host router only inside the isolated container hop", () => {
    const publicHeaders = new Headers({ host: "yousef-portfolio-edge-dev.example.workers.dev" });

    expect(withContainerRouteHost(publicHeaders, "seen-playground").get("host")).toBe("seen.dev.yousef.codes");
    expect(containerRouteUrl("https://yousef-portfolio-edge-dev.example.workers.dev/playground?sample=hello", "seen-playground").href)
      .toBe("https://seen.dev.yousef.codes/playground?sample=hello");
    expect(containerRouteUrl("https://yousef-portfolio-edge-dev.example.workers.dev/seen", "seen-playground").href)
      .toBe("https://seen.dev.yousef.codes/");
    expect(containerRouteUrl("https://yousef-portfolio-edge-dev.example.workers.dev/seen/playground?sample=hello", "seen-playground").href)
      .toBe("https://seen.dev.yousef.codes/playground?sample=hello");
    expect(withContainerRouteHost(publicHeaders, "dynamic").get("host")).toBe(
      "yousef-portfolio-edge-dev.example.workers.dev",
    );
    expect(containerRouteUrl("https://yousef-portfolio-edge-dev.example.workers.dev/about", "dynamic").href)
      .toBe("https://yousef-portfolio-edge-dev.example.workers.dev/about");
    expect(publicHeaders.get("host")).toBe("yousef-portfolio-edge-dev.example.workers.dev");
  });

  it("tries Static Assets before ordinary SSR GETs only", () => {
    expect(shouldTryStaticAsset(new Request("https://yousef.codes/summon-logo.png"))).toBe(true);
    expect(shouldTryStaticAsset(new Request("https://yousef.codes/about"))).toBe(true);
    expect(shouldTryStaticAsset(new Request("https://yousef.codes/seen"))).toBe(false);
    for (const project of ["summon", "materia", "sigil", "aether"]) {
      expect(shouldTryStaticAsset(new Request(`https://yousef.codes/${project}`))).toBe(false);
    }
    expect(shouldTryStaticAsset(new Request("https://yousef.codes/playground"))).toBe(false);
    expect(shouldTryStaticAsset(new Request("https://yousef.codes/contact", { method: "POST" }))).toBe(false);
  });

  it("selects only the contracted bounded container IDs", () => {
    for (let index = 0; index < 100; index += 1) {
      expect(shardFor(`session-${index}`, 4)).toBeGreaterThanOrEqual(0);
      expect(shardFor(`session-${index}`, 4)).toBeLessThan(4);
    }
    expect(shardFor("stable-session", 4)).toBe(shardFor("stable-session", 4));
  });
});
