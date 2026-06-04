import { describe, expect, it } from "vitest";
import { parseSseBlock } from "./stream";
import { formatConfidence } from "./utils";

describe("parseSseBlock", () => {
  it("parses event name and json payload", () => {
    const parsed = parseSseBlock('event: delta\ndata: {"type":"delta","content":"hello"}');

    expect(parsed).toEqual({
      event: "delta",
      data: '{"type":"delta","content":"hello"}'
    });
  });
});

describe("formatConfidence", () => {
  it("renders percent confidence", () => {
    expect(formatConfidence(0.864)).toBe("86%");
  });
});
