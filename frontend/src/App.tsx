import { FormEvent, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  ApiError,
  askChat,
  createDocument,
  createKnowledgeBase,
  deleteDocument,
  deleteKnowledgeBase,
  diagnoseIncident,
  getErrorContext,
  getErrorMessage,
  getErrorTitle,
  getHealth,
  getTrace,
  listIncidentHistory,
  listChatMessages,
  listChatSessions,
  listDocuments,
  listKnowledgeBases,
  listTraceSummaries,
  updateDocument,
  updateKnowledgeBase
} from "./api";
import type {
  Citation,
  ChatMessage,
  ChatSession,
  Health,
  IncidentHistory,
  IncidentResponse,
  KnowledgeBase,
  KnowledgeDocument,
  TraceNode,
  TraceSummary
} from "./types";
import { compactJson, formatConfidence } from "./utils";

type View = "overview" | "kb" | "chat" | "incident" | "trace";

const tenantId = 1;
const userId = 1;

const navItems: Array<{ key: View; label: string; mark: string; helper: string }> = [
  { key: "overview", label: "总览", mark: "总", helper: "运行态" },
  { key: "kb", label: "知识库", mark: "库", helper: "空间与文档" },
  { key: "chat", label: "RAG 问答", mark: "问", helper: "检索问答" },
  { key: "incident", label: "故障诊断", mark: "诊", helper: "根因与动作" },
  { key: "trace", label: "执行轨迹", mark: "迹", helper: "节点时间线" }
];

const pageCopy: Record<View, { title: string; description: string }> = {
  overview: {
    title: "Agent Ops 控制台",
    description: "查看当前服务状态、知识库、会话、诊断和 Agent Trace 的真实运行数据。"
  },
  kb: {
    title: "知识库",
    description: "管理当前租户下的知识库空间、文档登记和入库状态。"
  },
  chat: {
    title: "RAG 问答",
    description: "基于选定知识库提问，答案、引用证据和 Trace 会写入数据库历史。"
  },
  incident: {
    title: "研发故障诊断",
    description: "面向开发和运维的内部排障工具，聚合日志、代码和历史工单输出根因建议。"
  },
  trace: {
    title: "执行轨迹",
    description: "查看 RAG Agent 每个节点的输入输出摘要、耗时和元数据，不是 Java 服务调用链。"
  }
};

export default function App() {
  const [view, setView] = useState<View>("overview");
  const [health, setHealth] = useState<Health | null>(null);
  const [traceId, setTraceId] = useState("");
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const currentPage = pageCopy[view];

  useEffect(() => {
    getHealth().then(setHealth).catch(() => setHealth({ status: "DOWN", service: "backend-java" }));
  }, []);

  useEffect(() => {
    function onApiError(event: Event) {
      const detail = (event as CustomEvent<ApiError>).detail;
      setApiError(detail);
    }
    window.addEventListener("agent-api-error", onApiError);
    return () => window.removeEventListener("agent-api-error", onApiError);
  }, []);

  useEffect(() => {
    if (!apiError) {
      return;
    }
    const timer = window.setTimeout(() => setApiError(null), 5000);
    return () => window.clearTimeout(timer);
  }, [apiError]);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <button className="brand" type="button" onClick={() => setView("overview")}>
          <div className="brand-mark">A</div>
          <div>
            <strong>Agent Ops</strong>
            <span>RAG 与故障诊断控制台</span>
          </div>
        </button>
        <nav>
          {navItems.map((item) => (
            <button
              key={item.key}
              className={view === item.key ? "nav-item active" : "nav-item"}
              onClick={() => setView(item.key)}
              type="button"
            >
              <span>{item.mark}</span>
              <strong>{item.label}</strong>
              <small>{item.helper}</small>
            </button>
          ))}
        </nav>
      </aside>

      <main>
        <header className="topbar">
          <div>
            <h1>{currentPage.title}</h1>
          </div>
          <div className="topbar-actions">
            <ContextChip label="租户" value={`${tenantId}`} />
            <div className={health?.status === "UP" ? "status up" : health ? "status down" : "status checking"}>
              <span />
              API · {formatHealthStatus(health?.status)}
            </div>
          </div>
        </header>

        {apiError && (
          <div className="toast-region" aria-live="polite" aria-atomic="true">
            <div className="toast error" role="status">
              <div>
                <strong>{getErrorTitle(apiError)}</strong>
                <span>{getErrorMessage(apiError)}</span>
                <small>{getErrorContext(apiError)}</small>
              </div>
              <button className="toast-close" type="button" aria-label="关闭提示" onClick={() => setApiError(null)}>×</button>
            </div>
          </div>
        )}

        {view === "overview" && <Overview health={health} onNavigate={setView} />}
        {view === "kb" && <KnowledgeBasePanel />}
        {view === "chat" && <ChatPanel onTrace={setTraceId} goTrace={() => setView("trace")} />}
        {view === "incident" && <IncidentPanel />}
        {view === "trace" && <TracePanel initialTraceId={traceId} />}
        <p className="page-footnote">{currentPage.description}</p>
      </main>
    </div>
  );
}

function ContextChip({ label, value }: { label: string; value: string }) {
  return (
    <div className="context-chip">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function Overview({ health, onNavigate }: { health: Health | null; onNavigate: (view: View) => void }) {
  const [summary, setSummary] = useState({
    kbCount: 0,
    sessionCount: 0,
    incidentCount: 0,
    traces: [] as TraceSummary[],
    loading: true,
    error: ""
  });

  useEffect(() => {
    let cancelled = false;

    async function loadSummary() {
      setSummary((current) => ({ ...current, loading: true, error: "" }));
      const [kbResult, sessionResult, incidentResult, traceResult] = await Promise.allSettled([
        listKnowledgeBases(tenantId),
        listChatSessions(tenantId, userId),
        listIncidentHistory(tenantId, userId),
        listTraceSummaries(tenantId)
      ]);

      if (cancelled) {
        return;
      }

      const knowledgeBases = kbResult.status === "fulfilled" ? kbResult.value : [];
      const sessions = sessionResult.status === "fulfilled" ? sessionResult.value : [];
      const incidents = incidentResult.status === "fulfilled" ? incidentResult.value : [];
      const traces = traceResult.status === "fulfilled" ? traceResult.value : [];
      const hasError = [kbResult, sessionResult, incidentResult, traceResult].some((item) => item.status === "rejected");

      setSummary({
        kbCount: knowledgeBases.length,
        sessionCount: sessions.length,
        incidentCount: incidents.length,
        traces,
        loading: false,
        error: hasError ? "部分数据暂未更新" : ""
      });
    }

    loadSummary();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section className="content-grid overview-grid">
      <MetricCard label="API Health" value={formatHealthStatus(health?.status)} detail={health?.time ? `Last check: ${health.time}` : "Last check: -"} tone={health?.status === "UP" ? "good" : health ? "danger" : "warn"} />
      <MetricCard label="汇总状态" value={summary.loading ? "LOADING" : summary.error ? "PARTIAL" : "OK"} detail={summary.error || "总览数据已更新"} tone={summary.loading ? "warn" : summary.error ? "danger" : "good"} />
      <MetricCard label="知识库" value={summary.loading ? "..." : `${summary.kbCount}`} detail="当前租户空间" tone="info" />
      <MetricCard label="RAG 会话" value={summary.loading ? "..." : `${summary.sessionCount}`} detail="已保存问答历史" tone="good" />
      <MetricCard label="诊断记录" value={summary.loading ? "..." : `${summary.incidentCount}`} detail="研发排障历史" tone="warn" />
      <MetricCard label="执行轨迹" value={summary.loading ? "..." : `${summary.traces.length}`} detail="已记录执行轨迹" tone="info" />

      <div className="panel span-7">
        <div className="panel-title">
          <h2>最近 Agent Trace</h2>
          <span>{summary.traces.length} 条</span>
        </div>
        <div className="activity-list">
          {summary.traces.length === 0 ? (
            <p className="empty-state">{summary.loading ? "正在加载 Trace 汇总。" : "暂无 Agent Trace。完成一次 RAG 问答后会在这里出现。"}</p>
          ) : (
            summary.traces.slice(0, 5).map((item) => (
              <article key={item.trace_id}>
                <code>{item.trace_id}</code>
                <strong>{item.latest_node ?? "unknown"}</strong>
                <span>{item.node_count} 个节点</span>
                <small>{item.created_at ?? "-"}</small>
              </article>
            ))
          )}
        </div>
      </div>

      <div className="panel span-5">
        <div className="panel-title">
          <h2>工作流入口</h2>
          <span>进入核心功能</span>
        </div>
        <div className="workflow-grid">
          <button type="button" onClick={() => onNavigate("kb")}>
            <strong>知识库治理</strong>
            <span>空间、文档登记、入库状态</span>
          </button>
          <button type="button" onClick={() => onNavigate("chat")}>
            <strong>RAG 调试</strong>
            <span>REST 问答、引用证据、历史入库</span>
          </button>
          <button type="button" onClick={() => onNavigate("incident")}>
            <strong>故障诊断</strong>
            <span>根因排序、动作建议</span>
          </button>
          <button type="button" onClick={() => onNavigate("trace")}>
            <strong>执行轨迹</strong>
            <span>Agent 节点耗时和元数据</span>
          </button>
        </div>
      </div>
    </section>
  );
}

function MetricCard({ label, value, detail, tone = "default" }: { label: string; value: string; detail: string; tone?: "default" | "good" | "warn" | "danger" | "info" }) {
  return (
    <div className={`metric ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </div>
  );
}

function StatusBadge({ value }: { value?: string }) {
  const normalized = (value ?? "-").toLowerCase();
  const tone = normalized.includes("fail") || normalized.includes("down") ? "danger" : normalized.includes("pending") || normalized.includes("mock") ? "warn" : normalized.includes("success") || normalized.includes("indexed") || normalized.includes("up") ? "good" : "default";
  return <span className={`badge ${tone}`}>{formatStatusText(value)}</span>;
}

function formatStatusText(value?: string) {
  if (!value) {
    return "-";
  }
  const normalized = value.toLowerCase();
  if (normalized === "mock") {
    return "离线兜底";
  }
  if (normalized === "up") {
    return "正常";
  }
  if (normalized === "down") {
    return "异常";
  }
  if (normalized === "pending") {
    return "待处理";
  }
  if (normalized === "success") {
    return "成功";
  }
  if (normalized === "indexed") {
    return "已入库";
  }
  if (normalized === "failed" || normalized === "fail") {
    return "失败";
  }
  if (normalized === "checking") {
    return "检查中";
  }
  return value;
}

function formatHealthStatus(status?: string) {
  const normalized = (status ?? "checking").toLowerCase();
  if (normalized === "up") {
    return "UP";
  }
  if (normalized === "checking") {
    return "CHECKING";
  }
  if (normalized === "down") {
    return "DOWN";
  }
  return status ?? "CHECKING";
}

function KnowledgeBasePanel() {
  const [spaces, setSpaces] = useState<KnowledgeBase[]>([]);
  const [selectedKb, setSelectedKb] = useState<number | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [editingKbId, setEditingKbId] = useState<number | null>(null);
  const [kbDetailTab, setKbDetailTab] = useState<"register" | "documents">("register");
  const [spaceName, setSpaceName] = useState("");
  const [spaceDescription, setSpaceDescription] = useState("");
  const [docTitle, setDocTitle] = useState("");
  const [docSourceType, setDocSourceType] = useState("MARKDOWN");
  const [docSourceUri, setDocSourceUri] = useState("");
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null);
  const [message, setMessage] = useState("");

  const currentSpace = spaces.find((space) => space.id === selectedKb);
  const isEditingDocument = selectedDocId !== null;

  function syncSpaceForm(space?: KnowledgeBase) {
    if (!space) {
      setEditingKbId(null);
      setSpaceName("");
      setSpaceDescription("");
      return;
    }
    setSpaceName(space.name);
    setSpaceDescription(space.description ?? "");
    setEditingKbId(space.id);
  }

  function syncDocumentForm(document?: KnowledgeDocument) {
    if (!document) {
      setSelectedDocId(null);
      setDocTitle("");
      setDocSourceType("MARKDOWN");
      setDocSourceUri("");
      return;
    }
    setSelectedDocId(document.id);
    setDocTitle(document.title);
    setDocSourceType(document.sourceType ?? "MARKDOWN");
    setDocSourceUri(document.sourceUri ?? "");
  }

  const refreshSpaces = async () => {
    const nextSpaces = await listKnowledgeBases(tenantId);
    setSpaces(nextSpaces);
    return nextSpaces;
  };

  const refreshDocuments = async (kbId: number) => {
    const nextDocuments = await listDocuments(tenantId, kbId);
    setDocuments(nextDocuments);
    if (selectedDocId && nextDocuments.some((document) => document.id === selectedDocId)) {
      syncDocumentForm(nextDocuments.find((document) => document.id === selectedDocId));
    } else {
      syncDocumentForm();
    }
  };

  const refresh = async (kbId = selectedKb) => {
    const nextSpaces = await refreshSpaces();
    if (!kbId || !nextSpaces.some((space) => space.id === kbId)) {
      setSelectedKb(null);
      setDocuments([]);
      syncDocumentForm();
      return;
    }
    setSelectedKb(kbId);
    syncSpaceForm(nextSpaces.find((space) => space.id === kbId));
    await refreshDocuments(kbId);
  };

  useEffect(() => {
    refreshSpaces().catch((error) => setMessage(getErrorMessage(error, "知识库列表暂时加载失败，请稍后重试。")));
  }, []);

  async function openKnowledgeBase(kbId: number) {
    setSelectedKb(kbId);
    setKbDetailTab("register");
    syncDocumentForm();
    await refreshDocuments(kbId);
  }

  async function submitSpace(event: FormEvent) {
    event.preventDefault();
    if (editingKbId) {
      await updateKnowledgeBase(editingKbId, {
        name: spaceName,
        description: spaceDescription
      });
      setMessage(`已更新知识库 #${editingKbId}`);
      await refresh(editingKbId);
      return;
    }
    const result = await createKnowledgeBase({ tenantId, name: spaceName, description: spaceDescription, visibility: "PRIVATE" });
    setMessage(`已创建知识库 #${result.id}`);
    syncSpaceForm();
    await refreshSpaces();
  }

  async function removeSpace(id = editingKbId) {
    if (!id || !window.confirm("删除知识库会同时删除文档元数据，确定继续？")) {
      return;
    }
    await deleteKnowledgeBase(id);
    setMessage(`已删除知识库 #${id}`);
    setSelectedKb(null);
    setDocuments([]);
    syncSpaceForm();
    syncDocumentForm();
    await refreshSpaces();
  }

  async function submitDocument(event: FormEvent) {
    event.preventDefault();
    if (!selectedKb || !currentSpace) {
      setMessage("请先创建或选择知识库");
      return;
    }
    if (selectedDocId) {
      await updateDocument(selectedDocId, {
        title: docTitle,
        sourceType: docSourceType,
        sourceUri: docSourceUri
      });
      setMessage(`已保存文档 ${docTitle}`);
      setDocuments(await listDocuments(tenantId, selectedKb));
      syncDocumentForm();
      return;
    }
    const result = await createDocument({
      tenantId,
      kbId: selectedKb,
      title: docTitle,
      sourceType: docSourceType,
      sourceUri: docSourceUri
    });
    setMessage(`已登记文档 ${docTitle}`);
    setDocuments(await listDocuments(tenantId, selectedKb));
    syncDocumentForm();
  }

  async function removeDocument(id: number) {
    if (!window.confirm("删除文档元数据后，需要另行清理向量和图谱索引，确定继续？")) {
      return;
    }
    await deleteDocument(id);
    setMessage(`已删除文档 #${id}`);
    if (selectedKb) {
      await refresh(selectedKb);
    }
  }

  if (!selectedKb || !currentSpace) {
    return (
      <section className="content-grid workspace-grid">
        <form className="panel span-4" onSubmit={submitSpace}>
          <div className="panel-title">
            <h2>{editingKbId ? "编辑知识库" : "新建知识库"}</h2>
            <span>{message || "先维护知识库，再进入详情登记文档"}</span>
          </div>
          <label>
            名称
            <input value={spaceName} onChange={(event) => setSpaceName(event.target.value)} placeholder="例如：支付知识库" required />
          </label>
          <label>
            说明
            <textarea value={spaceDescription} onChange={(event) => setSpaceDescription(event.target.value)} placeholder="描述该知识库的业务范围" />
          </label>
          <div className="button-row">
            <button className="primary" type="submit">{editingKbId ? "保存修改" : "创建知识库"}</button>
            <button className="ghost" type="button" onClick={() => syncSpaceForm()}>重置</button>
          </div>
        </form>

        <div className="panel span-8">
          <div className="panel-title">
            <h2>知识库列表</h2>
            <span>{spaces.length} 个空间 · 点击进入后维护文档</span>
          </div>
          <DataTable
            headers={["ID", "名称", "说明", "操作"]}
            rows={spaces.map((space) => [
              space.id,
              <strong key={`name-${space.id}`}>{space.name}</strong>,
              space.description ?? "-",
              <div className="table-actions" key={`actions-${space.id}`}>
                <button className="primary compact" type="button" onClick={() => openKnowledgeBase(space.id)}>进入</button>
                <button className="ghost compact" type="button" onClick={() => syncSpaceForm(space)}>编辑</button>
                <button className="danger compact" type="button" onClick={() => removeSpace(space.id)}>删除</button>
              </div>
            ])}
          />
        </div>
      </section>
    );
  }

  return (
    <section className="content-grid workspace-grid">
      <div className="span-12 compact-toolbar">
        <button className="ghost compact" type="button" onClick={() => { setSelectedKb(null); setDocuments([]); syncDocumentForm(); }}>
          返回知识库列表
        </button>
      </div>

      <div className="panel span-12">
        <div className="tabs">
          <button className={kbDetailTab === "register" ? "tab active" : "tab"} type="button" onClick={() => setKbDetailTab("register")}>
            文档登记
          </button>
          <button className={kbDetailTab === "documents" ? "tab active" : "tab"} type="button" onClick={() => setKbDetailTab("documents")}>
            文档列表
          </button>
        </div>

        {kbDetailTab === "register" ? (
          <form className="tab-body document-form" onSubmit={submitDocument}>
            <div className="panel-title">
              <h2>{isEditingDocument ? "编辑文档" : "文档登记"}</h2>
              <span>{message || currentSpace.name}</span>
            </div>
            <label>
              文档标题
              <input value={docTitle} onChange={(event) => setDocTitle(event.target.value)} placeholder="例如：payment_callback.md" required />
            </label>
            <label>
              来源类型
              <select value={docSourceType} onChange={(event) => setDocSourceType(event.target.value)}>
                <option value="MARKDOWN">MARKDOWN</option>
                <option value="PDF">PDF</option>
                <option value="URL">URL</option>
                <option value="DOCX">DOCX</option>
              </select>
            </label>
            <label>
              来源地址
              <input value={docSourceUri} onChange={(event) => setDocSourceUri(event.target.value)} placeholder="例如：datasets/kb_docs/payment_callback.md" required />
            </label>
            <div className="button-row">
              <button className="primary" type="submit">{isEditingDocument ? "保存修改" : "登记文档"}</button>
              <button className="ghost" type="button" onClick={() => syncDocumentForm()}>{isEditingDocument ? "取消编辑" : "清空表单"}</button>
            </div>
          </form>
        ) : (
          <div className="tab-body">
            <div className="panel-title">
              <h2>文档列表</h2>
              <span>{documents.length} 条记录 · 状态由导入/索引流程维护</span>
            </div>
            <DataTable
              headers={["标题", "来源", "状态", "版本", "操作"]}
              rows={documents.map((doc) => [
                doc.title,
                doc.sourceType ?? "-",
                <StatusBadge key={`status-${doc.id}`} value={doc.status ?? "PENDING"} />,
                doc.version ?? "-",
                <div className="table-actions" key={`actions-${doc.id}`}>
                  <button className="ghost compact" type="button" onClick={() => { syncDocumentForm(doc); setKbDetailTab("register"); }}>编辑</button>
                  <button className="danger compact" type="button" onClick={() => removeDocument(doc.id)}>删除</button>
                </div>
              ])}
            />
          </div>
        )}
      </div>
    </section>
  );
}

function ChatPanel({ onTrace, goTrace }: { onTrace: (traceId: string) => void; goTrace: () => void }) {
  const [spaces, setSpaces] = useState<KnowledgeBase[]>([]);
  const [selectedKb, setSelectedKb] = useState<number | undefined>();
  const [chatTab, setChatTab] = useState<"ask" | "history">("ask");
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [historyError, setHistoryError] = useState("");
  const [activeSessionId, setActiveSessionId] = useState<number | undefined>();
  const [historyMessages, setHistoryMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");
  const [citations, setCitations] = useState<Citation[]>([]);
  const [traceId, setTraceId] = useState("");
  const [loading, setLoading] = useState(false);
  const [chatError, setChatError] = useState("");
  const currentSpace = spaces.find((space) => space.id === selectedKb);

  async function refreshSessions() {
    setHistoryError("");
    try {
      setSessions(await listChatSessions(tenantId, userId));
    } catch (error) {
      setHistoryError(getErrorMessage(error, "历史记录暂时加载失败，请稍后重试。"));
      setSessions([]);
    }
  }

  function kbName(kbId?: number) {
    return spaces.find((space) => space.id === kbId)?.name ?? "租户全局";
  }

  useEffect(() => {
    listKnowledgeBases(tenantId)
      .then((items) => {
        setSpaces(items);
        if (items[0]) {
          setSelectedKb(items[0].id);
        }
      })
      .catch(() => setSpaces([]));
  }, []);

  useEffect(() => {
    refreshSessions();
  }, []);

  async function loadSession(sessionId: number) {
    const messages = await listChatMessages(tenantId, sessionId);
    setActiveSessionId(sessionId);
    setHistoryMessages(messages);
    const lastAnswer = [...messages].reverse().find((item) => item.role === "assistant");
    setChatTab("history");
    if (lastAnswer?.traceId) {
      setTraceId(lastAnswer.traceId);
      onTrace(lastAnswer.traceId);
    }
  }

  function clearAskForm() {
    setActiveSessionId(undefined);
    setAnswer("");
    setCitations([]);
    setTraceId("");
    setChatError("");
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setAnswer("");
    setCitations([]);
    setTraceId("");
    setChatError("");
    setLoading(true);
    try {
      const response = await askChat({ tenantId, userId, kbId: selectedKb, question: question.trim() });
      setAnswer(response.answer ?? "");
      setCitations(response.citations ?? []);
      setTraceId(response.traceId ?? "");
      setActiveSessionId(response.sessionId);
      if (response.traceId) {
        onTrace(response.traceId);
      }
      await refreshSessions();
    } catch (error) {
      setChatError(getErrorMessage(error, "问题暂时没有发送成功，请稍后重试。"));
      setAnswer("");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="content-grid workspace-grid">
      <div className="panel span-12">
        <div className="tabs">
          <button className={chatTab === "ask" ? "tab active" : "tab"} type="button" onClick={() => setChatTab("ask")}>
            新提问
          </button>
          <button className={chatTab === "history" ? "tab active" : "tab"} type="button" onClick={() => { setChatTab("history"); refreshSessions(); }}>
            历史记录
          </button>
        </div>

        {chatTab === "ask" ? (
          <div className="tab-body content-grid">
            <form className="span-5 debug-panel" onSubmit={submit}>
              <div className="panel-title">
                <h2>提问</h2>
                <span>{loading ? "Agent 正在处理，完成后会写入历史" : currentSpace ? `基于 ${currentSpace.name}` : "基于租户全局"}</span>
              </div>
              <label>
                问答知识库
                <select
                  value={selectedKb ?? 0}
                  onChange={(event) => {
                    const next = Number(event.target.value) || undefined;
                    setSelectedKb(next);
                    clearAskForm();
                  }}
                >
                  <option value={0}>租户全局</option>
                  {spaces.map((space) => (
                    <option key={space.id} value={space.id}>{space.name}</option>
                  ))}
                </select>
              </label>
              <label>
                问题
                <textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="输入需要基于知识库回答的问题" required />
              </label>
              <div className="button-row">
                <button className="primary" disabled={loading} type="submit">{loading ? "处理中" : "发送问题"}</button>
                <button className="ghost" type="button" onClick={clearAskForm}>清空结果</button>
              </div>
            </form>

            <div className="span-7 answer-panel">
              <div className="panel-title">
                <div>
                  <h2>答案</h2>
                  <small>{traceId ? `Trace ID: ${traceId}` : "请求完成后展示 Trace ID"}</small>
                </div>
                {traceId && <button className="ghost" onClick={goTrace} type="button">查看 Trace</button>}
              </div>
              {chatError && <div className="inline-error" role="alert">{chatError}</div>}
              <p>{answer || "等待提问后展示答案。完成后下方展示引用证据，并写入历史会话。"}</p>
            </div>

            <div className="span-12">
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
          </div>
        ) : (
          <div className="tab-body content-grid">
            <div className="span-4">
              <div className="panel-title">
                <h2>历史会话</h2>
                <span>{sessions.length} 条</span>
              </div>
              <div className="session-list">
                {sessions.length === 0 ? (
                  <p className="empty-state">{historyError || "暂无历史。发送一次问题后，会在这里出现。"}</p>
                ) : (
                  sessions.map((session) => (
                    <button
                      key={session.id}
                      className={session.id === activeSessionId ? "session-item active" : "session-item"}
                      type="button"
                      onClick={() => loadSession(session.id)}
                    >
                      <strong>{session.title}</strong>
                      <span>{kbName(session.kbId)} · session #{session.id}</span>
                    </button>
                  ))
                )}
              </div>
            </div>

            <div className="span-8">
              <div className="panel-title">
                <h2>会话内容</h2>
                <span>{activeSessionId ? `session #${activeSessionId}` : "选择左侧历史会话"}</span>
              </div>
              <div className="message-list">
                {historyMessages.length === 0 ? (
                  <p className="empty-state">选择一条历史会话后展示问题、答案和关联 Trace。</p>
                ) : (
                  historyMessages.map((message) => (
                    <article className={`message-item ${message.role}`} key={message.id}>
                      <strong>{message.role === "user" ? "用户" : "助手"}</strong>
                      <p>{message.content}</p>
                      {message.traceId && <button className="ghost compact" type="button" onClick={() => { onTrace(message.traceId ?? ""); goTrace(); }}>查看 Trace</button>}
                    </article>
                  ))
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function IncidentPanel() {
  const [incidentTab, setIncidentTab] = useState<"diagnose" | "history">("diagnose");
  const [history, setHistory] = useState<IncidentHistory[]>([]);
  const [incidentHistoryError, setIncidentHistoryError] = useState("");
  const [selectedHistory, setSelectedHistory] = useState<IncidentHistory | null>(null);
  const [serviceName, setServiceName] = useState("");
  const [question, setQuestion] = useState("");
  const [traceId, setTraceId] = useState("");
  const [result, setResult] = useState<IncidentResponse | null>(null);
  const [loading, setLoading] = useState(false);

  async function refreshHistory() {
    setIncidentHistoryError("");
    try {
      setHistory(await listIncidentHistory(tenantId, userId));
    } catch (error) {
      setIncidentHistoryError(getErrorMessage(error, "诊断历史暂时加载失败，请稍后重试。"));
      setHistory([]);
    }
  }

  useEffect(() => {
    refreshHistory();
  }, []);

  function parseIncident(historyItem: IncidentHistory) {
    if (!historyItem.response_json) {
      return null;
    }
    try {
      return JSON.parse(historyItem.response_json) as IncidentResponse;
    } catch {
      return null;
    }
  }

  function showHistory(item: IncidentHistory) {
    setSelectedHistory(item);
    setResult(parseIncident(item));
    setServiceName(item.serviceName);
    setQuestion(item.question);
    setTraceId(item.businessTraceId ?? "");
    setIncidentTab("history");
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      const nextTraceId = traceId.trim();
      setResult(await diagnoseIncident({
        tenantId,
        userId,
        service: serviceName.trim(),
        question: question.trim(),
        traceId: nextTraceId || undefined,
        timeRange: "last_1h"
      }));
      await refreshHistory();
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="content-grid workspace-grid">
      <div className="panel span-12">
        <div className="tabs">
          <button className={incidentTab === "diagnose" ? "tab active" : "tab"} type="button" onClick={() => setIncidentTab("diagnose")}>
            开始诊断
          </button>
          <button className={incidentTab === "history" ? "tab active" : "tab"} type="button" onClick={() => { setIncidentTab("history"); refreshHistory(); }}>
            历史记录
          </button>
        </div>

        {incidentTab === "diagnose" ? (
          <div className="tab-body content-grid">
            <form className="span-5 debug-panel" onSubmit={submit}>
              <div className="panel-title">
                <h2>诊断输入</h2>
                <span>{loading ? "后台诊断中，可切换页面后回来查看历史" : "聚合日志、代码和历史工单证据"}</span>
              </div>
              <label>
                服务名
                <input value={serviceName} onChange={(event) => setServiceName(event.target.value)} placeholder="例如：order" required />
              </label>
              <label>
                业务 Trace ID
                <input value={traceId} onChange={(event) => setTraceId(event.target.value)} placeholder="输入业务日志中的 traceId" />
              </label>
              <label>
                诊断问题
                <textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="描述异常现象、错误码或排查目标" required />
              </label>
              <button className="primary" disabled={loading} type="submit">{loading ? "诊断中" : "开始诊断"}</button>
            </form>

            <div className="span-7">
              <div className="panel-title">
                <h2>诊断结果</h2>
                <span>{result?.trace_id ?? "-"}</span>
              </div>
              <IncidentResult result={result} />
            </div>
          </div>
        ) : (
          <div className="tab-body content-grid">
            <div className="span-4">
              <div className="panel-title">
                <h2>诊断历史</h2>
                <span>{history.length} 条</span>
              </div>
              <div className="session-list">
                {history.length === 0 ? (
                  <p className="empty-state">{incidentHistoryError || "暂无诊断历史。完成一次诊断后会写入数据库。"}</p>
                ) : (
                  history.map((item) => (
                    <button className={selectedHistory?.id === item.id ? "session-item active" : "session-item"} key={item.id} type="button" onClick={() => showHistory(item)}>
                      <strong>{item.question}</strong>
                      <span>{item.businessTraceId || item.agentTraceId || "no trace"} · {item.serviceName}</span>
                    </button>
                  ))
                )}
              </div>
            </div>

            <div className="span-8">
              <div className="panel-title">
                <h2>历史结果</h2>
                <span>{selectedHistory ? `#${selectedHistory.id}` : "选择左侧记录"}</span>
              </div>
              <IncidentResult result={result} />
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function IncidentResult({ result }: { result: IncidentResponse | null }) {
  return (
    <>
      <p className="summary">{result?.summary ?? "等待诊断结果。"}</p>
      <div className="split">
        <div>
          <h3>根因排序</h3>
          {(result?.root_causes ?? []).map((item, index) => (
            <article className="cause" key={`${item.cause}-${index}`}>
              <div className="cause-head">
                <strong>{item.cause}</strong>
                <span>{formatConfidence(item.confidence)}</span>
              </div>
              <div className="confidence-bar" aria-label="根因置信度">
                <span style={{ width: `${Math.min(100, Math.round((item.confidence ?? 0) * 100))}%` }} />
              </div>
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
    </>
  );
}

function TracePanel({ initialTraceId }: { initialTraceId: string }) {
  const [traceTab, setTraceTab] = useState<"query" | "history">("query");
  const [traceId, setTraceId] = useState(initialTraceId);
  const [summaries, setSummaries] = useState<TraceSummary[]>([]);
  const [traceHistoryError, setTraceHistoryError] = useState("");
  const [nodes, setNodes] = useState<TraceNode[]>([]);
  const [message, setMessage] = useState("");

  async function refreshTraceHistory() {
    setTraceHistoryError("");
    try {
      setSummaries(await listTraceSummaries(tenantId));
    } catch (error) {
      setTraceHistoryError(getErrorMessage(error, "Trace 历史暂时加载失败，请稍后重试。"));
      setSummaries([]);
    }
  }

  useEffect(() => {
    setTraceId(initialTraceId);
    if (initialTraceId) {
      loadTrace(initialTraceId).catch((error) => setMessage(getErrorMessage(error, "Trace 暂时查询失败，请稍后重试。")));
    }
  }, [initialTraceId]);

  useEffect(() => {
    refreshTraceHistory();
  }, []);

  async function loadTrace(nextTraceId: string) {
    setMessage("");
    const result = await getTrace(nextTraceId);
    setTraceId(nextTraceId);
    setNodes(result);
    setMessage(result.length ? "" : "没有查到 Agent Trace 节点，先执行一次 RAG 问答。");
    await refreshTraceHistory();
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
          <div className="tab-body content-grid">
            <form className="span-4 trace-query-form" onSubmit={submit}>
              <div className="panel-title">
                <h2>Trace 查询</h2>
                <span>{message || "查看 Agent 节点输入输出摘要"}</span>
              </div>
              <label>
                Agent Trace ID
                <input value={traceId} onChange={(event) => setTraceId(event.target.value)} placeholder="tr_xxx" />
              </label>
              <button className="primary compact" type="submit">查询 Trace</button>
            </form>
            <div className="span-8">
              <TraceTimeline nodes={nodes} />
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
                    <button className={item.trace_id === traceId ? "session-item active" : "session-item"} key={item.trace_id} type="button" onClick={() => loadTrace(item.trace_id)}>
                      <strong>{item.trace_id}</strong>
                      <span>{item.node_count} 个节点 · {item.latest_node ?? "unknown"}</span>
                    </button>
                  ))
                )}
              </div>
            </div>
            <div className="span-8">
              <TraceTimeline nodes={nodes} />
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function TraceTimeline({ nodes }: { nodes: TraceNode[] }) {
  return (
    <>
      <div className="panel-title">
        <h2>Agent 节点时间线</h2>
        <span>{nodes.length} 个节点</span>
      </div>
      <div className="timeline">
        {nodes.length === 0 ? (
          <p className="empty-state">暂无 Trace 节点。先执行一次 RAG 问答，或输入已有 Trace ID 查询。</p>
        ) : (
          nodes.map((node, index) => (
            <article key={`${node.node_name}-${index}`}>
              <span>{index + 1}</span>
              <div>
                <strong>{node.node_name ?? "unknown"}</strong>
                <p>{node.output_summary ?? node.input_summary ?? "-"}</p>
                <small>{node.duration_ms ?? "-"} ms · {compactJson(node.metadata)}</small>
              </div>
            </article>
          ))
        )}
      </div>
    </>
  );
}

function DataTable({ headers, rows }: { headers: string[]; rows: Array<Array<ReactNode>> }) {
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
