import { describe, expect, it } from "vitest";
import { canonicalJsonBytes, validateSignedMetadata } from "../src/signers/common";

function timestamp(version = 4) {
  return {
    _type: "timestamp",
    environment: "development",
    expires: "2026-08-21T05:00:00.000Z",
    meta: {
      "snapshot.json": {
        hashes: { sha256: "a".repeat(64) },
        length: 512,
        version,
      },
    },
    repository_id: "seen-dev-registry-v1",
    spec_version: "1.0",
    version,
  };
}

describe("role-isolated signer policy", () => {
  it("accepts canonical, bounded timestamp metadata", () => {
    const bytes = canonicalJsonBytes(timestamp());
    expect(validateSignedMetadata(
      bytes,
      "timestamp",
      "development",
      "seen-dev-registry-v1",
      new Date("2026-08-21T00:00:00.000Z"),
    ).version).toBe(4);
  });

  it("rejects non-canonical JSON before signing", () => {
    const nonCanonical = new TextEncoder().encode(JSON.stringify(timestamp(), null, 2));
    expect(() => validateSignedMetadata(
      nonCanonical,
      "timestamp",
      "development",
      "seen-dev-registry-v1",
      new Date("2026-08-21T00:00:00.000Z"),
    )).toThrow();
  });

  it("rejects wrong repository and rollback-shaped timestamp references", () => {
    const wrongReference = timestamp(4);
    wrongReference.meta["snapshot.json"].version = 3;
    expect(() => validateSignedMetadata(
      canonicalJsonBytes(wrongReference),
      "timestamp",
      "development",
      "seen-dev-registry-v1",
      new Date("2026-08-21T00:00:00.000Z"),
    )).toThrow();
    expect(() => validateSignedMetadata(
      canonicalJsonBytes(timestamp()),
      "timestamp",
      "development",
      "another-repository",
      new Date("2026-08-21T00:00:00.000Z"),
    )).toThrow();
  });

  it("uses UTF-8 sorted canonical object keys", () => {
    expect(new TextDecoder().decode(canonicalJsonBytes({ z: 1, a: 2 }))).toBe('{"a":2,"z":1}');
  });
});
