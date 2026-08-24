import { WorkflowEntrypoint, type WorkflowEvent, type WorkflowStep } from "cloudflare:workers";
import { NonRetryableError } from "cloudflare:workflows";
import { makeMutationEnvelope } from "./contracts";
import type { Env } from "./types";
import {
  portfolioMigrationEnvelopeInput,
  validatePortfolioMigrationParams,
  type PortfolioMigrationParams,
} from "./workflow-contract";

export type { PortfolioMigrationParams } from "./workflow-contract";

export class PortfolioMigrationWorkflow extends WorkflowEntrypoint<Env, PortfolioMigrationParams> {
  override async run(event: Readonly<WorkflowEvent<PortfolioMigrationParams>>, step: WorkflowStep): Promise<unknown> {
    const params = await step.do("validate migration request", async () => {
      try {
        return validatePortfolioMigrationParams(event.payload);
      } catch (error) {
        throw new NonRetryableError(error instanceof Error ? error.message : "invalid migration request");
      }
    });
    const jobType = params.jobType ?? "portfolio.migration.reconcile";
    if (jobType === "portfolio.migration.reconcile" && this.env.PORTFOLIO_MIGRATION_EXECUTION_ENABLED !== "true") {
      throw new NonRetryableError("Portfolio migration execution is disabled");
    }
    if (jobType === "portfolio.photography.backfill" && this.env.PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED !== "true") {
      throw new NonRetryableError("Photography migration execution is disabled");
    }
    if (jobType.startsWith("portfolio.finops.rollups.v2.") &&
        this.env.FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED !== "true") {
      throw new NonRetryableError("FinOps sharded-rollup backfill execution is disabled");
    }
    const envelopeJson = await step.do("create reconciliation envelope", async () =>
      JSON.stringify(await makeMutationEnvelope(portfolioMigrationEnvelopeInput(params, event.instanceId))),
    );
    const envelope = JSON.parse(envelopeJson) as Awaited<ReturnType<typeof makeMutationEnvelope>>;
    await step.do("enqueue reconciliation", { retries: { limit: 5, delay: "10 seconds", backoff: "exponential" } }, async () => {
      await this.env.PORTFOLIO_ASYNC_QUEUE.send(envelope, { contentType: "json" });
    });
    return { envelopeId: envelope.id, payloadSha256: envelope.payloadSha256 };
  }
}
