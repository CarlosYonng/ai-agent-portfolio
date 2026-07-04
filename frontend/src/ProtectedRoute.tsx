import { Navigate } from "react-router-dom";
import type { ReactNode } from "react";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const hasToken = !!localStorage.getItem("token");
  if (!hasToken) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
