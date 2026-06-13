import type {
  ChatMessage,
  ChatResponse,
  ChatSession,
  Health,
  IncidentHistory,
  IncidentResponse,
  KnowledgeBase,
  KnowledgeDocument,
  TraceSummary,
  TraceNode
} from "./types";

type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };

type ErrorCopy = {
  title: string;
  message: string;
};

export type ApiErrorPayload = {
  code?: string;
  message?: string;
  path?: string;
  traceId?: string;
  timestamp?: string;
};

const ERROR_COPY: Record<string, ErrorCopy> = {
  NETWORK_ERROR: {
    title: "系统连接异常",
    message: "当前无法连接到服务，请确认运行环境已启动后重试。"
  },
  DOWNSTREAM_ERROR: {
    title: "AI 能力暂时无法使用",
    message: "当前模型或工具服务没有返回有效结果，请稍后重试。"
  },
  INTERNAL_ERROR: {
    title: "操作未完成",
    message: "系统处理请求时遇到问题，请稍后重试。"
  },
  VALIDATION_ERROR: {
    title: "请检查输入内容",
    message: "部分输入不符合要求，请调整后再提交。"
  },
  MISSING_PARAMETER: {
    title: "缺少必要信息",
    message: "请补全必填内容后再提交。"
  },
  TYPE_MISMATCH: {
    title: "输入格式不正确",
    message: "请检查输入格式后再提交。"
  },
  BAD_REQUEST_BODY: {
    title: "输入格式不正确",
    message: "请检查输入内容后再提交。"
  },
  UNAUTHORIZED: {
    title: "需要重新登录",
    message: "当前登录状态已失效，请重新登录后继续。"
  },
  FORBIDDEN: {
    title: "没有操作权限",
    message: "当前账号没有权限执行这个操作。"
  },
  NOT_FOUND: {
    title: "没有找到数据",
    message: "目标数据可能已被删除，请刷新页面后再试。"
  },
  CONFLICT: {
    title: "数据状态已变化",
    message: "页面数据不是最新状态，请刷新后再试。"
  },
  TOO_MANY_REQUESTS: {
    title: "操作过于频繁",
    message: "请稍等片刻后再试。"
  }
};

export class ApiError extends Error {
  status: number;
  payload?: ApiErrorPayload;
  technicalMessage: string;
  userTitle: string;
  userMessage: string;

  constructor(status: number, statusText: string, payload?: ApiErrorPayload, fallbackText?: string) {
    const technicalMessage = payload?.message || fallbackText || `${status} ${statusText}`;
    const copy = toUserCopy(status, payload);
    super(copy.message);
    this.name = "ApiError";
    this.status = status;
    this.payload = payload;
    this.technicalMessage = technicalMessage;
    this.userTitle = copy.title;
    this.userMessage = copy.message;
  }
}

function toUserCopy(status: number, payload?: ApiErrorPayload): ErrorCopy {
  const code = payload?.code;
  if (code && ERROR_COPY[code]) {
    if (code === "VALIDATION_ERROR" && payload?.message) {
      return { ...ERROR_COPY[code], message: payload.message };
    }
    return ERROR_COPY[code];
  }
  if (status === 401) {
    return ERROR_COPY.UNAUTHORIZED;
  }
  if (status === 403) {
    return ERROR_COPY.FORBIDDEN;
  }
  if (status === 404) {
    return ERROR_COPY.NOT_FOUND;
  }
  if (status === 409) {
    return ERROR_COPY.CONFLICT;
  }
  if (status === 429) {
    return ERROR_COPY.TOO_MANY_REQUESTS;
  }
  if (status >= 500) {
    return ERROR_COPY.INTERNAL_ERROR;
  }
  return {
    title: "操作未完成",
    message: payload?.message || "当前操作没有成功，请稍后重试。"
  };
}

export function getErrorTitle(error: unknown, fallback = "操作未完成") {
  if (error instanceof ApiError) {
    return error.userTitle;
  }
  return fallback;
}

export function getErrorMessage(error: unknown, fallback = "操作没有成功，请稍后重试。") {
  if (error instanceof ApiError) {
    return error.userMessage;
  }
  return fallback;
}

export function getErrorContext(error: unknown) {
  if (!(error instanceof ApiError)) {
    return "";
  }
  if (error.payload?.traceId) {
    return `错误编号：${error.payload.traceId}`;
  }
  if (error.status === 0) {
    return "请确认本地运行环境已启动。";
  }
  return "如果多次重试仍失败，请联系维护人员处理。";
}

function notifyApiError(error: ApiError) {
  window.dispatchEvent(new CustomEvent("agent-api-error", { detail: error }));
}

function parseErrorResponse(response: Response, text: string) {
  let payload: ApiErrorPayload | undefined;
  let fallbackText = `${response.status} ${response.statusText}`;
  if (text) {
    try {
      payload = JSON.parse(text) as ApiErrorPayload;
    } catch {
      fallbackText = text;
    }
  }
  return new ApiError(response.status, response.statusText, payload, fallbackText);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...init?.headers
      }
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "无法连接服务";
    const apiError = new ApiError(0, "NETWORK_ERROR", { code: "NETWORK_ERROR", message, path });
    notifyApiError(apiError);
    throw apiError;
  }
  const text = await response.text();
  if (!response.ok) {
    const error = parseErrorResponse(response, text);
    notifyApiError(error);
    throw error;
  }
  return (text ? JSON.parse(text) : undefined) as T;
}

export function getHealth() {
  return request<Health>("/api/health");
}

export function listKnowledgeBases(tenantId: number) {
  return request<KnowledgeBase[]>(`/api/kb?tenantId=${tenantId}`);
}

export function createKnowledgeBase(payload: {
  tenantId: number;
  name: string;
  description: string;
  visibility: string;
}) {
  return request<{ id: number }>("/api/kb", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function updateKnowledgeBase(
  id: number,
  payload: {
    name?: string;
    description?: string;
    visibility?: string;
  }
) {
  return request<void>(`/api/kb/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function deleteKnowledgeBase(id: number) {
  return request<void>(`/api/kb/${id}`, {
    method: "DELETE"
  });
}

export function listDocuments(tenantId: number, kbId: number) {
  return request<KnowledgeDocument[]>(`/api/kb/${kbId}/documents?tenantId=${tenantId}`);
}

export function createDocument(payload: {
  tenantId: number;
  kbId: number;
  title: string;
  sourceType: string;
  sourceUri: string;
}) {
  return request<{ id: number }>("/api/kb/documents", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function updateDocument(
  id: number,
  payload: {
    title?: string;
    sourceType?: string;
    sourceUri?: string;
  }
) {
  return request<void>(`/api/kb/documents/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function deleteDocument(id: number) {
  return request<void>(`/api/kb/documents/${id}`, {
    method: "DELETE"
  });
}

export function updateDocumentStatus(id: number, status: string) {
  return request<void>(`/api/kb/documents/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status })
  });
}

export function askChat(payload: { tenantId: number; userId: number; sessionId?: number; kbId?: number; question: string }) {
  return request<ChatResponse>("/api/chat/messages", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function listChatSessions(tenantId: number, userId: number, kbId?: number) {
  const suffix = kbId ? `&kbId=${kbId}` : "";
  return request<ChatSession[]>(`/api/chat/sessions?tenantId=${tenantId}&userId=${userId}${suffix}`);
}

export function listChatMessages(tenantId: number, sessionId: number) {
  return request<ChatMessage[]>(`/api/chat/sessions/${sessionId}/messages?tenantId=${tenantId}`);
}

export function getTrace(traceId: string) {
  return request<TraceNode[]>(`/api/traces/${encodeURIComponent(traceId)}`);
}

export function listTraceSummaries(tenantId: number) {
  return request<TraceSummary[]>(`/api/traces?tenantId=${tenantId}`);
}

export function diagnoseIncident(payload: {
  tenantId: number;
  userId: number;
  service: string;
  question: string;
  traceId?: string;
  timeRange: string;
}) {
  return request<IncidentResponse>("/api/incidents/diagnose", {
    method: "POST",
    body: JSON.stringify(payload as Record<string, JsonValue>)
  });
}

export function listIncidentHistory(tenantId: number, userId: number) {
  return request<IncidentHistory[]>(`/api/incidents/history?tenantId=${tenantId}&userId=${userId}`);
}
