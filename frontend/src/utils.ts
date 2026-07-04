export function formatConfidence(value?: number) {
  if (value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return `${Math.round(value * 100)}%`;
}

export function compactJson(value: unknown) {
  if (value === undefined || value === null || value === "") {
    return "-";
  }
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value);
}

export function formatHealthStatus(status?: string) {
  const normalized = (status ?? "checking").toLowerCase();
  if (normalized === "up") return "UP";
  if (normalized === "checking") return "CHECKING";
  if (normalized === "down") return "DOWN";
  return status ?? "CHECKING";
}

export function formatStatusText(value?: string) {
  if (!value) return "-";
  const normalized = value.toLowerCase();
  if (normalized === "up") return "正常";
  if (normalized === "down") return "异常";
  if (normalized === "pending") return "待处理";
  if (normalized === "success") return "成功";
  if (normalized === "indexed") return "已入库";
  if (normalized === "failed" || normalized === "fail") return "失败";
  if (normalized === "checking") return "检查中";
  return value;
}
