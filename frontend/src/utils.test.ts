import { describe, expect, it } from "vitest";
import { formatConfidence, compactJson, formatHealthStatus, formatStatusText } from "./utils";

describe("formatConfidence", () => {
  it("renders percent confidence", () => {
    expect(formatConfidence(0.864)).toBe("86%");
  });
  it("renders 0% for zero", () => {
    expect(formatConfidence(0)).toBe("0%");
  });
  it("renders 100% for one", () => {
    expect(formatConfidence(1)).toBe("100%");
  });
  it("returns dash for undefined", () => {
    expect(formatConfidence(undefined)).toBe("-");
  });
  it("returns dash for NaN", () => {
    expect(formatConfidence(NaN)).toBe("-");
  });
});

describe("compactJson", () => {
  it("returns dash for undefined", () => {
    expect(compactJson(undefined)).toBe("-");
  });
  it("returns dash for null", () => {
    expect(compactJson(null)).toBe("-");
  });
  it("returns dash for empty string", () => {
    expect(compactJson("")).toBe("-");
  });
  it("returns string as-is", () => {
    expect(compactJson("hello")).toBe("hello");
  });
  it("serializes objects", () => {
    expect(compactJson({ a: 1 })).toBe('{"a":1}');
  });
  it("serializes arrays", () => {
    expect(compactJson([1, 2])).toBe("[1,2]");
  });
});

describe("formatHealthStatus", () => {
  it("returns UP for up", () => {
    expect(formatHealthStatus("UP")).toBe("UP");
  });
  it("returns UP for lowercase up", () => {
    expect(formatHealthStatus("up")).toBe("UP");
  });
  it("returns CHECKING for undefined", () => {
    expect(formatHealthStatus(undefined)).toBe("CHECKING");
  });
  it("returns CHECKING for checking", () => {
    expect(formatHealthStatus("CHECKING")).toBe("CHECKING");
  });
  it("returns DOWN for down", () => {
    expect(formatHealthStatus("DOWN")).toBe("DOWN");
  });
  it("returns original for unknown status", () => {
    expect(formatHealthStatus("DEGRADED")).toBe("DEGRADED");
  });
});

describe("formatStatusText", () => {
  it("returns dash for undefined", () => {
    expect(formatStatusText(undefined)).toBe("-");
  });
  it("translates up", () => {
    expect(formatStatusText("up")).toBe("正常");
  });
  it("translates down", () => {
    expect(formatStatusText("down")).toBe("异常");
  });
  it("translates pending", () => {
    expect(formatStatusText("pending")).toBe("待处理");
  });
  it("translates success", () => {
    expect(formatStatusText("success")).toBe("成功");
  });
  it("translates indexed", () => {
    expect(formatStatusText("indexed")).toBe("已入库");
  });
  it("translates failed", () => {
    expect(formatStatusText("failed")).toBe("失败");
  });
  it("translates checking", () => {
    expect(formatStatusText("checking")).toBe("检查中");
  });
  it("returns original for unknown", () => {
    expect(formatStatusText("unknown")).toBe("unknown");
  });
});
