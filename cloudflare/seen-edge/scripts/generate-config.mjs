import { open } from "node:fs/promises";
import { resolve } from "node:path";
import { buildConfigs } from "./config-lib.mjs";

const values = Object.fromEntries(process.argv.slice(2).map((argument) => {
  const match = /^--([a-z0-9-]+)=(.+)$/.exec(argument);
  if (!match) throw new Error(`invalid argument: ${argument}`);
  return [match[1], match[2]];
}));
const required = (name) => values[name] ?? (() => { throw new Error(`--${name}=... is required`); })();
const environment = required("environment");
const roles = ["releases", "security", "snapshot", "timestamp"];
const result = await buildConfigs({
  environment,
  namePrefix: values["name-prefix"] ?? "yousef",
  firestoreProject: required("firestore-project"),
  firestoreDatabase: required("firestore-database"),
  r2Endpoint: required("r2-endpoint"),
  signerGuardService: required("signer-guard-service"),
  publicKeys: Object.fromEntries(roles.map((role) => [role, required(`${role}-public-key-hex`)])),
  keyIds: Object.fromEntries(roles.map((role) => [role, required(`${role}-key-id`)])),
});
const outputs = [
  [`wrangler.generated.${environment}.json`, result.main],
  ...roles.map((role) => [`wrangler.generated.signer-${role}.${environment}.json`, result.signers[role]]),
];
for (const [filename, config] of outputs) {
  const handle = await open(resolve(filename), "wx", 0o600);
  try {
    await handle.writeFile(`${JSON.stringify(config, null, 2)}\n`);
  } finally {
    await handle.close();
  }
  console.log(`created ${filename}`);
}
console.log("Configs generated locally; no Cloudflare API calls were made and deployment remains disabled.");
