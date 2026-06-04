import type {
  ChatResponse,
  Health,
  IncidentResponse,
  KnowledgeBase,
  KnowledgeDocument,
  TraceNode
} from "./types";

type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers
    }
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
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

export function askChat(payload: { tenantId: number; userId: number; sessionId?: number; question: string }) {
  return request<ChatResponse>("/api/chat/messages", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function getTrace(traceId: string) {
  return request<TraceNode[]>(`/api/traces/${encodeURIComponent(traceId)}`);
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
