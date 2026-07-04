import { ReactNode, useMemo, useState } from "react";
import { formatStatusText } from "./utils";

export function MetricCard({
  label,
  value,
  detail,
  tone = "default",
}: {
  label: string;
  value: string;
  detail: string;
  tone?: "default" | "good" | "warn" | "danger" | "info";
}) {
  return (
    <div className={`metric ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </div>
  );
}

export function StatusBadge({ value }: { value?: string }) {
  const normalized = (value ?? "-").toLowerCase();
  const tone = normalized.includes("fail") || normalized.includes("down")
    ? "danger"
    : normalized.includes("pending")
      ? "warn"
      : normalized.includes("success") || normalized.includes("indexed") || normalized.includes("up")
        ? "good"
        : "default";
  return <span className={`badge ${tone}`}>{formatStatusText(value)}</span>;
}

/** 分页器组件 */
function Paginator({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  const pages: number[] = [];
  const start = Math.max(1, page - 2);
  const end = Math.min(totalPages, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);

  return (
    <div className="paginator">
      <button className="paginator-btn" disabled={page <= 1} onClick={() => onChange(page - 1)}>‹</button>
      {start > 1 && <span className="paginator-ellipsis">…</span>}
      {pages.map(p => (
        <button key={p} className={`paginator-btn ${p === page ? "paginator-active" : ""}`} onClick={() => onChange(p)}>
          {p}
        </button>
      ))}
      {end < totalPages && <span className="paginator-ellipsis">…</span>}
      <button className="paginator-btn" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>›</button>
      <span className="paginator-info">第 {page}/{totalPages} 页</span>
    </div>
  );
}

export function DataTable({
  headers,
  rows,
  pageSize = 10,
}: {
  headers: string[];
  rows: Array<Array<ReactNode>>;
  pageSize?: number;
}) {
  const [page, setPage] = useState(1);
  const empty = useMemo(() => rows.length === 0, [rows]);
  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  const safePage = Math.min(page, totalPages);
  const pageRows = rows.slice((safePage - 1) * pageSize, safePage * pageSize);

  // 如果页码超出范围则重置
  if (page !== safePage && safePage !== page) {
    setPage(safePage);
  }

  return (
    <div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr>
          </thead>
          <tbody>
            {empty ? (
              <tr><td colSpan={headers.length}>暂无数据</td></tr>
            ) : (
              pageRows.map((row, index) => (
                <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <Paginator page={safePage} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
