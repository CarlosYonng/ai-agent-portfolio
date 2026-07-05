import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const projectEnv = loadEnv(mode, "../", "");
  const javaPort = projectEnv.JAVA_PORT ?? "8080";
  const javaBase = projectEnv.VITE_JAVA_BASE_URL ?? `http://127.0.0.1:${javaPort}`;

  return {
    plugins: [react()],
    server: {
      proxy: {
        "/api": {
          target: javaBase,
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: "node"
    }
  };
});
