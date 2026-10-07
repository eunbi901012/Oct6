import { useEffect, useState } from "react";
import { rolesApi } from "../api/roles";
import { menuInformationApi } from "../api/menu-information";
import { codeGroupsApi } from "../api/code-groups";
import { detailCodesApi } from "../api/detail-codes";
import type { Row } from "../api/types";
import { InputField, toBody, toDraft } from "./fields";
import type { Field } from "./fields";
import { Message, useAction } from "./common";

export function ResourceEditor({
  endpoint,
  idKey,
  fields,
  selected,
  onSaved,
  extraBody,
  onBusyChange,
}: {
  endpoint: string;
  idKey: string;
  fields: Field[];
  selected: Row | null;
  onSaved: (body?: Row) => Promise<void>;
  extraBody?: Row;
  onBusyChange?: (busy: boolean) => void;
}) {
  const [draft, setDraft] = useState(() => toDraft(fields, selected));
  const action = useAction();
  useEffect(() => {
    setDraft(toDraft(fields, selected));
    // Refreshed selector options must not reset an in-progress draft.
  }, [selected, endpoint, idKey]);
  useEffect(() => {
    onBusyChange?.(action.busy);
  }, [action.busy, onBusyChange]);
  useEffect(() => () => onBusyChange?.(false), [onBusyChange]);
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        void action.run(async () => {
          const initial = selected ? toDraft(fields, selected) : undefined;
          const body = {
            ...toBody(fields, draft, selected !== null, initial),
            ...extraBody,
          };
          const id = selected?.[idKey];
          if (endpoint === "roles") {
            await (selected
              ? rolesApi.update(String(id), body)
              : rolesApi.create(body));
          } else if (endpoint === "menu-information") {
            await (selected
              ? menuInformationApi.update(Number(id), body)
              : menuInformationApi.create(body));
          } else if (endpoint === "code-groups") {
            await (selected
              ? codeGroupsApi.update(String(id), body)
              : codeGroupsApi.create(body));
          } else if (endpoint === "detail-codes") {
            await (selected
              ? detailCodesApi.update(Number(id), body)
              : detailCodesApi.create(body));
          } else throw new Error("등록된 저장 API가 아닙니다.");
          await onSaved(body);
        });
      }}
    >
      <fieldset
        disabled={
          action.busy ||
          action.error?.status === 403 ||
          action.error?.status === 401
        }
      >
        <legend>{selected ? "선택 상세 수정" : "신규 등록"}</legend>
        <div className="form-grid">
          {fields.map((field) => (
            <InputField
              key={field.key}
              field={field}
              value={draft[field.key] ?? ""}
              onChange={(value) =>
                setDraft((current) => ({ ...current, [field.key]: value }))
              }
              readOnly={!!selected && field.immutable}
            />
          ))}
        </div>
        <div className="actions">
          <button type="submit">저장</button>
          <button
            type="button"
            onClick={() => setDraft(toDraft(fields, selected))}
          >
            취소
          </button>
        </div>
      </fieldset>
      <Message
        busy={action.busy}
        error={action.error}
        success={action.success}
      />
      <p className="hint">
        상태 값·기간·필드 세부 제약은 서버에서 검증합니다. 미확정 정책을 자동
        적용하지 않습니다.
      </p>
    </form>
  );
}
