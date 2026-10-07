import { useEffect, useState } from "react";
import type { Row } from "../api/types";
import { InputField, toBody, toDraft } from "./fields";
import { assignmentFields } from "../pages/catalog";

export function AssignmentEditor({
  initial,
  onChange,
  disabled,
}: {
  initial: Row[];
  onChange: (rows: Row[]) => void;
  disabled?: boolean;
}) {
  const [drafts, setDrafts] = useState(() =>
    initial.map((row) => toDraft(assignmentFields, row)),
  );
  const [parseError, setParseError] = useState("");
  useEffect(() => {
    setDrafts(initial.map((row) => toDraft(assignmentFields, row)));
    onChange(
      initial.map((row) =>
        toBody(assignmentFields, toDraft(assignmentFields, row), false),
      ),
    );
    setParseError("");
  }, [initial, onChange]);
  const update = (next: Array<Record<string, string>>) => {
    setDrafts(next);
    try {
      onChange(next.map((draft) => toBody(assignmentFields, draft, false)));
      setParseError("");
    } catch (cause) {
      onChange([]);
      setParseError(
        cause instanceof Error ? cause.message : "입력을 확인하세요.",
      );
    }
  };
  return (
    <fieldset disabled={disabled}>
      <legend>역할별 승인자·유효기간 (승인자 자동 대입 없음)</legend>
      {drafts.map((draft, index) => (
        <section className="assignment" key={index}>
          <h3>역할 {index + 1}</h3>
          <div className="form-grid">
            {assignmentFields.map((field) => (
              <InputField
                key={field.key}
                field={field}
                value={draft[field.key] ?? ""}
                onChange={(value) =>
                  update(
                    drafts.map((row, position) =>
                      position === index ? { ...row, [field.key]: value } : row,
                    ),
                  )
                }
              />
            ))}
          </div>
          <button
            type="button"
            onClick={() =>
              update(drafts.filter((_, position) => position !== index))
            }
          >
            초안 역할 제외
          </button>
        </section>
      ))}
      <button
        type="button"
        onClick={() => update([...drafts, toDraft(assignmentFields, null)])}
      >
        역할 추가
      </button>
      {parseError && <p role="alert">{parseError}</p>}
    </fieldset>
  );
}
