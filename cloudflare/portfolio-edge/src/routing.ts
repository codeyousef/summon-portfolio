const SEEN_PATHS = ["/playground", "/api/seen/run"] as const;
const SEEN_PREFIX = "/seen";
const ECOSYSTEM_PREFIXES = ["summon", "materia", "sigil", "aether"] as const;
const INTERNAL_PREFIX = "/internal/";

export type RouteKind =
  | "health"
  | "internal"
  | "media"
  | "docs"
  | "ecosystem"
  | "seen-playground"
  | "dynamic";

type DeploymentEnvironment = "dev" | "prod";

function ecosystemProject(pathname: string): string | undefined {
  return ECOSYSTEM_PREFIXES.find((project) =>
    pathname === `/${project}` || pathname.startsWith(`/${project}/`),
  );
}

function projectHost(project: string, environment: DeploymentEnvironment): string {
  return `${project}.${environment === "dev" ? "dev." : ""}yousef.codes`;
}

export function classifyRoute(pathname: string): RouteKind {
  if (pathname === "/health" || pathname === "/health/ready" || pathname === "/health/queues") return "health";
  if (pathname.startsWith(INTERNAL_PREFIX)) return "internal";
  if (pathname.startsWith("/cdn/media/")) return "media";
  if (pathname.startsWith("/cdn/docs/")) return "docs";
  if (
    pathname === SEEN_PREFIX ||
    pathname.startsWith(`${SEEN_PREFIX}/`) ||
    SEEN_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`))
  ) {
    return "seen-playground";
  }
  if (ecosystemProject(pathname)) return "ecosystem";
  return "dynamic";
}

export function withContainerRouteHost(
  headers: Headers,
  kind: RouteKind,
  pathname = "/",
  environment: DeploymentEnvironment = "dev",
): Headers {
  const routed = new Headers(headers);
  if (kind === "seen-playground") routed.set("host", projectHost("seen", environment));
  if (kind === "ecosystem") {
    const project = ecosystemProject(pathname);
    if (project) routed.set("host", projectHost(project, environment));
  }
  return routed;
}

export function containerRouteUrl(
  requestUrl: string,
  kind: RouteKind,
  environment: DeploymentEnvironment = "dev",
): URL {
  const routed = new URL(requestUrl);
  if (kind === "seen-playground") {
    routed.host = projectHost("seen", environment);
    if (routed.pathname === SEEN_PREFIX) routed.pathname = "/";
    else if (routed.pathname.startsWith(`${SEEN_PREFIX}/`)) {
      routed.pathname = routed.pathname.slice(SEEN_PREFIX.length);
    }
  }
  if (kind === "ecosystem") {
    const project = ecosystemProject(routed.pathname);
    if (project) {
      routed.host = projectHost(project, environment);
      routed.pathname = routed.pathname === `/${project}`
        ? "/"
        : routed.pathname.slice(project.length + 1);
    }
  }
  return routed;
}

export function shouldTryStaticAsset(request: Request): boolean {
  if (request.method !== "GET" && request.method !== "HEAD") return false;
  const kind = classifyRoute(new URL(request.url).pathname);
  return kind === "dynamic";
}

export function shardFor(value: string, shardCount: number): number {
  if (!Number.isSafeInteger(shardCount) || shardCount < 1) throw new Error("shardCount must be positive");
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0) % shardCount;
}
