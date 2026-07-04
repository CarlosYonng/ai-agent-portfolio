export type Health = {
  status: string;
  service: string;
  time?: string;
};

export type KnowledgeBase = {
  id: number;
  customerId: number;
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
  ingestStage?: string;
  progressPercent?: number;
  chunkTotal?: number;
  chunkDone?: number;
  errorMessage?: string;
  indexedAt?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
};

export type Citation = {
  chunk_id?: string;
  doc_id?: string;
  title?: string;
  score?: number;
  text?: string;
};

export type ChatResponse = {
  sessionId: number;
  traceId?: string;
  answer?: string;
  citations?: Citation[];
};

export type ChatSession = {
  id: number;
  customerId: number;
  userId: number;
  kbId?: number;
  title: string;
  createdAt?: string;
  updatedAt?: string;
};

export type ChatMessage = {
  id: number;
  customerId: number;
  sessionId: number;
  role: "user" | "assistant" | "system";
  content: string;
  traceId?: string;
  citations?: string;
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

export type TraceSummary = {
  trace_id: string;
  node_count: number;
  latest_node?: string;
  created_at?: string;
};
