import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  createDocument,
  createKnowledgeBase,
  diagnoseIncident,
  getHealth,
  getTrace,
  listDocuments,
  listKnowledgeBases
} from "./api";
import { streamChat } from "./stream";
import type {
  Citation,
  Health,
  IncidentResponse,
  KnowledgeBase,
  KnowledgeDocument,
  TraceNode
} from "./types";
import { compactJson, formatConfidence } from "./utils";

type View = "overview" | "kb" | "chat" | "incident" | "trace";

const tenantId = 1;
const userId = 1;

const navItems: Array<{ key: View; label: string; mark: string }> = [
  { key: "overview", label: "总览", mark: "O" },
  { key: "kb", label: "知识库", mark: "K" },
  { key: "chat", label: "RAG 问答", mark: "R" },
  { key: "incident", label: "故障诊断", mark: "I" },
  { key: "trace", label: "Trace", mark: "T" }
];

export default function App() {
  const [view, setView] = useState<View>("overview");
  const [health, setHealth] = useState<Health | null>(null);
  const [traceId, setTraceId] = useState("");

  useEffect(() => {
    getHealth().then(setHealth).catch(() => setHealth({ status: "DOWN", service: "backend-java" }));
  }, []);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">A</div>
          <div>
            <strong>Agent Ops</strong>
            <span>RAG 与故障诊断控制台</span>
          </div>
        </div>
        <nav>
          {navItems.map((item) => (
            <button
              key={item.key}
              className={view === item.key ? "nav-item active" : "nav-item"}
              onClick={() => setView(item.key)}
              type="button"
            >
              <span>{item.mark}</span>
              {item.label}
            </button>
          ))}
        </nav>
      </aside>

      <main>
        <header className="topbar">
          <div>
            <p className="eyebrow">Java Backend + Python Agent</p>
            <h1>{navItems.find((item) => item.key === view)?.label}</h1>
          </div>
          <div className={health?.status === "UP" ? "status up" : "status down"}>
            <span />
            {health?.service ?? "backend-java"} · {health?.status ?? "checking"}
          </div>
        </header>

        {view === "overview" && <Overview health={health} />}
        {view === "kb" && <KnowledgeBasePanel />}
        {view === "chat" && <ChatPanel onTrace={setTraceId} goTrace={() => setView("trace")} />}
        {view === "incident" && <IncidentPanel />}
        {view === "trace" && <TracePanel initialTraceId={traceId} />}
      </main>
    </div>
  );
}

function Overview({ health }: { health: Health | null }) {
  return (
    <section className="content-grid overview-grid">
      <div className="panel span-2">
        <div className="panel-title">
          <h2>链路拓扑</h2>
          <span>本地调试和 Docker 演示共用同一套 API 路径</span>
        </div>
        <div className="topology" aria-label="Agent 项目链路拓扑">
          {["Frontend", "Java API", "AI Service", "Vector / Graph / MySQL"].map((label, index) => (
            <div className="node" key={label}>
              <span>{index + 1}</span>
              <strong>{label}</strong>
            </div>
          ))}
        </div>
      </div>
      <MetricCard label="后端状态" value={health?.status ?? "checking"} detail={health?.time ?? "-"} />
      <MetricCard label="默认租户" value="tenant 1" detail="演示数据隔离上下文" />
      <MetricCard label="入口" value="/api" detail="Vite 与 Nginx 都代理到 Java" />
      <MetricCard label="模型" value="mock" detail="配置真实 Key 后可切换" />
    </section>
  );
}

function MetricCard({ label, value, detail }: { label: string; value: string; detail: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </div>
  );
}

function KnowledgeBasePanel() {
  const [spaces, setSpaces] = useState<KnowledgeBase[]>([]);
  const [selectedKb, setSelectedKb] = useState<number>(1);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [spaceName, setSpaceName] = useState("Demo Knowledge Base");
  const [docTitle, setDocTitle] = useState("payment_callback.md");
  const [message, setMessage] = useState("");

  const refresh = async (kbId = selectedKb) => {
    const nextSpaces = await listKnowledgeBases(tenantId);
    setSpaces(nextSpaces);
    const id = kbId || nextSpaces[0]?.id || 1;
    setSelectedKb(id);
    setDocuments(await listDocuments(tenantId, id));
  };

  useEffect(() => {
    refresh().catch((error) => setMessage(error.message));
  }, []);

  async function submitSpace(event: FormEvent) {
    event.preventDefault();
    const result = await createKnowledgeBase({
      tenantId,
      name: spaceName,
      description: "前端控制台创建的知识库空间",
      visibility: "PRIVATE"
    });
    setMessage(`已创建知识库 #${result.id}`);
    await refresh(result.id);
  }

  async function submitDocument(event: FormEvent) {
    event.preventDefault();
    const result = await createDocument({
      tenantId,
      kbId: selectedKb,
      title: docTitle,
      sourceType: "MARKDOWN",
      sourceUri: `datasets/kb_docs/${docTitle}`
    });
    setMessage(`已登记文档 #${result.id}`);
    await refresh(selectedKb);
  }

  return (
    <section className="content-grid">
      <form className="panel" onSubmit={submitSpace}>
        <div className="panel-title">
          <h2>知识库空间</h2>
          <span>{message || "创建后可登记文档元数据"}</span>
        </div>
        <label>
          名称
          <input value={spaceName} onChange={(event) => setSpaceName(event.target.value)} />
        </label>
        <button className="primary" type="submit">创建知识库</button>
      </form>

      <form className="panel" onSubmit={submitDocument}>
        <div className="panel-title">
          <h2>文档登记</h2>
          <span>真实切片和向量入库由导入脚本负责</span>
        </div>
        <label>
          知识库
          <select value={selectedKb} onChange={(event) => refresh(Number(event.target.value))}>
            {spaces.map((space) => (
              <option key={space.id} value={space.id}>{space.name}</option>
            ))}
          </select>
        </label>
        <label>
          文档标题
          <input value={docTitle} onChange={(event) => setDocTitle(event.target.value)} />
        </label>
        <button className="primary" type="submit">登记文档</button>
      </form>

      <div className="panel span-2">
        <div className="panel-title">
          <h2>文档列表</h2>
          <span>{documents.length} 条记录</span>
        </div>
        <DataTable
          headers={["ID", "标题", "来源", "状态", "版本"]}
          rows={documents.map((doc) => [
            doc.id,
            doc.title,
            doc.sourceType ?? "-",
            doc.status ?? "-",
            doc.version ?? "-"
          ])}
        />
      </div>
    </section>
  );
}

function ChatPanel({ onTrace, goTrace }: { onTrace: (traceId: string) => void; goTrace: () => void }) {
  const [question, setQuestion] = useState("PAY_5001 是什么意思？应该怎么处理？");
  const [answer, setAnswer] = useState("");
  const [citations, setCitations] = useState<Citation[]>([]);
  const [traceId, setTraceId] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setAnswer("");
    setCitations([]);
    setTraceId("");
    setLoading(true);
    try {
      await streamChat({ tenantId, userId, question }, (item) => {
        if (item.type === "metadata" && item.traceId) {
          setTraceId(item.traceId);
          onTrace(item.traceId);
        }
        if (item.type === "delta") {
          setAnswer((current) => current + (item.content ?? ""));
        }
        if (item.type === "citations") {
          setCitations(item.citations ?? []);
        }
        if (item.type === "error") {
          setAnswer(item.content ?? "请求失败");
        }
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="content-grid">
      <form className="panel span-2" onSubmit={submit}>
        <div className="panel-title">
          <h2>RAG 问答</h2>
          <span>{loading ? "Agent 正在生成答案" : "SSE 逐段返回答案"}</span>
        </div>
        <textarea value={question} onChange={(event) => setQuestion(event.target.value)} />
        <button className="primary" disabled={loading} type="submit">{loading ? "生成中" : "发送问题"}</button>
      </form>

      <div className="panel span-2 answer-panel">
        <div className="panel-title">
          <h2>答案</h2>
          {traceId && <button className="ghost" onClick={goTrace} type="button">查看 Trace</button>}
        </div>
        <p>{answer || "等待提问后展示答案。"}</p>
      </div>

      <div className="panel span-2">
        <div className="panel-title">
          <h2>引用证据</h2>
          <span>{citations.length} 条</span>
        </div>
        <div className="evidence-list">
          {citations.map((item, index) => (
            <article key={`${item.chunk_id}-${index}`}>
              <strong>{item.title ?? item.chunk_id}</strong>
              <span>score {item.score?.toFixed(3) ?? "-"}</span>
              <p>{item.preview ?? "-"}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function IncidentPanel() {
  const [question, setQuestion] = useState("订单创建接口偶发 500，traceId=demo-trace-001，请分析根因。");
  const [traceId, setTraceId] = useState("demo-trace-001");
  const [result, setResult] = useState<IncidentResponse | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      setResult(await diagnoseIncident({ tenantId, userId, service: "order", question, traceId, timeRange: "last_1h" }));
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="content-grid">
      <form className="panel span-2" onSubmit={submit}>
        <div className="panel-title">
          <h2>故障诊断</h2>
          <span>聚合日志、代码和历史工单证据</span>
        </div>
        <label>
          Trace ID
          <input value={traceId} onChange={(event) => setTraceId(event.target.value)} />
        </label>
        <textarea value={question} onChange={(event) => setQuestion(event.target.value)} />
        <button className="primary" disabled={loading} type="submit">{loading ? "诊断中" : "开始诊断"}</button>
      </form>

      <div className="panel span-2">
        <div className="panel-title">
          <h2>诊断结果</h2>
          <span>{result?.trace_id ?? "-"}</span>
        </div>
        <p className="summary">{result?.summary ?? "等待诊断结果。"}</p>
        <div className="split">
          <div>
            <h3>根因排序</h3>
            {(result?.root_causes ?? []).map((item, index) => (
              <article className="cause" key={`${item.cause}-${index}`}>
                <strong>{item.cause}</strong>
                <span>{formatConfidence(item.confidence)}</span>
                <p>{item.evidence}</p>
              </article>
            ))}
          </div>
          <div>
            <h3>处理动作</h3>
            <ul className="actions">
              {(result?.actions ?? []).map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
}

function TracePanel({ initialTraceId }: { initialTraceId: string }) {
  const [traceId, setTraceId] = useState(initialTraceId);
  const [nodes, setNodes] = useState<TraceNode[]>([]);
  const [message, setMessage] = useState("");

  useEffect(() => {
    setTraceId(initialTraceId);
  }, [initialTraceId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage("");
    try {
      const result = await getTrace(traceId);
      setNodes(result);
      setMessage(result.length ? "" : "没有查到 Trace 节点，先执行一次 RAG 问答。");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "查询失败");
    }
  }

  return (
    <section className="content-grid">
      <form className="panel span-2" onSubmit={submit}>
        <div className="panel-title">
          <h2>Trace 查询</h2>
          <span>{message || "查看 Agent 节点输入输出摘要"}</span>
        </div>
        <input value={traceId} onChange={(event) => setTraceId(event.target.value)} placeholder="tr_xxx" />
        <button className="primary" type="submit">查询 Trace</button>
      </form>
      <div className="panel span-2">
        <div className="timeline">
          {nodes.map((node, index) => (
            <article key={`${node.node_name}-${index}`}>
              <span>{index + 1}</span>
              <div>
                <strong>{node.node_name ?? "unknown"}</strong>
                <p>{node.output_summary ?? node.input_summary ?? "-"}</p>
                <small>{node.duration_ms ?? "-"} ms · {compactJson(node.metadata)}</small>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function DataTable({ headers, rows }: { headers: string[]; rows: Array<Array<string | number>> }) {
  const empty = useMemo(() => rows.length === 0, [rows]);
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr>
        </thead>
        <tbody>
          {empty ? (
            <tr><td colSpan={headers.length}>暂无数据</td></tr>
          ) : (
            rows.map((row, index) => (
              <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
