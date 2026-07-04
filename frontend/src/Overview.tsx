import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { MetricCard } from "./PanelComponents";
import {
  listKnowledgeBases,
  listChatSessions,
  listTraceSummaries,
} from "./api";
import type { Health, TraceSummary } from "./types";

export default function Overview({ health }: { health: Health | null }) {
  const navigate = useNavigate();
  const [kbCount, setKbCount] = useState(0);
  const [sessionCount, setSessionCount] = useState(0);
  const [traces, setTraces] = useState<TraceSummary[]>([]);

  useEffect(() => {
    Promise.allSettled([
      listKnowledgeBases().then((list) => setKbCount(list.length)).catch(() => setKbCount(0)),
      listChatSessions().then((list) => setSessionCount(list.length)).catch(() => setSessionCount(0)),
      listTraceSummaries().then(setTraces).catch(() => setTraces([])),
    ]);
  }, []);

  const healthTone = health?.status === "UP" ? "good" : health ? "danger" : "warn";
  const healthValue = health?.status ?? "检查中...";
  const healthDetail = health ? `服务: ${health.service}` : "等待 API 返回";

  return (
    <div className="content-grid overview-grid workspace-grid">
      <MetricCard
        label="API 健康状态"
        value={healthValue}
        detail={healthDetail}
        tone={healthTone}
      />
      <MetricCard
        label="知识库"
        value={String(kbCount)}
        detail="当前知识库空间数"
        tone={kbCount > 0 ? "good" : "default"}
      />
      <MetricCard
        label="活跃会话"
        value={String(sessionCount)}
        detail="RAG 问答会话数"
        tone={sessionCount > 0 ? "info" : "default"}
      />
      <MetricCard
        label="执行轨迹"
        value={String(traces.length)}
        detail="Agent Trace 记录数"
        tone={traces.length > 0 ? "info" : "default"}
      />
      <MetricCard
        label="最近活动"
        value={traces.length > 0
          ? new Date(traces[0].created_at ?? "").toLocaleDateString()
          : "-"}
        detail="最新 Trace 日期"
        tone="default"
      />

      <div className="span-8">
        <div className="section-heading">
          <h3>最近执行轨迹</h3>
          <span>{traces.length} 条</span>
        </div>
        <div className="activity-list">
          {traces.length === 0 ? (
            <p className="empty-state">暂无执行轨迹数据</p>
          ) : (
            traces.slice(0, 6).map((trace) => (
              <article
                key={trace.trace_id}
                onClick={() => navigate(`/trace?traceId=${trace.trace_id}`)}
              >
                <code>{trace.trace_id}</code>
                <strong>{trace.latest_node ?? "-"}</strong>
                <span>{trace.node_count} 节点</span>
                <small>{trace.created_at ? new Date(trace.created_at).toLocaleString() : "-"}</small>
              </article>
            ))
          )}
        </div>
      </div>

      <div className="span-4">
        <div className="section-heading">
          <h3>快捷入口</h3>
        </div>
        <div className="workflow-grid">
          <button className="wf-kb" type="button" onClick={() => navigate("/knowledge")}>
            <strong>知识库</strong>
            <span>管理知识库空间与文档</span>
          </button>
          <button className="wf-chat" type="button" onClick={() => navigate("/chat")}>
            <strong>RAG 问答</strong>
            <span>基于知识库的检索问答</span>
          </button>
          <button className="wf-trace" type="button" onClick={() => navigate("/trace")}>
            <strong>执行轨迹</strong>
            <span>查看 Agent 节点时间线</span>
          </button>
        </div>
      </div>
    </div>
  );
}
