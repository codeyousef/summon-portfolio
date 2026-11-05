import { WorkflowEntrypoint, type WorkflowEvent, type WorkflowStep } from "cloudflare:workers";
import { hashLocation, sha256Hex } from "./contracts";
import type { RegistryJobStatus } from "./containers";
import type { RegistryJob } from "./container-env";
import type { Env, MaintenanceWorkflowParams, PromotionWorkflowParams } from "./types";

const POLL_ATTEMPTS = 120;

export class SeenPromotionWorkflow extends WorkflowEntrypoint<Env, PromotionWorkflowParams> {
  override async run(event: WorkflowEvent<PromotionWorkflowParams>, step: WorkflowStep): Promise<RegistryJobStatus> {
    const operation = event.payload.operation;
    const inputSha256 = await sha256Hex(new TextEncoder().encode(JSON.stringify(operation)));
    const promotion = promotionStub(this.env, operation.aggregateId);
    await step.do("claim promotion", async () => promotion.claim({
      idempotencyKey: operation.id,
      aggregateId: operation.aggregateId,
      expectedRevision: operation.expectedRevision,
      inputSha256,
    }));
    try {
      const result = await runContainerJob(this.env, step, { id: operation.id, kind: "promotion" });
      await step.do("complete promotion", async () => promotion.finish(operation.id, "complete", null));
      return result;
    } catch (error) {
      const detail = error instanceof Error ? error.message.slice(0, 256) : "registry job failed";
      await step.do("fail promotion", async () => promotion.finish(operation.id, "failed", detail));
      throw error;
    }
  }
}

export class SeenMaintenanceWorkflow extends WorkflowEntrypoint<Env, MaintenanceWorkflowParams> {
  override async run(event: WorkflowEvent<MaintenanceWorkflowParams>, step: WorkflowStep): Promise<RegistryJobStatus> {
    const operation = event.payload.operation;
    return runContainerJob(this.env, step, { id: operation.id, kind: "maintenance", command: operation.command });
  }
}

async function runContainerJob(env: Env, step: WorkflowStep, job: RegistryJob): Promise<RegistryJobStatus> {
  const stub = containerStub(env, job.id);
  await step.do("start registry job", { retries: { limit: 3, delay: "5 seconds", backoff: "exponential" } }, async () => {
    return stub.startJob(job);
  });
  for (let attempt = 0; attempt < POLL_ATTEMPTS; attempt += 1) {
    await step.sleep(`wait for registry job ${attempt}`, "5 seconds");
    const status = await step.do(`read registry job status ${attempt}`, async () => stub.jobStatus(job.id));
    if (status.state === "complete") return status;
    if (status.state === "failed") throw new Error(`registry job exited unsuccessfully (${status.exitCode ?? "unknown"})`);
  }
  throw new Error("registry job exceeded the ten-minute Workflow bound");
}

function containerStub(env: Env, id: string): DurableObjectStub<import("./containers").SeenRegistryJobContainer> {
  const namespace = env.SEEN_REGISTRY_JOBS.jurisdiction("us");
  return namespace.getByName(`job:${id}`, { locationHint: hashLocation(id) });
}

function promotionStub(env: Env, aggregateId: string): DurableObjectStub<import("./coordinators").SeenPromotionDO> {
  const namespace = env.SEEN_PROMOTIONS.jurisdiction("us");
  return namespace.getByName(`promotion:${aggregateId}`, { locationHint: hashLocation(aggregateId) });
}
