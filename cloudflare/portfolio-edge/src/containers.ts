import { Container, getContainer } from "@cloudflare/containers";
import { shardFor } from "./routing";
import { portfolioContainerEnvironment } from "./container-env";
import type { Env } from "./types";
import { appendContainerServerTiming, requiresExtendedColdStart } from "./container-state";

export class PortfolioSsrContainer extends Container<Env> {
  override defaultPort = 8080;
  override sleepAfter = "10m";
  override enableInternet = true;
  override pingEndpoint = "/health";
  override envVars = portfolioContainerEnvironment(this.env);

  async fetchWithExtendedColdStart(request: Request): Promise<Response> {
    const stateStartedAt = performance.now();
    const state = await this.getState();
    const stateMs = performance.now() - stateStartedAt;
    let startMs: number | undefined;
    if (requiresExtendedColdStart(state.status)) {
      const startStartedAt = performance.now();
      await this.startAndWaitForPorts({
        cancellationOptions: { portReadyTimeoutMS: 60_000 },
      });
      startMs = performance.now() - startStartedAt;
    }
    const originStartedAt = performance.now();
    const response = await this.containerFetch(request);
    const headers = new Headers(response.headers);
    appendContainerServerTiming(headers, {
      stateMs,
      ...(startMs === undefined ? {} : { startMs }),
      originMs: performance.now() - originStartedAt,
    });
    return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
  }
}

export class SeenPlaygroundContainer extends Container<Env> {
  override defaultPort = 8080;
  override sleepAfter = "1m";
  override enableInternet = false;
  override pingEndpoint = "/health";
  override envVars = {
    PORT: "8080",
    USE_LOCAL_STORE: "true",
    SEEN_PLAYGROUND_ISOLATED: "true",
  };

  async fetchWithExtendedColdStart(request: Request): Promise<Response> {
    const stateStartedAt = performance.now();
    const state = await this.getState();
    const stateMs = performance.now() - stateStartedAt;
    let startMs: number | undefined;
    if (requiresExtendedColdStart(state.status)) {
      const startStartedAt = performance.now();
      await this.startAndWaitForPorts({
        cancellationOptions: { portReadyTimeoutMS: 60_000 },
      });
      startMs = performance.now() - startStartedAt;
    }
    const originStartedAt = performance.now();
    const response = await this.containerFetch(request);
    const headers = new Headers(response.headers);
    appendContainerServerTiming(headers, {
      stateMs,
      ...(startMs === undefined ? {} : { startMs }),
      originMs: performance.now() - originStartedAt,
    });
    return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
  }
}

export function portfolioContainer(sessionId: string, env: Env) {
  const shard = shardFor(sessionId, 4);
  return getContainer(env.PORTFOLIO_SSR, `portfolio-ssr-${env.PORTFOLIO_CONTAINER_POOL_VERSION}-${shard}`);
}

export function seenPlaygroundContainer(sessionId: string, env: Env) {
  const shard = shardFor(sessionId, 2);
  return getContainer(env.SEEN_PLAYGROUND, `seen-playground-${env.PORTFOLIO_CONTAINER_POOL_VERSION}-${shard}`);
}
