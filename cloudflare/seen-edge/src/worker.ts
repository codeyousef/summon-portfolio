import { ContractError, deadLetter, hashLocation, parseSeenOperation } from "./contracts";
import { readImmutableRegistryObject } from "./r2";
import type { Env, SeenOperation } from "./types";

export default {
  async fetch(request, env): Promise<Response> {
    const publicObject = await readImmutableRegistryObject(request, env);
    if (publicObject) return publicObject;
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return Response.json({ status: "ok", service: "seen-edge", environment: env.SEEN_ENVIRONMENT }, {
        headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" },
      });
    }
    if (!url.pathname.startsWith("/internal/")) return empty(404);
    if (!authorized(request, env.SEEN_INTERNAL_API_TOKEN)) return empty(401);
    if (request.method === "POST" && url.pathname === "/internal/v1/operations") {
      try {
        const operation = parseSeenOperation(await readJson(request, 8192));
        await env.SEEN_OPERATIONS_QUEUE.send(operation, { contentType: "json" });
        return Response.json({ accepted: true, id: operation.id }, {
          status: 202,
          headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" },
        });
      } catch (error) {
        if (error instanceof ContractError || error instanceof SyntaxError) return empty(400);
        throw error;
      }
    }
    const reserveMatch = /^\/internal\/v1\/reservations\/([^/]+)$/.exec(url.pathname);
    if (request.method === "POST" && reserveMatch) {
      try {
        const aggregateId = decodeURIComponent(requireValue(reserveMatch[1]));
        const body = await readJson(request, 8192);
        if (!isRecord(body) || body.aggregateId !== aggregateId) return empty(400);
        const stub = reservationStub(env, aggregateId);
        return Response.json(await stub.reserve({
          idempotencyKey: string(body.idempotencyKey),
          aggregateId,
          expectedRevision: number(body.expectedRevision),
          payloadSha256: string(body.payloadSha256),
          expiresAt: string(body.expiresAt),
        }), { headers: { "Cache-Control": "no-store" } });
      } catch (error) {
        if (error instanceof ContractError || error instanceof URIError || error instanceof SyntaxError) return empty(409);
        throw error;
      }
    }
    return empty(404);
  },

  async queue(batch, env): Promise<void> {
    for (const message of batch.messages) {
      try {
        const operation = parseSeenOperation(message.body);
        await createWorkflow(operation, env);
        message.ack();
      } catch (error) {
        if (error instanceof ContractError) {
          await env.SEEN_OPERATIONS_DLQ.send(deadLetter(message.id, message.body, error.message), { contentType: "json" });
          message.ack();
        } else {
          message.retry({ delaySeconds: Math.min(300, 2 ** Math.min(message.attempts, 8)) });
        }
      }
    }
  },
} satisfies ExportedHandler<Env>;

async function createWorkflow(operation: SeenOperation, env: Env): Promise<void> {
  if (operation.kind === "promotion") {
    await createWorkflowInstance(env.SEEN_PROMOTION_WORKFLOW, operation.id, { operation }, hashLocation(operation.id));
  } else {
    await createWorkflowInstance(env.SEEN_MAINTENANCE_WORKFLOW, operation.id, { operation }, hashLocation(operation.id));
  }
}

async function createWorkflowInstance<Params>(
  workflow: Workflow<Params>,
  id: string,
  params: Params,
  locationHint: "enam" | "wnam",
): Promise<void> {
  try {
    await workflow.create({
      id,
      params,
      locationHint,
      retention: { successRetention: "7 days", errorRetention: "30 days" },
    });
  } catch (error) {
    const instance = await workflow.get(id);
    const status = await instance.status();
    if (status.status === "unknown") throw error;
  }
}

function reservationStub(env: Env, aggregateId: string): DurableObjectStub<import("./coordinators").SeenReservationDO> {
  return env.SEEN_RESERVATIONS.jurisdiction("us").getByName(`reservation:${aggregateId}`, {
    locationHint: hashLocation(aggregateId),
  });
}

function authorized(request: Request, expected: string): boolean {
  const value = request.headers.get("Authorization");
  if (!value?.startsWith("Bearer ")) return false;
  const encoder = new TextEncoder();
  const supplied = encoder.encode(value.slice(7));
  const wanted = encoder.encode(expected);
  return supplied.byteLength === wanted.byteLength && crypto.subtle.timingSafeEqual(supplied, wanted);
}

async function readJson(request: Request, maximumBytes: number): Promise<unknown> {
  const declared = Number(request.headers.get("Content-Length") ?? "0");
  if (declared > maximumBytes) throw new ContractError("request body is too large");
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.byteLength === 0 || body.byteLength > maximumBytes) throw new ContractError("request body is invalid");
  return JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(body)) as unknown;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function string(value: unknown): string {
  if (typeof value !== "string") throw new ContractError("value is not a string");
  return value;
}

function number(value: unknown): number {
  if (!Number.isSafeInteger(value)) throw new ContractError("value is not an integer");
  return value as number;
}

function requireValue(value: string | undefined): string {
  if (value === undefined) throw new ContractError("route value is missing");
  return value;
}

function empty(status: number): Response {
  const headers = new Headers({ "Content-Length": "0", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" });
  if (status === 401) headers.set("WWW-Authenticate", "Bearer realm=\"seen-edge-internal\"");
  return new Response(null, { status, headers });
}
