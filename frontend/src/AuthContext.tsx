import { createContext, useContext, useState, useEffect, type ReactNode } from "react";

type UserInfo = {
  userId: number;
  customerId: number | null;
  username: string;
  displayName: string;
  role: string;
};

type AuthContextType = {
  token: string | null;
  user: UserInfo | null;
  login: (token: string, user: UserInfo) => void;
  logout: () => void;
  isAuthenticated: boolean;
};

const AuthContext = createContext<AuthContextType | null>(null);

function decodeTokenPayload(token: string): UserInfo | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    // JWT 使用 URL-safe base64（- 代替 +，_ 代替 /），atob 只认标准 base64。
    // 先做字符替换 + 补全 padding，避免 displayName 含中文时触发 "Invalid character"。
    let base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = base64.length % 4;
    if (pad) base64 += "=".repeat(4 - pad);
    const binary = atob(base64);
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes));
    return {
      userId: parseInt(payload.sub, 10),
      customerId: payload.customerId,
      username: payload.username,
      displayName: payload.displayName,
      role: payload.role,
    };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem("token"));
  const [user, setUser] = useState<UserInfo | null>(() => {
    const stored = localStorage.getItem("token");
    return stored ? decodeTokenPayload(stored) : null;
  });

  function login(newToken: string, newUser: UserInfo) {
    localStorage.setItem("token", newToken);
    setToken(newToken);
    setUser(newUser);
  }

  function logout() {
    localStorage.removeItem("token");
    setToken(null);
    setUser(null);
  }

  useEffect(() => {
    if (token) {
      const decoded = decodeTokenPayload(token);
      if (decoded) {
        setUser(decoded);
      } else {
        logout();
      }
    }
  }, [token]);

  return (
    <AuthContext.Provider value={{ token, user, login, logout, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
