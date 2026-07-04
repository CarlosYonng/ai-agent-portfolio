import { FormEvent, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { getErrorMessage, getTrace, listTraceSummaries } from "./api";
import type { TraceNode, TraceSummary } from "./types";
import { compactJson } from "./utils";

export default function TracePanel({ initialTraceId }: { initialTraceId: string }) {
  const [searchParams] = useSearchParams();
  const routeTraceId = searchParams.get("traceId") ?? "";
  const [traceTab, setTraceTab] = useState<"query" | "history">("query");
  const [traceId, setTraceId] = useState(initialTraceId);
  const [summaries, setSummaries] = useState<TraceSummary[]>([]);
  const [traceHistoryError, setTraceHistoryError] = useState("");
  const [nodes, setNodes] = useState<TraceNode[]>([]);
  const [message, setMessage] = useState("");
  const [traceLoading, setTraceLoading] = useState(false);

  async function refreshTraceHistory() {
    setTraceHistoryError("");
    try {
      setSummaries(await listTraceSummaries());
    } catch (error) {
      setTraceHistoryError(getErrorMessage(error, "Trace 历史暂时加载失败，请稍后重试。"));
      setSummaries([]);
    }
  }

  useEffect(() => {
    const nextTraceId = routeTraceId || initialTraceId;
    setTraceId(nextTraceId);
    if (nextTraceId) {
      loadTrace(nextTraceId).catch((error) => setMessage(getErrorMessage(error, "Trace 暂时查询失败，请稍后重试。")));
    }
  }, [initialTraceId, routeTraceId]);

  useEffect(() => {
    refreshTraceHistory();
  }, []);

  async function loadTrace(nextTraceId: string) {
    const cleanedTraceId = nextTraceId.trim();
    setMessage("");
    if (!cleanedTraceId) {
      setTraceId("");
      setNodes([]);
      setMessage("请输入 Agent Trace ID。");
      return;
    }
    setTraceLoading(true);
    setMessage("Trace 查询中...");
    try {
      const result = await getTrace(cleanedTraceId);
      setTraceId(cleanedTraceId);
      setNodes(result);
      setMessage(result.length ? "" : "没有查到 Agent Trace 节点，先执行一次 RAG 问答。");
      await refreshTraceHistory();
    } finally {
      setTraceLoading(false);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      await loadTrace(traceId);
    } catch (error) {
      setMessage(getErrorMessage(error, "Trace 暂时查询失败，请稍后重试。"));
    }
  }

  return (
    <section className="content-grid workspace-grid">
      <div className="panel span-12">
        <div className="tabs">
          <button className={traceTab === "query" ? "tab active" : "tab"} type="button" onClick={() => setTraceTab("query")}>
            查询 Trace
          </button>
          <button className={traceTab === "history" ? "tab active" : "tab"} type="button" onClick={() => { setTraceTab("history"); refreshTraceHistory(); }}>
            历史记录
          </button>
        </div>

        {traceTab === "query" ? (
          <div className="tab-body trace-query-layout">
            <form className="debug-panel trace-query-form" onSubmit={submit}>
              <div className="panel-title">
                <h2>Trace 查询</h2>
                <span>{message || "输入 RAG 问答返回的 Agent Trace ID"}</span>
              </div>
              <label>
                <span>Agent Trace ID <span className="text-red-500 font-bold">*</span></span>
                <input value={traceId} onChange={(event) => setTraceId(event.target.value)} placeholder="例如：tr_xxx" required />
              </label>
              <div className="trace-query-note">
                <strong>{traceId || "暂无 Trace ID"}</strong>
                <span>Trace 来自 RAG 问答响应或历史会话，用于还原 Agent 节点执行顺序。</span>
              </div>
              <button className="primary" type="submit" disabled={traceLoading}>{traceLoading ? "查询中..." : "查询 Trace"}</button>
            </form>
            <div className="trace-result-panel">
              <TraceTimeline nodes={nodes} traceId={traceId} />
            </div>
          </div>
        ) : (
          <div className="tab-body content-grid">
            <div className="span-4">
              <div className="panel-title">
                <h2>Trace 历史</h2>
                <span>{summaries.length} 条</span>
              </div>
              <div className="session-list">
                {summaries.length === 0 ? (
                  <p className="empty-state">{traceHistoryError || "暂无 Agent Trace 历史。执行一次 RAG 问答后会写入数据库。"}</p>
                ) : (
                  summaries.map((item) => (
                    <button
                      className={item.trace_id === traceId ? "session-item active" : "session-item"}
                      key={item.trace_id}
                      type="button"
                      disabled={traceLoading}
                      onClick={() => loadTrace(item.trace_id).catch((error) => setMessage(getErrorMessage(error, "Trace 暂时查询失败，请稍后重试。")))}
                    >
                      <strong>{item.trace_id}</strong>
                      <span>{item.node_count} 个节点 · {item.latest_node ?? "unknown"}</span>
                    </button>
                  ))
                )}
              </div>
            </div>
            <div className="span-8 trace-result-panel">
              <TraceTimeline nodes={nodes} traceId={traceId} />
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function parseTopCitations(metadata?: string): any[] | null {
  if (!metadata) return null;
  try {
    const parsed = JSON.parse(metadata);
    if (Array.isArray(parsed.top_citations) && parsed.top_citations.length > 0) {
      return parsed.top_citations;
    }
  } catch { /* ignore */ }
  return null;
}

export function TraceTimeline({ nodes, traceId }: { nodes: TraceNode[]; traceId?: string }) {
  return (
    <div className="trace-timeline-shell">
      <div className="panel-title">
        <div>
          <h2>Agent 节点时间线</h2>
          <small>{traceId ? `当前 Trace：${traceId}` : "输入 Trace ID 后展示节点详情"}</small>
        </div>
        <span>{nodes.length} 个节点</span>
      </div>
      <div className="timeline">
        {nodes.length === 0 ? (
          <p className="empty-state">暂无 Trace 节点。先执行一次 RAG 问答，或输入已有 Trace ID 查询。</p>
        ) : (
          nodes.map((node, index) => {
            const citations = parseTopCitations(node.metadata);
            return (
              <article key={`${node.node_name}-${index}`}>
                <span>{index + 1}</span>
                <div>
                  <strong>{node.node_name ?? "unknown"}</strong>
                  <p>{node.output_summary ?? node.input_summary ?? "-"}</p>
                  {citations && (
                    <details style={{ marginTop: "0.5rem", fontSize: "0.8125rem", color: "#64748b" }}>
                      <summary style={{ cursor: "pointer", fontWeight: 500 }}>
                        引用证据 ({citations.length} 条 · {compactJson(node.metadata)})
                      </summary>
                      <div style={{ marginTop: "0.375rem", display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                        {citations.map((c: any, ci: number) => (
                          <span key={ci} style={{ fontSize: "0.75rem" }}>
                            [{c.source}] {c.title}
                          </span>
                        ))}
                      </div>
                    </details>
                  )}
                  {!citations && <small>{node.duration_ms ?? "-"} ms · {compactJson(node.metadata)}</small>}
                </div>
              </article>
            );
          })
        )}
      </div>
    </div>
  );
}
