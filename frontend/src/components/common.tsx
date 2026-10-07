import { useCallback, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { ApiFailure, failure } from "../api/client";
import type { Row } from "../api/types";
import { Icon } from "./Icon";

export function text(value: unknown): string {
  if (value === null || value === undefined) return "미기록";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

export function Message({
  error,
  success,
  busy,
}: {
  error?: ApiFailure | null;
  success?: string;
  busy?: boolean;
}) {
  return (
    <div className="messages" aria-live="polite">
      {busy && (
        <div className="loading-state" role="status">
          <p>
            <span className="spinner" aria-hidden="true" />
            처리 중입니다…
          </p>
          <div
            className="skeleton"
            data-testid="loading-skeleton"
            aria-hidden="true"
          >
            <span />
            <span />
            <span />
          </div>
        </div>
      )}
      {error && (
        <div className="error-state" role="alert">
          <Icon
            name={
              error.status === 403 || error.status === 401 ? "lock" : "alert"
            }
          />
          <p>
            {error.status === 403 ? "권한 없음: " : ""}
            {error.message}
          </p>
          {error.status === 401 && <Link to="/login">로그인이 필요합니다</Link>}
          {error.fieldErrors.map((item, index) => (
            <p key={index}>
              {item.field}: {item.message}
            </p>
          ))}
        </div>
      )}
      {success && (
        <p className="success-state" role="status">
          <Icon name="check" />
          {success}
        </p>
      )}
    </div>
  );
}

export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiFailure | null>(null);
  const [success, setSuccess] = useState("");
  const pending = useRef(false);
  const run = async (work: () => Promise<void>) => {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError(null);
    setSuccess("");
    try {
      await work();
      setSuccess("저장 후 재조회되었습니다.");
    } catch (cause) {
      const problem = failure(cause);
      setError(problem);
      if (problem.status === 403)
        window.dispatchEvent(new Event("action-denied"));
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  return { busy, error, success, run };
}

export function useList<T>(
  loader: () => Promise<T[]>,
  enabled = true,
  protectedResource = true,
) {
  const [rows, setRows] = useState<T[]>([]);
  const [error, setError] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);
  const generation = useRef(0);
  const reload = useCallback(async () => {
    if (!enabled) {
      setRows([]);
      return;
    }
    const current = ++generation.current;
    setBusy(true);
    setError(null);
    try {
      const result = await loader();
      if (current === generation.current) setRows(result);
      return result;
    } catch (cause) {
      if (current === generation.current) {
        const problem = failure(cause);
        setRows([]);
        setError(problem);
        if (protectedResource && problem.status === 403)
          window.dispatchEvent(new Event("action-denied"));
      }
      throw cause;
    } finally {
      if (current === generation.current) setBusy(false);
    }
  }, [loader, enabled, protectedResource]);
  useEffect(() => {
    setRows([]);
    void reload().catch(() => undefined);
    return () => {
      generation.current += 1;
    };
  }, [reload]);
  return { rows, error, busy, reload };
}

export interface Column<T> {
  label: string;
  value: (row: T) => unknown;
}
export function DataTable<T>({
  rows,
  columns,
  onSelect,
  rowKey,
  disabled = false,
  selectedKey,
  loading = false,
  failed = false,
}: {
  rows: T[];
  columns: Column<T>[];
  onSelect: (row: T) => void;
  rowKey: (row: T) => string | number;
  disabled?: boolean;
  selectedKey?: string | number;
  loading?: boolean;
  failed?: boolean;
}) {
  if (loading && !rows.length)
    return (
      <p className="hint" role="status">
        목록을 불러오는 중입니다.
      </p>
    );
  if (failed)
    return (
      <p className="hint">
        목록 조회에 실패했습니다. 오류를 확인하고 다시 조회하세요.
      </p>
    );
  if (!rows.length)
    return (
      <div className="empty-state" role="status">
        <span className="icon-capsule">
          <Icon name="search" />
        </span>
        <h3>조회 결과가 없습니다.</h3>
        <p>조회 조건을 확인하거나 등록 가능한 화면에서 새 항목을 등록하세요.</p>
      </div>
    );
  return (
    <div className="table-scroll" aria-busy={loading}>
      <table>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.label} scope="col">
                {column.label}
              </th>
            ))}
            <th scope="col">선택</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              className={
                rowKey(row) === selectedKey ? "selected-row" : undefined
              }
            >
              {columns.map((column) => (
                <td key={column.label}>
                  {typeof column.value(row) === "boolean" ? (
                    <span
                      className={`badge ${column.value(row) ? "badge-success" : "badge-neutral"}`}
                    >
                      {column.value(row) ? "허용" : "거부"}
                    </span>
                  ) : (
                    text(column.value(row))
                  )}
                </td>
              ))}
              <td>
                <button
                  type="button"
                  disabled={disabled}
                  onClick={() => onSelect(row)}
                >
                  상세 선택
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function Panel({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="panel">
      <h2>{title}</h2>
      {children}
    </section>
  );
}

export function Tree({
  rows,
  idKey,
  parentKey,
  labelKey,
}: {
  rows: Row[];
  idKey: string;
  parentKey: string;
  labelKey: string;
}) {
  const renderNodes = (nodes: Row[], seen: Set<unknown>): ReactNode => (
    <ul>
      {nodes
        .filter((row) => !seen.has(row[idKey]))
        .map((row) => (
          <li key={text(row[idKey])}>
            {text(row[labelKey])}
            {renderNodes(
              rows.filter((child) => child[parentKey] === row[idKey]),
              new Set([...seen, row[idKey]]),
            )}
          </li>
        ))}
    </ul>
  );
  const roots = rows.filter(
    (row) =>
      row[parentKey] == null ||
      !rows.some((parent) => parent[idKey] === row[parentKey]),
  );
  return (
    <div aria-label="계층">
      {renderNodes(roots.length ? roots : rows, new Set())}
      <p className="hint">
        응답에 포함된 관계만 표시합니다. 계층 완전성은 OQ-UI-012 확인 필요.
      </p>
    </div>
  );
}
