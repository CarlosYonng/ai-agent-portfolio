import type {
  ChatMessage,
  ChatResponse,
  ChatSession,
  Health,
  KnowledgeBase,
  KnowledgeDocument,
  TraceSummary,
  TraceNode
} from "./types";

type ApiRequestInit = RequestInit & {
  timeoutMs?: number;
};

const DEFAULT_API_TIMEOUT_MS = 15000;
const LONG_RUNNING_API_TIMEOUT_MS = 60000;

type ErrorCopy = {
  title: string;
  message: string;
};

export type ApiErrorPayload = {
  code?: string;
  message?: string;
  traceId?: string;
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
  DOWNSTREAM_AI_ERROR: {
    title: "AI 能力暂时无法使用",
    message: "AI 服务调用失败，请稍后重试。"
  },
  INTERNAL_ERROR: {
    title: "操作未完成",
    message: "系统内部异常，请联系维护人员处理。"
  },
  SYSTEM_SCHEMA_MISMATCH: {
    title: "客户创建未完成",
    message: "系统配置暂时不可用，请联系维护人员处理。"
  },
  SYSTEM_REQUEST_SERIALIZATION_FAILED: {
    title: "服务内部请求异常",
    message: "服务内部请求组装失败，请联系维护人员处理。"
  },
  VALIDATION_ERROR: {
    title: "请检查输入内容",
    message: "部分输入不符合要求，请调整后再提交。"
  },
  REQUEST_VALIDATION_FAILED: {
    title: "请检查输入内容",
    message: "请求参数不合法，请调整后再提交。"
  },
  AUTH_INVALID_CREDENTIALS: {
    title: "登录失败",
    message: "用户名或密码错误。"
  },
  AUTH_ACCOUNT_DISABLED: {
    title: "账号不可用",
    message: "账户已被禁用。"
  },
  AUTH_INVITE_CODE_INVALID: {
    title: "邀请码不可用",
    message: "邀请码无效或客户已停用。"
  },
  AUTH_REQUIRED: {
    title: "需要重新登录",
    message: "登录状态已失效，请重新登录。"
  },
  PERMISSION_DENIED: {
    title: "没有操作权限",
    message: "当前账号没有权限执行这个操作。"
  },
  USER_USERNAME_EXISTS: {
    title: "用户名不可用",
    message: "用户名已存在。"
  },
  USER_NOT_FOUND: {
    title: "用户不存在",
    message: "用户不存在，可能已被删除。"
  },
  USER_PASSWORD_REQUIRED: {
    title: "缺少密码",
    message: "密码不能为空。"
  },
  USER_ROLE_INVALID: {
    title: "角色不合法",
    message: "请选择有效角色。"
  },
  CUSTOMER_NOT_FOUND: {
    title: "客户不存在",
    message: "客户不存在，可能已被删除。"
  },
  CUSTOMER_NAME_EXISTS: {
    title: "客户名称不可用",
    message: "客户名称已存在。"
  },
  CUSTOMER_REQUIRED: {
    title: "请选择客户",
    message: "客户用户必须选择所属客户。"
  },
  CUSTOMER_INACTIVE: {
    title: "客户不可用",
    message: "所属客户不存在或已停用。"
  },
  CUSTOMER_INVITE_CODE_GENERATE_FAILED: {
    title: "邀请码生成失败",
    message: "客户邀请码生成失败，请稍后重试。"
  },
  KB_NOT_FOUND: {
    title: "知识库不存在",
    message: "知识库不存在，可能已被删除。"
  },
  KB_VISIBILITY_INVALID: {
    title: "知识库可见性不合法",
    message: "visibility 仅支持 PRIVATE/TEAM/PUBLIC。"
  },
  DOCUMENT_NOT_FOUND: {
    title: "文档不存在",
    message: "文档不存在，可能已被删除。"
  },
  DOCUMENT_STATUS_INVALID: {
    title: "文档状态不合法",
    message: "status 仅支持 PENDING/INDEXED/FAILED。"
  },
  DOCUMENT_UPLOAD_INVALID: {
    title: "文档格式不支持",
    message: "请上传 Markdown 或 TXT 文档。"
  },
  DOCUMENT_INGEST_FAILED: {
    title: "自动入库失败",
    message: "文档已保存，但索引流程没有完成，请查看状态后重试。"
  },
  DOCUMENT_SOURCE_FILE_MISSING: {
    title: "源文件不可下载",
    message: "文档源文件不存在，请重新上传后再下载。"
  },
  CHAT_SESSION_NOT_FOUND: {
    title: "会话不可访问",
    message: "会话不存在或无权访问。"
  },
  TRACE_NOT_FOUND: {
    title: "Trace 不可访问",
    message: "Trace 不存在或无权访问。"
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

const SERVER_MESSAGE_CODES = new Set([
  "VALIDATION_ERROR",
  "REQUEST_VALIDATION_FAILED",
  "MISSING_PARAMETER",
  "TYPE_MISMATCH",
  "BAD_REQUEST_BODY",
  "AUTH_INVALID_CREDENTIALS",
  "AUTH_ACCOUNT_DISABLED",
  "AUTH_INVITE_CODE_INVALID",
  "AUTH_REQUIRED",
  "PERMISSION_DENIED",
  "USER_USERNAME_EXISTS",
  "USER_NOT_FOUND",
  "USER_PASSWORD_REQUIRED",
  "USER_ROLE_INVALID",
  "CUSTOMER_NOT_FOUND",
  "CUSTOMER_NAME_EXISTS",
  "CUSTOMER_REQUIRED",
  "CUSTOMER_INACTIVE",
  "KB_NOT_FOUND",
  "KB_VISIBILITY_INVALID",
  "DOCUMENT_NOT_FOUND",
  "DOCUMENT_STATUS_INVALID",
  "DOCUMENT_UPLOAD_INVALID",
  "DOCUMENT_INGEST_FAILED",
  "DOCUMENT_SOURCE_FILE_MISSING",
  "CHAT_SESSION_NOT_FOUND",
  "TRACE_NOT_FOUND"
]);

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
    if (SERVER_MESSAGE_CODES.has(code) && payload?.message) {
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
      const result = JSON.parse(text) as Record<string, unknown>;
      if (result.code && typeof result.code === "string" && result.code !== "ok") {
        payload = {
          code: result.code,
          message: typeof result.message === "string" ? result.message : undefined,
          traceId: typeof result.traceId === "string" ? result.traceId : undefined,
        };
      }
    } catch {
      fallbackText = text;
    }
  }
  return new ApiError(response.status, response.statusText, payload, fallbackText);
}

export async function apiRequest<T>(path: string, init?: ApiRequestInit): Promise<T> {
  let response: Response;
  const { timeoutMs, ...requestInit } = (init ?? {}) as ApiRequestInit;
  const effectiveTimeoutMs = timeoutMs ?? DEFAULT_API_TIMEOUT_MS;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), effectiveTimeoutMs);
  try {
    const token = localStorage.getItem("token");
    const isFormData = requestInit.body instanceof FormData;
    const headers: Record<string, string> = {
      ...requestInit.headers as Record<string, string>
    };
    if (!isFormData) {
      headers["Content-Type"] = "application/json";
    }
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    response = await fetch(path, {
      ...requestInit,
      headers,
      signal: requestInit.signal ?? controller.signal
    });
  } catch (error) {
    const message = error instanceof DOMException && error.name === "AbortError"
      ? "请求等待超时，请确认后端服务是否为最新版本后重试。"
      : error instanceof Error ? error.message : "无法连接服务";
    const apiError = new ApiError(0, "NETWORK_ERROR", { code: "NETWORK_ERROR", message });
    notifyApiError(apiError);
    throw apiError;
  } finally {
    window.clearTimeout(timeout);
  }
  const text = await response.text();
  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem("token");
      window.location.hash = "#/login";
    }
    const error = parseErrorResponse(response, text);
    notifyApiError(error);
    throw error;
  }
  // 统一响应包装：ApiResult<T>，提取 data 字段返回给调用方
  if (text) {
    try {
      const result = JSON.parse(text) as { code: string; message: string; data: T; traceId?: string };
      // 非 2xx 已在前面拦截，到这里 code 一定为 "ok"
      if (result.code !== "ok" && result.code !== undefined) {
        const apiError = new ApiError(response.status, response.statusText,
          { code: result.code, message: result.message, traceId: result.traceId });
        notifyApiError(apiError);
        throw apiError;
      }
      return result.data as T;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      // JSON 解析失败（非标准响应），退化为原始行为
      throw new ApiError(response.status, response.statusText);
    }
  }
  return undefined as T;
}

export function getHealth() {
  return apiRequest<Health>("/api/health");
}

export function listKnowledgeBases() {
  return apiRequest<KnowledgeBase[]>("/api/kb");
}

export function createKnowledgeBase(payload: {
  name: string;
  description: string;
  visibility: string;
}) {
  return apiRequest<{ id: number }>("/api/kb", {
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
  return apiRequest<void>(`/api/kb/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function deleteKnowledgeBase(id: number) {
  return apiRequest<void>(`/api/kb/${id}`, {
    method: "DELETE"
  });
}

export function listDocuments(kbId: number) {
  return apiRequest<KnowledgeDocument[]>(`/api/kb/${kbId}/documents`);
}

export function createDocument(payload: {
  kbId: number;
  title: string;
  sourceType: string;
  sourceUri: string;
}) {
  return apiRequest<void>("/api/kb/documents", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function uploadDocument(kbId: number, payload: { title?: string; file: File }) {
  const form = new FormData();
  form.append("file", payload.file);
  if (payload.title) {
    form.append("title", payload.title);
  }
  return apiRequest<KnowledgeDocument>(`/api/kb/${kbId}/documents/upload`, {
    method: "POST",
    body: form,
    timeoutMs: 180000
  });
}

export function retryDocumentIngest(id: number) {
  return apiRequest<KnowledgeDocument>(`/api/kb/documents/${id}/retry`, {
    method: "POST",
    timeoutMs: 30000
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
  return apiRequest<void>(`/api/kb/documents/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function deleteDocument(id: number) {
  return apiRequest<void>(`/api/kb/documents/${id}`, {
    method: "DELETE"
  });
}

export async function downloadDocumentFile(id: number) {
  const token = localStorage.getItem("token");
  const headers: Record<string, string> = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(`/api/kb/documents/${id}/download`, { headers });
  if (!response.ok) {
    const text = await response.text();
    const error = parseErrorResponse(response, text);
    notifyApiError(error);
    throw error;
  }
  const blob = await response.blob();
  return {
    blob,
    filename: filenameFromDisposition(response.headers.get("Content-Disposition")) ?? `document-${id}`,
  };
}

function filenameFromDisposition(disposition: string | null) {
  if (!disposition) {
    return null;
  }
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }
  const plainMatch = disposition.match(/filename="?([^";]+)"?/i);
  return plainMatch?.[1] ?? null;
}

export function askChat(payload: { sessionId?: number; kbId?: number; question: string }) {
  return apiRequest<ChatResponse>("/api/chat/messages", {
    method: "POST",
    body: JSON.stringify(payload),
    timeoutMs: LONG_RUNNING_API_TIMEOUT_MS
  });
}

export function listChatSessions(params?: {
  kbId?: number;
  keyword?: string;
  startDate?: string;
  endDate?: string;
  filterCustomerId?: number;
}) {
  const query = new URLSearchParams();
  if (params?.kbId) query.set("kbId", String(params.kbId));
  if (params?.keyword) query.set("keyword", params.keyword);
  if (params?.startDate) query.set("startDate", params.startDate);
  if (params?.endDate) query.set("endDate", params.endDate);
  if (params?.filterCustomerId) query.set("filterCustomerId", String(params.filterCustomerId));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiRequest<ChatSession[]>(`/api/chat/sessions${suffix}`);
}

export function listChatMessages(sessionId: number) {
  return apiRequest<ChatMessage[]>(`/api/chat/sessions/${sessionId}/messages`);
}

export function getTrace(traceId: string) {
  return apiRequest<TraceNode[]>(`/api/traces/${encodeURIComponent(traceId)}`, {
    timeoutMs: 10000
  });
}

export function listTraceSummaries() {
  return apiRequest<TraceSummary[]>("/api/traces");
	}
