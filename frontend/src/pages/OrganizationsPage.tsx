import { useCallback, useState } from "react";
import { organizationsApi } from "../api/organizations";
import { PageHeading } from "../components/PageHeading";
import type { Organization } from "../api/types";
import { DataTable, Message, Panel, Tree, useList } from "../components/common";
import { InputField, toDraft } from "../components/fields";
import type { Field } from "../components/fields";
import { organizationsReference } from "./catalog";

const relationshipFields: Field[] = [
  { key: "organization_code", label: "조직코드" },
  {
    key: "parent_organization_id",
    label: "상위조직",
    reference: organizationsReference,
  },
  { key: "effective_start", label: "적용 시작일", type: "date" },
  { key: "effective_end", label: "적용 종료일", type: "date" },
];
export function OrganizationsPage() {
  const [code, setCode] = useState("");
  const [query, setQuery] = useState("");
  const loader = useCallback(
    () => organizationsApi.search({ organizationCode: query }),
    [query],
  );
  const list = useList(loader);
  const [selected, setSelected] = useState<Organization | null>(null);
  const [draft, setDraft] = useState<Record<string, string>>({});
  return (
    <>
      <PageHeading title="조직 관리" />
      <Panel title="검색조건">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            setSelected(null);
            if (query === code) void list.reload().catch(() => undefined);
            else setQuery(code);
          }}
        >
          <label>
            조직코드
            <input
              value={code}
              onChange={(event) => setCode(event.target.value)}
            />
          </label>
          <button type="submit" disabled={list.busy}>
            조회
          </button>
        </form>
      </Panel>
      <Message error={list.error} busy={list.busy} />
      {!list.error && !list.busy && (
        <>
          <Panel title="목록">
            <DataTable
              rows={list.rows}
              loading={list.busy}
              failed={!!list.error}
              columns={[
                { label: "조직코드", value: (row) => row.organization_code },
                { label: "조직정보", value: (row) => row.organization_name },
                { label: "조직구분", value: (row) => row.organization_type },
                {
                  label: "상위조직",
                  value: (row) => row.parent_organization_id,
                },
              ]}
              rowKey={(row) => row.organization_id}
              onSelect={(row) => {
                setSelected(row);
                setDraft(toDraft(relationshipFields, row));
              }}
            />
          </Panel>
          <Panel title="선택 조직 계층">
            <Tree
              rows={list.rows}
              idKey="organization_id"
              parentKey="parent_organization_id"
              labelKey="organization_name"
            />
            {selected && (
              <p>
                선택 조직: {selected.organization_name} (
                {selected.organization_code})
              </p>
            )}
          </Panel>
          <Panel title="원천 조직·계층·기간 · 읽기 전용">
            <p role="status">
              OQ-001: 원천/로컬 쓰기 경계 승인 전 등록과 저장이 차단됩니다.
            </p>
            <button
              type="button"
              onClick={() => {
                setSelected(null);
                setDraft({});
              }}
            >
              등록 폼
            </button>
            <form onSubmit={(event) => event.preventDefault()}>
              <div className="form-grid">
                {relationshipFields.map((field) => (
                  <InputField
                    key={field.key}
                    field={field}
                    value={draft[field.key] ?? ""}
                    onChange={() => undefined}
                    readOnly
                  />
                ))}
              </div>
              <button type="submit" disabled>
                저장 · OQ-001 승인 필요
              </button>
              <button
                type="button"
                onClick={() => setDraft(toDraft(relationshipFields, selected))}
              >
                취소
              </button>
            </form>
            <p>
              관계 변경 이력의 조회 위치는 OQ-UI-002 확인 필요. 승인되지 않은
              쓰기 API를 호출하지 않습니다.
            </p>
          </Panel>
        </>
      )}
    </>
  );
}
