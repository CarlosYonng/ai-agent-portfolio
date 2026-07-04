import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { apiRequest, getErrorContext, getErrorMessage } from "./api";

type AuthResponse = {
  token: string;
  user: Parameters<ReturnType<typeof useAuth>["login"]>[1];
};

function formatAuthError(error: unknown, fallback: string) {
  const message = getErrorMessage(error, error instanceof Error ? error.message : fallback);
  const context = getErrorContext(error);
  return context ? `${message}（${context}）` : message;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await apiRequest<AuthResponse>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password })
      });
      login(data.token, data.user);
      navigate("/overview");
    } catch (err) {
      setError(formatAuthError(err, "登录失败"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-bg" />
      <div className="login-card">
        <div className="login-header">
          <div className="login-logo">
            <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
              <rect width="28" height="28" rx="8" fill="#2563eb"/>
              <path d="M8 20V10l6 5-6 5z" fill="white"/>
              <path d="M14 20V10l6 5-6 5z" fill="white" opacity="0.6"/>
            </svg>
          </div>
          <h1>Agent Ops</h1>
          <p className="login-subtitle">RAG 运行控制台</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          {error && <div className="login-error"><span className="login-error-icon">!</span>{error}</div>}

          <div className="login-field">
            <label htmlFor="username">用户名</label>
            <div className="login-input-wrap">
              <svg className="login-input-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
                <circle cx="8" cy="5" r="3" stroke="#94a3b8" strokeWidth="1.5"/>
                <path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round"/>
              </svg>
              <input id="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="输入用户名" required />
            </div>
          </div>

          <div className="login-field">
            <label htmlFor="password">密码</label>
            <div className="login-input-wrap">
              <svg className="login-input-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
                <rect x="2" y="7" width="12" height="8" rx="2" stroke="#94a3b8" strokeWidth="1.5"/>
                <path d="M5 7V4a3 3 0 016 0v3" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round"/>
              </svg>
              <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="输入密码" required />
            </div>
          </div>

          <button className="login-btn" type="submit" disabled={loading}>
            {loading ? (
              <span className="login-btn-loading">
                <svg className="login-spinner" width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <circle cx="8" cy="8" r="6" stroke="white" strokeWidth="2" strokeDasharray="28" strokeLinecap="round"/>
                </svg>
                登录中...
              </span>
            ) : "登录"}
          </button>
        </form>

        <div className="login-footer">
          <span>拿到客户邀请码？</span>
          <Link to="/register">加入客户空间</Link>
        </div>

        <div className="login-demo">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="6" stroke="#94a3b8" strokeWidth="1.2"/>
            <path d="M7 4v4M7 9.5v.5" stroke="#94a3b8" strokeWidth="1.2" strokeLinecap="round"/>
          </svg>
          <span>演示账号：demo / demo123</span>
        </div>
      </div>
    </div>
  );
}
