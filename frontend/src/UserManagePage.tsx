import { useEffect, useMemo, useState } from "react";
import { apiRequest, getErrorContext, getErrorMessage } from "./api";
import { useAuth } from "./AuthContext";

type CustomerInfo = { id: number; name: string; contactName?: string; contactEmail?: string; inviteCode?: string; status: string };
type UserRecord = { id: number; customerId?: number; username: string; displayName: string; email: string; roleCode: string; status: string; createdAt: string };

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  return apiRequest<T>(url, init);
}

function formatAdminError(error: unknown, fallback: string) {
  const message = getErrorMessage(error, error instanceof Error ? error.message : fallback);
  const context = getErrorContext(error);
  return context ? `${message}（${context}）` : message;
}

function PasswordField({ value, onChange, placeholder, required }: { value: string; onChange: (v: string) => void; placeholder?: string; required?: boolean }) {
  const [show, setShow] = useState(false);
  return (
    <div className="password-wrap">
      <input type={show ? "text" : "password"} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} required={required} />
      <button type="button" className="password-toggle" onClick={() => setShow(!show)}>{show ? "🙈" : "👁"}</button>
    </div>
  );
}

const ROLE_OPTIONS: Record<string, { value: string; label: string }[]> = {
  SUPER_ADMIN: [
    { value: "SUPER_ADMIN", label: "超级管理员" },
    { value: "PLATFORM_ADMIN", label: "平台管理员" },
    { value: "CUSTOMER_ADMIN", label: "客户管理员" },
    { value: "USER", label: "客户用户" },
  ],
  PLATFORM_ADMIN: [
    { value: "CUSTOMER_ADMIN", label: "客户管理员" },
    { value: "USER", label: "客户用户" },
  ],
};

export default function UserManagePage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [customers, setCustomers] = useState<CustomerInfo[]>([]);
  const [message, setMessage] = useState("");
  const [tab, setTab] = useState<"users" | "customers">("users");

  // 新建用户表单
  const [formUsername, setFormUsername] = useState("");
  const [formPassword, setFormPassword] = useState("");
  const [formPassword2, setFormPassword2] = useState("");
  const [formDisplayName, setFormDisplayName] = useState("");
  const [formEmail, setFormEmail] = useState("");
  const [formRole, setFormRole] = useState("USER");
  const [formCustomerId, setFormCustomerId] = useState<number | "">("");
  const [userPage, setUserPage] = useState(1);
  const [custPage, setCustPage] = useState(1);
  const PAGE_SIZE = 10;

  // 客户表单
  const [custName, setCustName] = useState("");
  const [custContact, setCustContact] = useState("");
  const [editingCust, setEditingCust] = useState<CustomerInfo | null>(null);

  async function loadUsers() {
    const nextUsers = await fetchJson<UserRecord[]>("/api/admin/users");
    setUsers(nextUsers);
  }

  async function loadCustomers() {
    const nextCustomers = await fetchJson<CustomerInfo[]>("/api/admin/customers");
    setCustomers(nextCustomers);
  }

  useEffect(() => {
    loadUsers().catch(() => {});
    loadCustomers().catch(() => {});
  }, []);
  useEffect(() => {
    if ((formRole === "USER" || formRole === "CUSTOMER_ADMIN") && formCustomerId === "" && customers.length > 0) {
      setFormCustomerId(customers[0].id);
    }
    if (formRole !== "USER" && formRole !== "CUSTOMER_ADMIN" && formCustomerId !== "") {
      setFormCustomerId("");
    }
  }, [customers, formCustomerId, formRole]);

  const customerNameById = useMemo(() => {
    return new Map(customers.map((customer) => [customer.id, customer.name]));
  }, [customers]);

  async function createUser(e: React.FormEvent) {
    e.preventDefault();
    if (formPassword !== formPassword2) { setMessage("两次密码输入不一致"); return; }
    if ((formRole === "USER" || formRole === "CUSTOMER_ADMIN") && formCustomerId === "") { setMessage("客户角色必须选择所属客户"); return; }
    try {
      await fetchJson("/api/admin/users", {
        method: "POST",
        body: JSON.stringify({
          username: formUsername,
          password: formPassword,
          displayName: formDisplayName,
          email: formEmail || undefined,
          roleCode: formRole,
          customerId: formRole === "USER" || formRole === "CUSTOMER_ADMIN" ? formCustomerId : undefined,
        }),
      });
      setMessage("用户创建成功");
      setFormUsername(""); setFormPassword(""); setFormPassword2(""); setFormDisplayName(""); setFormEmail(""); setFormRole("USER");
      await loadUsers();
    } catch (err) { setMessage(formatAdminError(err, "创建失败")); }
  }

  async function toggleUser(id: number, enabled: boolean) {
    try { await fetchJson<void>(`/api/admin/users/${id}/status?enabled=${enabled}`, { method: "PATCH" }); await loadUsers(); }
    catch (err) { setMessage(formatAdminError(err, "操作失败")); }
  }

  async function saveCustomer(e: React.FormEvent) {
    e.preventDefault();
    try {
      if (editingCust) {
        await fetchJson(`/api/admin/customers/${editingCust.id}`, { method: "PUT", body: JSON.stringify({ name: custName, contactName: custContact || undefined }) });
        setMessage("客户更新成功");
        setEditingCust(null);
      } else {
        const customer = await fetchJson<CustomerInfo>("/api/admin/customers", { method: "POST", body: JSON.stringify({ name: custName, contactName: custContact || undefined }) });
        setMessage(`客户创建成功，邀请码：${customer.inviteCode ?? "-"}`);
      }
      setCustName(""); setCustContact("");
      await loadCustomers();
    } catch (err) { setMessage(formatAdminError(err, "操作失败")); }
  }

  function editCustomer(c: CustomerInfo) {
    setEditingCust(c);
    setCustName(c.name);
    setCustContact(c.contactName ?? "");
    setTab("customers");
  }

  async function deleteCustomer(id: number) {
    if (!window.confirm("确定删除此客户？相关的用户数据可能受影响。")) return;
    try {
      await fetchJson<void>(`/api/admin/customers/${id}`, { method: "DELETE" });
      await Promise.all([loadCustomers(), loadUsers()]);
      setMessage("客户已删除");
    }
    catch (err) { setMessage(formatAdminError(err, "删除失败")); }
  }

  async function copyInviteCode(code?: string) {
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code);
    } catch {
      const textarea = document.createElement("textarea");
      textarea.value = code;
      textarea.setAttribute("readonly", "true");
      textarea.style.position = "fixed";
      textarea.style.left = "-9999px";
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
    }
    setMessage(`邀请码已复制：${code}`);
  }

  return (
    <section className="content-grid workspace-grid">
      <div className="panel span-12">
        <div className="tabs">
          <button className={tab === "users" ? "tab active" : "tab"} onClick={() => setTab("users")}>用户管理</button>
          <button className={tab === "customers" ? "tab active" : "tab"} onClick={() => setTab("customers")}>客户管理</button>
        </div>

        {tab === "users" ? (
          <div className="tab-body content-grid">
            <form className="span-4" onSubmit={createUser}>
              <div className="panel-title"><h2>新建用户</h2><span>{message || "平台账号后台创建，客户用户可用邀请码自助注册"}</span></div>
              <label><span>用户名 <span className="text-red-500 font-bold">*</span></span><input value={formUsername} onChange={e => setFormUsername(e.target.value)} required /></label>
              <label><span>密码 <span className="text-red-500 font-bold">*</span></span><PasswordField value={formPassword} onChange={setFormPassword} required /></label>
              <label><span>确认密码 <span className="text-red-500 font-bold">*</span></span><PasswordField value={formPassword2} onChange={setFormPassword2} required /></label>
              <label><span>显示名称 <span className="text-red-500 font-bold">*</span></span><input value={formDisplayName} onChange={e => setFormDisplayName(e.target.value)} required /></label>
              <label><span>角色 <span className="text-red-500 font-bold">*</span></span>
                <select value={formRole} onChange={e => setFormRole(e.target.value)}>
                  {(ROLE_OPTIONS[currentUser?.role ?? ""] ?? ROLE_OPTIONS.SUPER_ADMIN).map(opt => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </label>
              <label><span>所属客户 {(formRole === "USER" || formRole === "CUSTOMER_ADMIN") && <span className="text-red-500 font-bold">*</span>}</span>
                <select value={formCustomerId} onChange={e => setFormCustomerId(e.target.value ? Number(e.target.value) : "")} disabled={formRole !== "USER" && formRole !== "CUSTOMER_ADMIN"}>
                  {(formRole === "USER" || formRole === "CUSTOMER_ADMIN") && customers.length === 0 && <option value="">请先创建客户</option>}
                  {formRole !== "USER" && formRole !== "CUSTOMER_ADMIN" && <option value="">平台账号不归属客户</option>}
                  {customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </label>
              <label>邮箱 <input value={formEmail} onChange={e => setFormEmail(e.target.value)} placeholder="选填" /></label>
              <button className="primary" type="submit">创建用户</button>
            </form>
            <div className="span-8">
              <div className="panel-title"><h2>用户列表</h2><span>{users.length} 个用户</span></div>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>ID</th><th>用户名</th><th>显示名</th><th>角色</th><th>客户</th><th>状态</th><th>操作</th></tr></thead>
                  <tbody>
                    {users.slice((userPage - 1) * PAGE_SIZE, userPage * PAGE_SIZE).map(u => (
                      <tr key={u.id}>
                        <td>{u.id}</td><td>{u.username}</td><td>{u.displayName}</td>
                        <td><span className={`role-tag role-${u.roleCode?.toLowerCase()}`}>{u.roleCode}</span></td>
                        <td>{u.customerId ? customerNameById.get(u.customerId) ?? u.customerId : "平台"}</td>
                        <td>{u.status === "ACTIVE" ? <span className="status-ok">正常</span> : <span className="status-off">禁用</span>}</td>
                        <td>
                          <button className="ghost compact" onClick={() => toggleUser(u.id, u.status !== "ACTIVE")}>
                            {u.status === "ACTIVE" ? "禁用" : "启用"}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {users.length > PAGE_SIZE && (
              <div className="paginator">
                <button className="paginator-btn" disabled={userPage <= 1} onClick={() => setUserPage(userPage - 1)}>‹</button>
                <span className="paginator-info">第 {userPage}/{Math.max(1, Math.ceil(users.length / PAGE_SIZE))} 页</span>
                <button className="paginator-btn" disabled={userPage * PAGE_SIZE >= users.length} onClick={() => setUserPage(userPage + 1)}>›</button>
              </div>
              )}
            </div>
          </div>
        ) : (
          <div className="tab-body content-grid">
            <form className="span-4" onSubmit={saveCustomer}>
              <div className="panel-title"><h2>{editingCust ? "编辑客户" : "新建客户"}</h2><span>{message || "创建客户后自动生成注册邀请码"}</span></div>
              <label><span>客户名称 <span className="text-red-500 font-bold">*</span></span>
                <input value={custName} onChange={e => setCustName(e.target.value)} required />
              </label>
              <label>联系人 <input value={custContact} onChange={e => setCustContact(e.target.value)} /></label>
              <div className="button-row">
                <button className="primary" type="submit">{editingCust ? "保存修改" : "创建客户"}</button>
                {editingCust && <button className="ghost" type="button" onClick={() => { setEditingCust(null); setCustName(""); setCustContact(""); }}>取消</button>}
              </div>
            </form>
            <div className="span-8">
              <div className="panel-title"><h2>客户列表</h2><span>{customers.length} 个客户</span></div>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>ID</th><th>名称</th><th>邀请码</th><th>联系人</th><th>状态</th><th>操作</th></tr></thead>
                  <tbody>
                    {customers.slice((custPage - 1) * PAGE_SIZE, custPage * PAGE_SIZE).map(c => (
                      <tr key={c.id}>
                        <td>{c.id}</td><td>{c.name}</td>
                        <td>
                          {c.inviteCode ? (
                            <button
                              className="invite-copy"
                              type="button"
                              title="复制邀请码"
                              aria-label={`复制邀请码 ${c.inviteCode}`}
                              onClick={() => copyInviteCode(c.inviteCode)}
                            >
                              <code>{c.inviteCode}</code>
                              <span>复制</span>
                            </button>
                          ) : "-"}
                        </td>
                        <td>{c.contactName ?? "-"}</td>
                        <td><span className={`role-tag ${c.status === "ACTIVE" ? "status-ok" : "status-off"}`}>{c.status}</span></td>
                        <td className="table-actions">
                          <button className="ghost compact" onClick={() => editCustomer(c)}>编辑</button>
                          <button className="danger compact" onClick={() => deleteCustomer(c.id)}>删除</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {customers.length > PAGE_SIZE && (
              <div className="paginator">
                <button className="paginator-btn" disabled={custPage <= 1} onClick={() => setCustPage(custPage - 1)}>‹</button>
                <span className="paginator-info">第 {custPage}/{Math.max(1, Math.ceil(customers.length / PAGE_SIZE))} 页</span>
                <button className="paginator-btn" disabled={custPage * PAGE_SIZE >= customers.length} onClick={() => setCustPage(custPage + 1)}>›</button>
              </div>
              )}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
