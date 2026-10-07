import { useCallback, useId } from "react";
import { api } from "../api/client";
import type { Row } from "../api/types";
import { Message, text, useList } from "./common";

export interface Choice {
  value: string;
  label: string;
}
export interface Reference {
  endpoint: string;
  id: string;
  label: string;
  paginated?: boolean;
  query?: Record<string, string>;
}
export interface Field {
  key: string;
  label: string;
  type?: "number" | "date" | "textarea" | "json";
  immutable?: boolean;
  choices?: Choice[];
  reference?: Reference;
}

export function ReferenceSelect({
  reference,
  label,
  value,
  onChange,
  disabled,
}: {
  reference: Reference;
  label: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}) {
  const id = useId();
  const queryKey = JSON.stringify(reference.query ?? {});
  const loader = useCallback(
    () =>
      api.list<Row>(
        `/api/${reference.endpoint}`,
        JSON.parse(queryKey) as Record<string, string>,
        reference.paginated === true,
      ),
    [reference.endpoint, reference.paginated, queryKey],
  );
  const list = useList(loader, true, false);
  const options = list.rows
    .map((row) => {
      if (reference.endpoint === "users") {
        const account = row.account as Row | null;
        const personnel = row.personnel as Row | null;
        return account
          ? {
              value: text(account.account_id),
              label: text(personnel?.person_name ?? account.login_id),
            }
          : null;
      }
      return {
        value: text(row[reference.id]),
        label: text(row[reference.label]),
      };
    })
    .filter((item): item is Choice => item !== null);
  return (
    <div className="reference">
      <label htmlFor={id}>{label}</label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled || list.busy || !!list.error}
      >
        <option value="">선택하세요</option>
        {value && !options.some((option) => option.value === value) && (
          <option value={value}>기존 식별자 {value}</option>
        )}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label} ({option.value})
          </option>
        ))}
      </select>
      <Message error={list.error} busy={list.busy} />
      {list.error && (
        <button
          type="button"
          onClick={() => void list.reload().catch(() => undefined)}
        >
          선택 목록 재조회
        </button>
      )}
      {list.error?.status === 403 && (
        <p>선택 데이터 조회 권한이 필요합니다. OQ-UI-012 확인 필요.</p>
      )}
    </div>
  );
}

export function InputField({
  field,
  value,
  onChange,
  readOnly,
  disabled,
}: {
  field: Field;
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
  disabled?: boolean;
}) {
  const id = useId();
  if (field.reference && !readOnly) {
    return (
      <ReferenceSelect
        reference={field.reference}
        label={field.label}
        value={value}
        onChange={onChange}
        disabled={disabled}
      />
    );
  }
  return (
    <div className="field">
      <label htmlFor={id}>{field.label}</label>
      {field.choices && !readOnly ? (
        <select
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          disabled={disabled}
        >
          <option value="">선택하세요</option>
          {field.choices.map((choice) => (
            <option key={choice.value} value={choice.value}>
              {choice.label}
            </option>
          ))}
        </select>
      ) : field.type === "textarea" || field.type === "json" ? (
        <textarea
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          readOnly={readOnly}
          disabled={disabled}
          rows={4}
        />
      ) : (
        <input
          id={id}
          type={
            field.type === "number"
              ? "number"
              : field.type === "date"
                ? "date"
                : "text"
          }
          value={value}
          onChange={(event) => onChange(event.target.value)}
          readOnly={readOnly}
          disabled={disabled}
          step={field.type === "number" ? "1" : undefined}
        />
      )}
    </div>
  );
}

export function toDraft(
  fields: Field[],
  row: Row | null,
): Record<string, string> {
  return Object.fromEntries(
    fields.map((field) => {
      const value = row?.[field.key];
      return [
        field.key,
        value == null
          ? ""
          : typeof value === "object"
            ? JSON.stringify(value, null, 2)
            : String(value),
      ];
    }),
  );
}

export function toBody(
  fields: Field[],
  draft: Record<string, string>,
  editing: boolean,
  initial?: Record<string, string>,
): Row {
  const body: Row = {};
  fields.forEach((field) => {
    const value = draft[field.key] ?? "";
    if (editing && field.immutable) return;
    if (initial && value === (initial[field.key] ?? "")) return;
    if (value === "") {
      const plainString =
        !field.reference &&
        !field.choices &&
        (!field.type || field.type === "textarea");
      if (initial && plainString) body[field.key] = "";
      return;
    }
    if (field.type === "json") {
      const parsed: unknown = JSON.parse(value);
      if (
        parsed === null ||
        typeof parsed !== "object" ||
        Array.isArray(parsed)
      ) {
        throw new Error(`${field.label}: JSON 객체를 입력하세요.`);
      }
      body[field.key] = parsed;
    } else if (
      field.type === "number" ||
      (field.reference &&
        field.reference.id !== "role_code" &&
        field.reference.id !== "group_id")
    ) {
      const number = Number(value);
      if (!Number.isSafeInteger(number))
        throw new Error(`${field.label}: 정수 식별자를 확인하세요.`);
      body[field.key] = number;
    } else body[field.key] = value;
  });
  return body;
}
