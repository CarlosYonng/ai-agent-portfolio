import { useEffect, useState } from "react";
import { HashRouter, Routes, Route, useNavigate, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./AuthContext";
import ProtectedRoute from "./ProtectedRoute";
import LoginPage from "./LoginPage";
import RegisterPage from "./RegisterPage";
import type { ApiError } from "./api";
import type { Health } from "./types";
import { getHealth, getErrorTitle, getErrorMessage, getErrorContext } from "./api";
import { formatHealthStatus } from "./utils";
import Overview from "./Overview";
import KnowledgeBasePanel from "./KnowledgeBasePanel";
import ChatPanel from "./ChatPanel";
import TracePanel from "./TracePanel";
import UserManagePage from "./UserManagePage";

export default function App() {
  return (
    <AuthProvider>
      <HashRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/*" element={<ProtectedRoute><AppShell /></ProtectedRoute>} />
        </Routes>
      </HashRouter>
    </AuthProvider>
  );
}

function AppShell() {
  const navigate = useNavigate();
  const [health, setHealth] = useState<Health | null>(null);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [traceId, setTraceId] = useState("");
  const { user, logout } = useAuth();

  useEffect(() => {
    function checkHealth() {
      getHealth().then(setHealth).catch(() => setHealth({ status: "DOWN", service: "backend-java" }));
    }
    checkHealth();
    const timer = setInterval(checkHealth, 5000);
    return () => clearInterval(timer);
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
    if (!apiError) return;
    const timer = window.setTimeout(() => setApiError(null), 5000);
    return () => window.clearTimeout(timer);
  }, [apiError]);

  const role = user?.role;

  const navItems = [
    { key: "overview", label: "总览", mark: "总", helper: "运行态" },
    { key: "chat", label: "RAG 问答", mark: "问", helper: "检索问答" },
    { key: "trace", label: "Agent 执行轨迹", mark: "迹", helper: "节点时间线" },
  ];

  // 知识库：仅管理员可见（内部管理员 + 客户管理员）
  if (role === "SUPER_ADMIN" || role === "PLATFORM_ADMIN" || role === "CUSTOMER_ADMIN") {
    navItems.push({ key: "knowledge", label: "知识库", mark: "库", helper: "空间与文档" });
  }

  // 用户管理：内部管理员（超管和平台管理员）可见
  if (role === "SUPER_ADMIN" || role === "PLATFORM_ADMIN") {
    navItems.push({ key: "admin/users", label: "用户管理", mark: "管", helper: "用户与客户" });
  }

  const currentPath = (window.location.hash.replace(/^#\/?/, "") || "overview").split("?")[0];
  const pageCopy: Record<string, { title: string; description: string }> = {
    overview: { title: "Agent Ops 控制台", description: "查看当前服务状态、知识库、会话和 Agent Trace 的真实运行数据。" },
    knowledge: { title: "知识库", description: "管理当前客户下的知识库空间、文档登记和入库状态。" },
    chat: { title: "RAG 问答", description: "基于选定知识库提问，答案、引用证据和 Trace 会写入数据库历史。" },
    trace: { title: "Agent 执行轨迹", description: "查看 RAG Agent 每个节点的输入输出摘要、耗时和元数据。" },
    "admin/users": { title: "用户与客户管理", description: "管理平台账号、客户资料和客户邀请码，明确区分平台用户与客户用户。" },
  };
  const currentPage = pageCopy[currentPath] || pageCopy.overview;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <button className="brand" type="button" onClick={() => navigate("/overview")}>
          <div className="brand-mark">A</div>
          <div>
            <strong>Agent Ops</strong>
            <span>RAG 运行控制台</span>
          </div>
        </button>
        <nav>
          {navItems.map((item) => (
            <button
              key={item.key}
              className={currentPath === item.key ? "nav-item active" : "nav-item"}
              onClick={() => navigate(`/${item.key}`)}
              type="button"
            >
              <span>{item.mark}</span>
              <strong>{item.label}</strong>
              <small>{item.helper}</small>
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="sidebar-user">
            <span>{user?.displayName ?? user?.username}</span>
            <button className="logout-btn" type="button" onClick={() => { logout(); navigate("/login"); }}>退出</button>
          </div>
        </div>
      </aside>

      <main>
        <header className="topbar">
          <div>
            <h1>{currentPage.title}</h1>
            <p>{currentPage.description}</p>
          </div>
          <div className="topbar-actions">
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

        <Routes>
          <Route path="/overview" element={<Overview health={health} />} />
          <Route path="/knowledge" element={<KnowledgeBasePanel />} />
          <Route path="/chat" element={<ChatPanel onTrace={setTraceId} goTrace={() => navigate("/trace")} role={user?.role} />} />
          <Route path="/trace" element={<TracePanel initialTraceId={traceId} />} />
          <Route path="/admin/users" element={<UserManagePage />} />
          <Route path="/" element={<Navigate to="/overview" replace />} />
          <Route path="*" element={<Navigate to="/overview" replace />} />
        </Routes>
      </main>
    </div>
  );
}
