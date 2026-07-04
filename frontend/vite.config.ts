import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const JAVA_BASE = "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: JAVA_BASE,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "node"
  }
});
