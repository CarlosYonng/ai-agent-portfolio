import { FormEvent, useEffect, useState } from "react";
import {
  askChat,
  listChatMessages,
  listChatSessions,
  listKnowledgeBases,
  getErrorMessage,
} from "./api";
import type { ChatMessage, ChatSession, Citation, KnowledgeBase } from "./types";

const INTERNAL_ROLES = ["SUPER_ADMIN", "PLATFORM_ADMIN"];

export default function ChatPanel({
  onTrace,
  goTrace,
  role,
}: {
  onTrace: (traceId: string) => void;
  goTrace: () => void;
  role?: string;
}) {
  const [spaces, setSpaces] = useState<KnowledgeBase[]>([]);
  const [selectedKb, setSelectedKb] = useState<number | undefined>();
  const [chatTab, setChatTab] = useState<"ask" | "history">("ask");
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [historyError, setHistoryError] = useState("");
  const [activeSessionId, setActiveSessionId] = useState<number | undefined>();
  const [historyMessages, setHistoryMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState("");
  const [loading, setLoading] = useState(false);
  const [agentStatus, setAgentStatus] = useState("");
  const [chatError, setChatError] = useState("");
  const [filterKeyword, setFilterKeyword] = useState("");
  const [filterStartDate, setFilterStartDate] = useState("");
  const [filterEndDate, setFilterEndDate] = useState("");
  const [conversation, setConversation] = useState<Array<{
    role: string;
    content: string;
    citations?: Citation[];
    traceId?: string;
    messageId?: number;
  }>>([]);
  const isInternalRole = INTERNAL_ROLES.includes(role ?? "");
  const currentSpace = spaces.find((space) => space.id === selectedKb);

  async function refreshSessions(filters?: { keyword?: string; startDate?: string; endDate?: string }) {
    setHistoryError("");
    try {
      setSessions(await listChatSessions(filters));
    } catch (error) {
      setHistoryError(getErrorMessage(error, "历史记录暂时加载失败，请稍后重试。"));
      setSessions([]);
    }
  }

  function kbName(kbId?: number) {
    return spaces.find((space) => space.id === kbId)?.name ?? "客户全局";
  }

  useEffect(() => {
    listKnowledgeBases()
      .then((items) => {
        setSpaces(items);
        setSelectedKb(isInternalRole ? undefined : items[0]?.id);
      })
      .catch(() => setSpaces([]));
  }, [isInternalRole]);

  useEffect(() => { refreshSessions(); }, []);

  function newSession() {
    setActiveSessionId(undefined);
    setConversation([]);
    setChatError("");
  }

  async function loadSession(sessionId: number) {
    const messages = await listChatMessages(sessionId);
    setActiveSessionId(sessionId);
    setHistoryMessages(messages);
    const lastAnswer = [...messages].reverse().find((item) => item.role === "assistant");
    setChatTab("history");
    if (lastAnswer?.traceId) {
      onTrace(lastAnswer.traceId);
    }
  }

  /** 从历史记录加载会话到提问区继续对话 */
  function continueSession(sessionId: number, messages: ChatMessage[]) {
    setActiveSessionId(sessionId);
    setConversation(messages.map(m => ({
      role: m.role,
      content: m.content,
      traceId: m.traceId,
      messageId: m.id,
      citations: parseCitations(m.citations),
    })));
    setChatTab("ask");
  }

  /** 解析 assistant 消息的 citations JSON 字段为数组，解析失败返回空数组 */
  function parseCitations(raw: string | undefined): Citation[] {
    if (!raw) return [];
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const currentQuestion = question.trim();
    if (!currentQuestion) return;
    setChatError("");
    setLoading(true);
    setAgentStatus("正在提交问题");
    setQuestion("");

    // 追加用户问题到对话
    const updated = [...conversation, { role: "user", content: currentQuestion }];
    setConversation(updated);

    // 先创建一个空的助手回答占位
    const assistantIndex = updated.length;
    setConversation(prev => [...prev, { role: "assistant", content: "处理中...", citations: [] }]);

    try {
      const response = await askChat({
        kbId: selectedKb,
        sessionId: activeSessionId,
        question: currentQuestion,
      });

      setConversation(prev => {
        const next = [...prev];
        if (next[assistantIndex]) {
          next[assistantIndex] = {
            ...next[assistantIndex],
            content: response.answer ?? "暂无回答",
            traceId: response.traceId,
            messageId: response.sessionId,
            citations: response.citations as any,
          };
        }
        return next;
      });

      if (response.traceId) onTrace(response.traceId);
      if (response.sessionId) setActiveSessionId(response.sessionId);
      setLoading(false);
      setAgentStatus("");
      await refreshSessions();
    } catch (error) {
      setChatError(getErrorMessage(error, "问题暂时没有发送成功，请稍后重试。"));
      setConversation(prev => prev.slice(0, -1));
      setLoading(false);
      setAgentStatus("");
    }
  }

  return (
    <section className="content-grid workspace-grid">
      <div className="panel span-12">
        <div className="tabs">
          <button className={chatTab === "ask" ? "tab active" : "tab"} type="button" onClick={() => setChatTab("ask")}>
            当前对话
          </button>
          <button className={chatTab === "history" ? "tab active" : "tab"} type="button" onClick={() => { setChatTab("history"); refreshSessions(); }}>
            历史记录
          </button>
        </div>

        {chatTab === "ask" ? (
          <div className="tab-body content-grid" style={{ alignItems: "start" }}>
            {/* 左侧：提问表单 */}
            <form className="span-5 debug-panel" onSubmit={submit} style={{ position: "sticky", top: "1rem" }}>
              <div className="panel-title">
                <div>
                  <h2>提问</h2>
                  <small>{activeSessionId
                    ? `会话 #${activeSessionId} · 共 ${Math.ceil(conversation.length / 2)} 轮`
                    : "新会话"}</small>
                </div>
                {activeSessionId && (
                  <button className="ghost compact" type="button" onClick={newSession} title="开始新会话">新会话</button>
                )}
              </div>
              <label>
                问答知识库
                <select
                  value={selectedKb ?? 0}
                  onChange={(event) => {
                    const next = Number(event.target.value) || undefined;
                    setSelectedKb(next);
                    if (activeSessionId) newSession();
                  }}
                >
                  {isInternalRole && <option value={0}>全局知识库</option>}
                  {spaces.map((space) => (
                    <option key={space.id} value={space.id}>{space.name}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>问题 <span className="text-red-500 font-bold">*</span></span>
                <textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="输入需要基于知识库回答的问题" required />
              </label>
              <button className="primary" disabled={loading} type="submit">{loading ? "处理中..." : "发送问题"}</button>
            </form>

            {/* 右侧：对话内容 */}
            <div className="span-7" style={{ minWidth: 0 }}>
              {chatError && <div className="inline-error" role="alert" style={{ marginBottom: "1rem" }}>{chatError}</div>}

              {conversation.length === 0 && !loading ? (
                <p className="empty-state">输入问题后开始对话，连续提问会自动在同一会话中保持上下文。</p>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}>
                  {loading && (
                    <div className="message-item" style={{ opacity: 0.6 }}>
                      <strong>助手</strong>
                      <p>{agentStatus || "正在处理..."}</p>
                    </div>
                  )}
                  {/* 将对话按轮分组：每 2 条为一轮 */}
                  {(() => {
                    const rounds: Array<{ round: number; user: typeof conversation[0]; assistant?: typeof conversation[0] }> = [];
                    for (let i = 0; i < conversation.length; i += 2) {
                      rounds.push({
                        round: Math.floor(i / 2) + 1,
                        user: conversation[i],
                        assistant: conversation[i + 1],
                      });
                    }
                    // 逆序：最新一轮在最上面
                    return [...rounds].reverse().map((round) => (
                      <div
                        key={round.round}
                        style={{
                          border: "1px solid #e2e8f0",
                          borderRadius: "0.75rem",
                          overflow: "hidden",
                          background: "#fff",
                        }}
                      >
                        {/* 轮次标题 */}
                        <div
                          style={{
                            padding: "0.5rem 1rem",
                            fontSize: "0.8125rem",
                            fontWeight: 600,
                            color: "#475569",
                            background: "#f8fafc",
                            borderBottom: "1px solid #e2e8f0",
                          }}
                        >
                          第 {round.round} 轮
                        </div>
                        {/* 用户问题 */}
                        <div style={{ padding: "0.75rem 1rem 0.5rem", background: "#fafafa" }}>
                          <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "#64748b", marginBottom: "0.25rem" }}>问</div>
                          <p style={{ margin: 0, fontSize: "0.9375rem", color: "#1e293b" }}>{round.user.content}</p>
                        </div>
                        {/* 助手回答 */}
                        {round.assistant && (
                          <div style={{ padding: "0.5rem 1rem 0.75rem" }}>
                            <hr style={{ margin: "0 0 0.75rem", border: "none", borderTop: "1px dashed #e2e8f0" }} />
                            <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "#2563eb", marginBottom: "0.25rem" }}>答</div>
                            <p style={{ margin: "0 0 0.75rem", fontSize: "0.9375rem", color: "#1e293b", lineHeight: 1.6 }}>{round.assistant.content}</p>
                            {round.assistant.traceId && (
                              <div className="trace-meta" style={{ marginTop: "0.5rem" }}>
                                <span>Agent Trace：<code>{round.assistant.traceId}</code></span>
                                <button className="ghost compact" type="button" onClick={() => { onTrace(round.assistant?.traceId ?? ""); goTrace(); }}>查看 Trace</button>
                              </div>
                            )}
                            {round.assistant.citations && round.assistant.citations.length > 0 && (
                              <details style={{ marginTop: "0.75rem", fontSize: "0.875rem", color: "#64748b" }}>
                                <summary style={{ cursor: "pointer", fontWeight: 500 }}>引用证据 ({round.assistant.citations.length} 条)</summary>
                                <div className="evidence-list" style={{ marginTop: "0.5rem", gridTemplateColumns: "1fr" }}>
                                  {round.assistant.citations.map((item, ci) => (
                                    <article key={ci} style={{ padding: "0.5rem", fontSize: "0.8125rem" }}>
                                      <strong>{item.title ?? item.chunk_id}</strong>
                                      <p style={{ marginTop: "0.25rem", fontSize: "0.8125rem" }}>{item.text ?? "-"}</p>
                                    </article>
                                  ))}
                                </div>
                              </details>
                            )}
                          </div>
                        )}
                      </div>
                    ));
                  })()}
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="tab-body content-grid">
            <div className="span-4">
              <div className="panel-title">
                <h2>历史会话</h2>
                <span>{sessions.length} 条</span>
              </div>
              <div className="session-filter">
                <input className="filter-input" placeholder="搜索标题..." value={filterKeyword} onChange={e => setFilterKeyword(e.target.value)} />
                <div className="filter-date-group">
                  <input type="date" value={filterStartDate} onChange={e => setFilterStartDate(e.target.value)} />
                  <span>~</span>
                  <input type="date" value={filterEndDate} onChange={e => setFilterEndDate(e.target.value)} />
                </div>
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button className="ghost compact" onClick={() => refreshSessions({
                    keyword: filterKeyword || undefined,
                    startDate: filterStartDate || undefined,
                    endDate: filterEndDate || undefined,
                  })}>搜索</button>
                  <button className="ghost compact" onClick={() => { setFilterKeyword(""); setFilterStartDate(""); setFilterEndDate(""); refreshSessions(); }}>重置</button>
                </div>
              </div>
              <div className="session-list">
                {sessions.length === 0 ? (
                  <p className="empty-state">{historyError || "暂无历史。发送一次问题后，会在这里出现。"}</p>
                ) : (
                  sessions.map((session) => (
                    <div key={session.id} className="session-item" style={{ cursor: "default" }}>
                      <button
                        style={{ textAlign: "left", border: 0, background: "none", width: "100%", padding: 0, cursor: "pointer" }}
                        type="button"
                        onClick={() => loadSession(session.id)}
                      >
                        <strong>{session.title}</strong>
                        <span>{kbName(session.kbId)} · session #{session.id}</span>
                      </button>
                      <button
                        className="ghost compact"
                        type="button"
                        style={{ marginTop: "0.25rem" }}
                        onClick={async () => {
                          const msgs = await listChatMessages(session.id);
                          continueSession(session.id, msgs);
                        }}
                      >
                        继续对话
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>

            <div className="span-8">
              <div className="panel-title">
                <h2>会话内容</h2>
                <span>{activeSessionId ? `session #${activeSessionId}` : "选择左侧历史会话"}</span>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
                {historyMessages.length === 0 ? (
                  <p className="empty-state">选择一条历史会话后展示问题、答案和关联 Trace。</p>
                ) : (
                  (() => {
                    const rounds: Array<{ round: number; user: typeof historyMessages[0]; assistant?: typeof historyMessages[0] }> = [];
                    for (let i = 0; i < historyMessages.length; i += 2) {
                      rounds.push({
                        round: Math.floor(i / 2) + 1,
                        user: historyMessages[i],
                        assistant: historyMessages[i + 1],
                      });
                    }
                    return rounds.map((round) => (
                      <div key={round.round} style={{
                        border: "1px solid #e2e8f0",
                        borderRadius: "0.75rem",
                        overflow: "hidden",
                        background: "#fff",
                      }}>
                        <div style={{
                          padding: "0.5rem 1rem",
                          fontSize: "0.8125rem",
                          fontWeight: 600,
                          color: "#475569",
                          background: "#f8fafc",
                          borderBottom: "1px solid #e2e8f0",
                        }}>
                          第 {round.round} 轮
                        </div>
                        <div style={{ padding: "0.75rem 1rem 0.5rem", background: "#fafafa" }}>
                          <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "#64748b", marginBottom: "0.25rem" }}>问</div>
                          <p style={{ margin: 0, fontSize: "0.9375rem", color: "#1e293b" }}>{round.user.content}</p>
                        </div>
                        {round.assistant && (
                          <div style={{ padding: "0.5rem 1rem 0.75rem" }}>
                            <hr style={{ margin: "0 0 0.75rem", border: "none", borderTop: "1px dashed #e2e8f0" }} />
                            <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "#2563eb", marginBottom: "0.25rem" }}>答</div>
                            <p style={{ margin: "0 0 0.75rem", fontSize: "0.9375rem", color: "#1e293b", lineHeight: 1.6 }}>{round.assistant.content}</p>
                            {round.assistant.traceId && (
                              <div className="trace-meta">
                                <span>Agent Trace：<code>{round.assistant.traceId}</code></span>
                                <button className="ghost compact" type="button" onClick={() => { onTrace(round.assistant?.traceId ?? ""); goTrace(); }}>查看 Trace</button>
                              </div>
                            )}
                            {(() => {
                              try {
                                const parsed = round.assistant.citations ? JSON.parse(round.assistant.citations) : null;
                                if (Array.isArray(parsed) && parsed.length > 0) {
                                  return (
                                    <details style={{ marginTop: "0.75rem", fontSize: "0.875rem", color: "#64748b" }}>
                                      <summary style={{ cursor: "pointer", fontWeight: 500 }}>引用证据 ({parsed.length} 条)</summary>
                                      <div className="evidence-list" style={{ marginTop: "0.5rem", gridTemplateColumns: "1fr" }}>
                                        {parsed.map((item: any, ci: number) => (
                                          <article key={ci} style={{ padding: "0.5rem", fontSize: "0.8125rem" }}>
                                            <strong>{item.title ?? item.chunk_id}</strong>
                                            <p style={{ marginTop: "0.25rem", fontSize: "0.8125rem" }}>{item.text ?? "-"}</p>
                                          </article>
                                        ))}
                                      </div>
                                    </details>
                                  );
                                }
                              } catch { /* JSON 解析失败不展示 */ }
                              return null;
                            })()}
                          </div>
                        )}
                      </div>
                    ));
                  })()
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
