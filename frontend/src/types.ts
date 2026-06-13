export type Health = {
  status: string;
  service: string;
  time?: string;
};

export type KnowledgeBase = {
  id: number;
  tenantId: number;
  name: string;
  description?: string;
  visibility: string;
  createdAt?: string;
};

export type KnowledgeDocument = {
  id: number;
  title: string;
  sourceType?: string;
  sourceUri?: string;
  status?: string;
  version?: number;
  createdAt?: string;
};

export type Citation = {
  chunk_id?: string;
  doc_id?: string;
  title?: string;
  score?: number;
  preview?: string;
};

export type ChatResponse = {
  sessionId: number;
  traceId?: string;
  answer?: string;
  citations?: Citation[];
};

export type ChatSession = {
  id: number;
  tenantId: number;
  userId: number;
  kbId?: number;
  title: string;
  createdAt?: string;
  updatedAt?: string;
};

export type ChatMessage = {
  id: number;
  tenantId: number;
  sessionId: number;
  role: "user" | "assistant" | "system";
  content: string;
  traceId?: string;
  createdAt?: string;
};

export type TraceNode = {
  node_name?: string;
  input_summary?: string;
  output_summary?: string;
  duration_ms?: number;
  metadata?: string;
  created_at?: string;
};

export type IncidentResponse = {
  trace_id?: string;
  summary?: string;
  root_causes?: Array<{ cause?: string; confidence?: number; evidence?: string }>;
  actions?: string[];
  evidences?: Array<Record<string, unknown>>;
};

export type IncidentHistory = {
  id: number;
  tenantId: number;
  userId: number;
  serviceName: string;
  businessTraceId?: string;
  agentTraceId?: string;
  question: string;
  summary?: string;
  response_json?: string;
  createdAt?: string;
};

export type TraceSummary = {
  trace_id: string;
  node_count: number;
  latest_node?: string;
  created_at?: string;
};
