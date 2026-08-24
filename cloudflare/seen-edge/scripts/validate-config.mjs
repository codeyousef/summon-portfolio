import { buildConfigs } from "./config-lib.mjs";

const fake = (digit) => digit.repeat(64);
for (const environment of ["dev", "prod"]) {
  await buildConfigs({
    environment,
    namePrefix: "yousef",
    firestoreProject: environment === "dev" ? "portfolio-476219" : "seen-registry-prod-476219",
    firestoreDatabase: environment === "dev" ? "seen-registry-dev" : "seen-registry-prod",
    r2Endpoint: "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com",
    signerGuardService: `yousef-seen-signer-guard-${environment}`,
    publicKeys: { releases: fake("1"), security: fake("2"), snapshot: fake("3"), timestamp: fake("4") },
    keyIds: { releases: fake("5"), security: fake("6"), snapshot: fake("7"), timestamp: fake("8") },
  });
}
console.log("Seen Wrangler templates match the platform contract (no Cloudflare API calls).");
