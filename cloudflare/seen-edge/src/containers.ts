import { Container } from "@cloudflare/containers";
import { ContractError } from "./contracts";
import { registryJobLaunch, type RegistryJob } from "./container-env";
import type { Env } from "./types";

interface StoredJob {
  id: string;
  startedAt: string;
  finishedAt?: string;
  exitCode?: number;
}

export interface RegistryJobStatus {
  id: string;
  state: "starting" | "running" | "complete" | "failed";
  exitCode: number | null;
  startedAt: string;
  finishedAt: string | null;
}

const JOB_KEY = "seen-registry-job:v1";

export class SeenRegistryJobContainer extends Container<Env> {
  static override outboundByHost = {
    "seen-signer-releases.internal": signerOutbound("releases"),
    "seen-signer-security.internal": signerOutbound("security"),
    "seen-signer-snapshot.internal": signerOutbound("snapshot"),
    "seen-signer-timestamp.internal": signerOutbound("timestamp"),
  };

  override sleepAfter = "1m";
  override enableInternet = true;
  override envVars = {};

  async startJob(job: RegistryJob): Promise<RegistryJobStatus> {
    const launch = registryJobLaunch(job, this.env);
    const current = await this.ctx.storage.get<StoredJob>(JOB_KEY);
    const state = await this.getState();
    if (current?.id === job.id) return status(current, state);
    if (current && (state.status === "running" || state.status === "healthy" || state.status === "stopping")) {
      throw new ContractError("container instance already owns a different active job");
    }
    const record: StoredJob = { id: job.id, startedAt: new Date().toISOString() };
    await this.ctx.storage.put(JOB_KEY, record);
    try {
      await this.start({
        entrypoint: launch.entrypoint,
        envVars: launch.envVars,
        enableInternet: true,
        labels: launch.labels,
      });
    } catch (error) {
      await this.ctx.storage.delete(JOB_KEY);
      throw error;
    }
    return { id: job.id, state: "starting", exitCode: null, startedAt: record.startedAt, finishedAt: null };
  }

  async jobStatus(jobId: string): Promise<RegistryJobStatus> {
    const record = await this.ctx.storage.get<StoredJob>(JOB_KEY);
    if (!record || record.id !== jobId) throw new ContractError("job is not owned by this container instance");
    return status(record, await this.getState());
  }

  override async onStop(params: { exitCode: number; reason: "exit" | "runtime_signal" }): Promise<void> {
    const record = await this.ctx.storage.get<StoredJob>(JOB_KEY);
    if (record) {
      await this.ctx.storage.put(JOB_KEY, {
        ...record,
        finishedAt: new Date().toISOString(),
        exitCode: params.exitCode,
      } satisfies StoredJob);
    }
    await super.onStop(params);
  }
}

function signerOutbound(role: "releases" | "security" | "snapshot" | "timestamp") {
  return async (request: Request, env: Env): Promise<Response> => {
    const binding = role === "releases" ? env.SEEN_RELEASES_SIGNER
      : role === "security" ? env.SEEN_SECURITY_SIGNER
      : role === "snapshot" ? env.SEEN_SNAPSHOT_SIGNER
      : env.SEEN_TIMESTAMP_SIGNER;
    const headers = new Headers(request.headers);
    headers.set("Authorization", `Bearer ${env.SEEN_SIGNER_CALL_TOKEN}`);
    headers.set("Origin", "https://seen-edge.internal");
    headers.set("X-Seen-Signer-Audience", `https://seen-signer-${role}.internal`);
    headers.set("X-Forwarded-Proto", "https");
    return binding.fetch(new Request(request, { headers }));
  };
}

function status(record: StoredJob, state: Awaited<ReturnType<SeenRegistryJobContainer["getState"]>>): RegistryJobStatus {
  if (record.finishedAt !== undefined) {
    return {
      id: record.id,
      state: record.exitCode === 0 ? "complete" : "failed",
      exitCode: record.exitCode ?? null,
      startedAt: record.startedAt,
      finishedAt: record.finishedAt,
    };
  }
  if (state.status === "stopped_with_code") {
    return {
      id: record.id,
      state: state.exitCode === 0 ? "complete" : "failed",
      exitCode: state.exitCode ?? null,
      startedAt: record.startedAt,
      finishedAt: null,
    };
  }
  if (state.status === "stopped") {
    return { id: record.id, state: "failed", exitCode: null, startedAt: record.startedAt, finishedAt: null };
  }
  return { id: record.id, state: state.status === "stopping" ? "running" : "running", exitCode: null, startedAt: record.startedAt, finishedAt: null };
}
