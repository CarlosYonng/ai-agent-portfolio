/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ops: {
          ink: "#0f172a",
          panel: "#ffffff",
          canvas: "#f8fafc",
          line: "#e2e8f0",
          primary: "#2563eb",
          muted: "#64748b"
        }
      },
      fontFamily: {
        sans: ["Inter", "PingFang SC", "Microsoft YaHei", "Arial", "sans-serif"],
        mono: ["JetBrains Mono", "ui-monospace", "SFMono-Regular", "Menlo", "monospace"]
      }
    }
  },
  plugins: []
};
