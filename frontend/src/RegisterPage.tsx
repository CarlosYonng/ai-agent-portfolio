import { useState } from "react";
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

export default function RegisterPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await apiRequest<AuthResponse>("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({
          username,
          password,
          displayName,
          email: email || undefined,
          inviteCode,
        })
      });
      login(data.token, data.user);
      navigate("/overview");
    } catch (err) {
      setError(formatAuthError(err, "注册失败"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          <div className="login-logo">A</div>
          <h1>加入客户空间</h1>
          <p>使用客户邀请码创建外部用户账号</p>
        </div>
        <form onSubmit={handleSubmit}>
          {error && <div className="login-error">{error}</div>}
          <label>
            客户邀请码 <span className="text-red-500 font-bold">*</span>
            <input value={inviteCode} onChange={(e) => setInviteCode(e.target.value.toUpperCase())} placeholder="例如 CUST-ABCDEFGH" required />
          </label>
          <label>
            用户名 <span className="text-red-500 font-bold">*</span>
            <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="至少 2 个字符" required minLength={2} />
          </label>
          <label>
            显示名称 <span className="text-red-500 font-bold">*</span>
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="页面展示名称" required />
          </label>
          <label>
            密码 <span className="text-red-500 font-bold">*</span>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="至少 6 个字符" required minLength={6} />
          </label>
          <label>
            邮箱
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="选填" />
          </label>
          <button className="primary" type="submit" disabled={loading}>
            {loading ? "注册中..." : "注册"}
          </button>
        </form>
        <p className="login-footer">
          平台账号由超级管理员创建。已有账号？<Link to="/login">登录</Link>
        </p>
      </div>
    </div>
  );
}
