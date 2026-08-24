import { describe, expect, it } from "vitest";
import { parsePublicObjectRoute } from "../src/r2";

const digest = "a".repeat(64);

describe("public R2 paths", () => {
  it("accepts immutable content-addressed media and docs keys", () => {
    expect(parsePublicObjectRoute(`/cdn/media/sha256/${digest}/portrait.avif`)).toEqual({
      bucket: "media",
      key: `sha256/${digest}/portrait.avif`,
    });
    expect(parsePublicObjectRoute(`/cdn/docs/sha256/${digest}/guide.pdf`)?.bucket).toBe("docs");
  });

  it("rejects mutable, traversal, and malformed object paths", () => {
    expect(parsePublicObjectRoute("/cdn/media/latest/portrait.avif")).toBeNull();
    expect(parsePublicObjectRoute(`/cdn/media/sha256/${digest}/../secret`)).toBeNull();
    expect(parsePublicObjectRoute(`/cdn/media/sha256/${"A".repeat(64)}/portrait.avif`)).toBeNull();
  });
});
