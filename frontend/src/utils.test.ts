import { describe, expect, it } from "vitest";
import { formatConfidence } from "./utils";

describe("formatConfidence", () => {
  it("renders percent confidence", () => {
    expect(formatConfidence(0.864)).toBe("86%");
  });
});
