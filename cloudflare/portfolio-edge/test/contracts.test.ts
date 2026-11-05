import { describe, expect, it } from "vitest";
import {
  canonicalJson,
  ContractError,
  makeMutationEnvelope,
  readMutationReceipt,
  readPhotographyStagingReceipt,
  validateDeadLetterReceipt,
  validateMutationEnvelope,
  validateMutationReceipt,
  validatePhotographyStagingReceipt,
} from "../src/contracts";

describe("mutation envelope", () => {
  it("preserves a bounded dead-letter receipt for lossless parking", () => {
    const receipt = {
      envelopeId: "gcp:abc",
      aggregateType: "finops.samurai.ingest",
      payloadSha256: "a".repeat(64),
      sourceMessageId: "b".repeat(32),
      attempts: 5,
      reason: "idempotency key reused with a different payload",
      failedAt: "2026-08-22T05:00:00.000Z",
    };
    expect(validateDeadLetterReceipt(receipt)).toEqual(receipt);
    expect(() => validateDeadLetterReceipt({ ...receipt, reason: "x".repeat(513) })).toThrow(ContractError);
  });

  it("hashes canonical payloads independent of key insertion order", async () => {
    const first = await makeMutationEnvelope({
      id: "mutation:1",
      aggregateType: "portfolio.docs.refresh",
      aggregateId: "docs:summon",
      expectedRevision: 4,
      authorityEpoch: 2,
      occurredAt: "2026-08-20T12:00:00.000Z",
      payload: { z: 1, nested: { b: true, a: "value" } },
    });
    const second = await makeMutationEnvelope({
      ...first,
      payload: { nested: { a: "value", b: true }, z: 1 },
    });
    expect(first.payloadSha256).toBe(second.payloadSha256);
    await expect(validateMutationEnvelope(first)).resolves.toEqual(first);
  });

  it("rejects a payload whose digest was substituted", async () => {
    const envelope = await makeMutationEnvelope({
      id: "mutation:2",
      aggregateType: "portfolio.media.process",
      aggregateId: "media:2",
      expectedRevision: 0,
      authorityEpoch: 1,
      occurredAt: "2026-08-20T12:00:00.000Z",
      payload: { mediaId: "2" },
    });
    await expect(validateMutationEnvelope({ ...envelope, payload: { mediaId: "other" } })).rejects.toBeInstanceOf(ContractError);
  });

  it("uses stable canonical JSON and rejects unsupported values", () => {
    expect(canonicalJson({ b: [2, 1], a: null })).toBe('{"a":null,"b":[2,1]}');
    expect(() => canonicalJson({ invalid: Number.NaN })).toThrow(ContractError);
  });

  it("accepts only a durable receipt bound to the exact envelope", async () => {
    const envelope = await makeMutationEnvelope({
      id: "mutation:receipt",
      aggregateType: "portfolio.docs.refresh",
      aggregateId: "docs:summon",
      expectedRevision: 4,
      authorityEpoch: 2,
      occurredAt: "2026-08-20T12:00:00.000Z",
      payload: { slug: "index" },
    });
    const receipt = {
      id: envelope.id,
      committedEpoch: 2,
      newRevision: 5,
      firestoreCommitTime: "2026-08-20T12:00:01.000Z",
      mirrorState: "mirrored" as const,
    };
    expect(validateMutationReceipt(receipt, envelope)).toEqual(receipt);
    expect(await readMutationReceipt(Response.json(receipt), envelope)).toEqual(receipt);
    expect(await readMutationReceipt(new Response("ok"), envelope)).toBeNull();
    expect(await readMutationReceipt(new Response("x".repeat(16 * 1024 + 1), {
      headers: { "content-length": "1" },
    }), envelope)).toBeNull();
    expect(() => validateMutationReceipt({ ...receipt, id: "mutation:other" }, envelope)).toThrow(ContractError);
  });

  it("validates photography staging without inventing a Firestore commit", async () => {
    const envelope = await makeMutationEnvelope({
      id: "photography:stage:1",
      aggregateType: "portfolio.photography.backfill",
      aggregateId: "portfolio-dev",
      expectedRevision: 0,
      authorityEpoch: 1,
      occurredAt: "2026-08-23T00:00:00Z",
      payload: {
        assets: [{
          photoId: "photo-1",
          sourceStorageKey: "photography/portfolio-dev/photo-1.jpg",
          contentType: "image/jpeg",
          expectedSha256: "a".repeat(64),
          expectedSizeBytes: 10,
        }],
      },
    });
    const receipt = {
      id: envelope.id,
      committedEpoch: 1,
      newRevision: 2,
      stagedAt: "2026-08-23T00:01:00Z",
      mirrorState: "staged" as const,
      assets: [{
        photoId: "photo-1",
        sourceStorageKey: "photography/portfolio-dev/photo-1.jpg",
        contentAddressedStorageKey: `sha256/${"a".repeat(64)}/photo-1.jpg`,
        sha256: "a".repeat(64),
        sizeBytes: 10,
        contentType: "image/jpeg",
      }],
    };
    const validated = {
      id: envelope.id,
      committedEpoch: 1,
      newRevision: 2,
      stagedAt: "2026-08-23T00:01:00Z",
      mirrorState: "staged",
      assets: receipt.assets,
    };

    expect(validatePhotographyStagingReceipt(receipt, envelope)).toEqual(validated);
    expect(await readPhotographyStagingReceipt(Response.json(receipt), envelope)).toEqual(validated);
    expect(
      await readPhotographyStagingReceipt(Response.json({ ...receipt, mirrorState: "mirrored" }), envelope),
    ).toBeNull();
  });
});
