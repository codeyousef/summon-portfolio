import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = resolve(packageRoot, "../..");
const contractPath = resolve(repositoryRoot, "infra/cloudflare/modules/platform/contract.json");
const mainTemplatePath = resolve(packageRoot, "wrangler.template.json");
const signerTemplatePath = resolve(packageRoot, "wrangler.signer.template.json");
const packagePath = resolve(packageRoot, "package.json");
const roles = ["releases", "security", "snapshot", "timestamp"];

export async function loadInputs() {
  const [contractText, mainTemplate, signerTemplate, packageText] = await Promise.all([
    readFile(contractPath, "utf8"),
    readFile(mainTemplatePath, "utf8"),
    readFile(signerTemplatePath, "utf8"),
    readFile(packagePath, "utf8"),
  ]);
  return {
    contract: JSON.parse(contractText),
    mainTemplate,
    signerTemplate,
    packageJson: JSON.parse(packageText),
  };
}

export async function buildConfigs(rawOptions) {
  const options = normalizeOptions(rawOptions);
  const { contract, mainTemplate, signerTemplate, packageJson } = await loadInputs();
  const worker = contract.workers?.seen_edge;
  if (!worker) throw new Error("platform contract is missing workers.seen_edge");
  const resourceName = (suffix) => `${options.namePrefix}-${suffix}-${options.environment}`;
  const edgeScriptName = resourceName(worker.name_suffix);
  const signerSpecs = new Map(worker.route_less_workers.map((spec) => [spec.role, spec]));
  const r2 = Object.fromEntries(worker.r2_bucket_keys.map((key) => {
    const spec = contract.r2_buckets[key];
    return [spec.binding, resourceName(spec.name_suffix)];
  }));
  const queues = Object.fromEntries(worker.queue_keys.map((key) => {
    const spec = contract.queues[key];
    return [spec.binding, resourceName(spec.name_suffix)];
  }));
  const signerScripts = Object.fromEntries(roles.map((role) => {
    const spec = signerSpecs.get(role);
    if (!spec) throw new Error(`platform contract is missing ${role} signer`);
    return [role, resourceName(spec.name_suffix)];
  }));
  const main = parseTemplate(mainTemplate, {
    SCRIPT_NAME: edgeScriptName,
    RUNTIME_ENVIRONMENT: options.runtimeEnvironment,
    REPOSITORY_ID: options.repositoryId,
    REGISTRY_ORIGIN: options.registryOrigin,
    FIRESTORE_PROJECT_ID: options.firestoreProject,
    FIRESTORE_DATABASE_ID: options.firestoreDatabase,
    SEEN_QUARANTINE_BUCKET: r2.SEEN_QUARANTINE,
    SEEN_PUBLIC_BUCKET: r2.SEEN_PUBLIC,
    SEEN_METADATA_BUCKET: r2.SEEN_METADATA,
    SEEN_PRIVATE_BUCKET: r2.SEEN_PRIVATE,
    SEEN_EVIDENCE_BUCKET: r2.SEEN_EVIDENCE,
    SEEN_BACKUP_BUCKET: r2.SEEN_BACKUP,
    SEEN_R2_ENDPOINT: options.r2Endpoint,
    RELEASES_PUBLIC_KEY_HEX: options.publicKeys.releases,
    SECURITY_PUBLIC_KEY_HEX: options.publicKeys.security,
    SNAPSHOT_PUBLIC_KEY_HEX: options.publicKeys.snapshot,
    TIMESTAMP_PUBLIC_KEY_HEX: options.publicKeys.timestamp,
    SEEN_OPERATIONS_QUEUE: queues.SEEN_OPERATIONS_QUEUE,
    SEEN_OPERATIONS_DLQ: queues.SEEN_OPERATIONS_DLQ,
    RELEASES_SIGNER_SCRIPT: signerScripts.releases,
    SECURITY_SIGNER_SCRIPT: signerScripts.security,
    SNAPSHOT_SIGNER_SCRIPT: signerScripts.snapshot,
    TIMESTAMP_SIGNER_SCRIPT: signerScripts.timestamp,
    SEEN_REGISTRY_JOBS_CONTAINER: resourceName("seen-registry-jobs"),
    SEEN_PROMOTION_WORKFLOW: resourceName(worker.workflows.find((value) => value.binding === "SEEN_PROMOTION_WORKFLOW").name_suffix),
    SEEN_MAINTENANCE_WORKFLOW: resourceName(worker.workflows.find((value) => value.binding === "SEEN_MAINTENANCE_WORKFLOW").name_suffix),
  });
  const signers = Object.fromEntries(roles.map((role) => {
    const spec = signerSpecs.get(role);
    const config = parseTemplate(signerTemplate, {
      SIGNER_SCRIPT_NAME: signerScripts[role],
      SIGNER_ROLE: role,
      RUNTIME_ENVIRONMENT: options.runtimeEnvironment,
      REPOSITORY_ID: options.repositoryId,
      SIGNER_KEY_ID: options.keyIds[role],
      SIGNING_KEY_BINDING: spec.secret_binding,
      SIGNER_GUARD_SERVICE: options.signerGuardService,
      EDGE_SCRIPT_NAME: edgeScriptName,
    });
    if (role === "timestamp") {
      config.vars.SEEN_OBJECT_PREFIX = "v1";
      config.r2_buckets = [{ binding: "SEEN_METADATA", bucket_name: r2.SEEN_METADATA }];
    }
    return [role, config];
  }));
  validateConfigs({ main, signers }, contract, packageJson, options);
  return { main, signers, options };
}

export function validateConfigs(configs, contract, packageJson, options) {
  const worker = contract.workers.seen_edge;
  assert(contract.authoritative_database === "firestore", "Firestore must remain authoritative");
  assert(packageJson.devDependencies.wrangler === contract.toolchain.wrangler, "Wrangler pin differs from platform contract");
  assert(packageJson.dependencies["@cloudflare/containers"] === "0.3.7", "Container SDK must stay pinned to 0.3.7");
  assert(configs.main.compatibility_date === contract.toolchain.compatibility_date, "compatibility date differs from contract");
  assert(configs.main.name === `${options.namePrefix}-${worker.name_suffix}-${options.environment}`, "Seen edge script name differs from contract");
  validatePrivateWorker(configs.main);

  const expectedR2 = new Map(worker.r2_bucket_keys.map((key) => {
    const spec = contract.r2_buckets[key];
    return [spec.binding, `${options.namePrefix}-${spec.name_suffix}-${options.environment}`];
  }));
  bindingMap(configs.main.r2_buckets, "binding", "bucket_name", expectedR2, "R2");
  const expectedQueues = new Map(worker.queue_keys.map((key) => {
    const spec = contract.queues[key];
    return [spec.binding, `${options.namePrefix}-${spec.name_suffix}-${options.environment}`];
  }));
  bindingMap(configs.main.queues.producers, "binding", "queue", expectedQueues, "Queue");
  assert(configs.main.queues.consumers.length === 1, "exactly one Seen queue consumer is required");
  assert(configs.main.queues.consumers[0].queue === expectedQueues.get("SEEN_OPERATIONS_QUEUE"), "Seen queue consumer differs from contract");
  assert(configs.main.queues.consumers[0].dead_letter_queue === expectedQueues.get("SEEN_OPERATIONS_DLQ"), "Seen queue DLQ differs from contract");

  const expectedDo = worker.durable_objects.map((spec) => `${spec.binding}:${spec.class_name}`).sort();
  const actualDo = configs.main.durable_objects.bindings
    .filter((value) => value.name !== "SEEN_REGISTRY_JOBS")
    .map((value) => `${value.name}:${value.class_name}`).sort();
  assert(JSON.stringify(actualDo) === JSON.stringify(expectedDo), "Seen coordinator Durable Objects differ from contract");
  const containerBinding = configs.main.durable_objects.bindings.find((value) => value.name === "SEEN_REGISTRY_JOBS");
  assert(containerBinding?.class_name === "SeenRegistryJobContainer", "Container class requires a matching Durable Object binding");
  assert(configs.main.migrations[0].new_sqlite_classes.includes("SeenRegistryJobContainer"), "Container class requires a SQLite migration");

  assert(configs.main.containers.length === worker.containers.length, "Seen container count differs from contract");
  for (const spec of worker.containers) {
    const actual = configs.main.containers.find((value) => value.class_name === spec.class_name);
    assert(actual?.instance_type === spec.instance_type, `${spec.class_name} instance type differs from contract`);
    assert(actual?.max_instances === spec.max_instances, `${spec.class_name} max_instances differs from contract`);
    assert(JSON.stringify(actual?.constraints?.regions) === JSON.stringify(worker.container_regions), `${spec.class_name} region policy differs from contract`);
  }
  assert(configs.main.workflows.length === worker.workflows.length, "Seen workflow count differs from contract");
  for (const spec of worker.workflows) {
    const actual = configs.main.workflows.find((value) => value.binding === spec.binding);
    assert(actual?.class_name === spec.class_name, `${spec.binding} differs from contract`);
  }

  const signerSpecs = new Map(worker.route_less_workers.map((value) => [value.role, value]));
  assert(Object.keys(configs.signers).sort().join(",") === roles.join(","), "exactly four role-isolated signers are required");
  for (const role of roles) {
    const config = configs.signers[role];
    const spec = signerSpecs.get(role);
    validatePrivateWorker(config);
    assert(config.name === `${options.namePrefix}-${spec.name_suffix}-${options.environment}`, `${role} signer name differs from contract`);
    assert(config.main === `src/signers/${role}.ts`, `${role} signer entrypoint is invalid`);
    assert(config.vars.SEEN_SIGNER_ROLE === role, `${role} signer role is invalid`);
    assert(config.secrets.required.length === 2 && config.secrets.required.includes(spec.secret_binding) &&
      config.secrets.required.includes("SEEN_SIGNER_CALL_TOKEN"), `${role} signer secret isolation is invalid`);
    assert(config.services.length === 1 && config.services[0].binding === "SEEN_SIGNER_GUARD", `${role} signer must fail closed through the state guard`);
    assert(config.durable_objects.bindings.length === 1 && config.durable_objects.bindings[0].script_name === configs.main.name,
      `${role} signer ordering binding must target Seen edge`);
    if (role === "timestamp") {
      assert(config.r2_buckets?.length === 1 && config.r2_buckets[0].binding === "SEEN_METADATA", "timestamp signer requires only metadata CAS authority");
    } else {
      assert(config.r2_buckets === undefined, `${role} signer must not receive direct object storage authority`);
    }
  }
  assertNoSecretValues(configs);
}

function normalizeOptions(options) {
  const environment = options.environment;
  if (environment !== "dev" && environment !== "prod") throw new Error("environment must be dev or prod");
  const runtimeEnvironment = environment === "dev" ? "development" : "production";
  const namePrefix = options.namePrefix ?? "yousef";
  assert(/^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/.test(namePrefix), "namePrefix is invalid");
  assert(/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(options.firestoreProject), "firestoreProject is invalid");
  assert(/^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$/.test(options.firestoreDatabase), "firestoreDatabase is invalid");
  const expectedDatabase = environment === "dev" ? "seen-registry-dev" : "seen-registry-prod";
  assert(options.firestoreDatabase === expectedDatabase, `Firestore database must be ${expectedDatabase}`);
  assert(/^https:\/\/[a-z0-9.-]+\.r2\.cloudflarestorage\.com$/.test(options.r2Endpoint), "r2Endpoint is invalid");
  assert(/^[a-z0-9][a-z0-9-]{2,62}$/.test(options.signerGuardService), "signerGuardService is invalid");
  for (const role of roles) {
    assert(/^[0-9a-f]{64}$/.test(options.publicKeys?.[role] ?? ""), `${role} public key is invalid`);
    assert(/^[0-9a-f]{64}$/.test(options.keyIds?.[role] ?? ""), `${role} key ID is invalid`);
  }
  return {
    environment,
    runtimeEnvironment,
    namePrefix,
    firestoreProject: options.firestoreProject,
    firestoreDatabase: options.firestoreDatabase,
    r2Endpoint: options.r2Endpoint,
    signerGuardService: options.signerGuardService,
    publicKeys: options.publicKeys,
    keyIds: options.keyIds,
    repositoryId: environment === "dev" ? "seen-dev-registry-v1" : "seen-prod-registry-v1",
    registryOrigin: environment === "dev" ? "https://seen.dev.yousef.codes/packages" : "https://seen.yousef.codes/packages",
  };
}

function parseTemplate(template, replacements) {
  const rendered = template.replace(/__([A-Z0-9_]+)__/g, (placeholder, token) => {
    if (!(token in replacements)) throw new Error(`unknown template placeholder ${placeholder}`);
    return replacements[token];
  });
  const unresolved = rendered.match(/__[A-Z0-9_]+__/g);
  if (unresolved) throw new Error(`unresolved template placeholders: ${unresolved.join(", ")}`);
  return JSON.parse(rendered);
}

function validatePrivateWorker(config) {
  assert(config.workers_dev === false && config.preview_urls === false, `${config.name} public preview deployment must stay disabled`);
  assert(config.routes === undefined && config.route === undefined, `${config.name} routes are a separately approved phase`);
  assert(config.account_id === undefined && config.zone_id === undefined, `${config.name} cannot track account or zone identifiers`);
}

function bindingMap(values, bindingKey, resourceKey, expected, label) {
  assert(Array.isArray(values) && values.length === expected.size, `${label} binding count differs from contract`);
  for (const value of values) assert(expected.get(value[bindingKey]) === value[resourceKey], `${label} binding ${value[bindingKey]} differs from contract`);
}

function assertNoSecretValues(configs) {
  const serialized = JSON.stringify({ main: configs.main.vars, signers: Object.values(configs.signers).map((value) => value.vars) }).toLowerCase();
  for (const fragment of ["private_key", "secret_access", "bearer ", "api_token", "credential_json"]) {
    assert(!serialized.includes(fragment), `tracked non-secret variables contain forbidden fragment ${fragment}`);
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}
