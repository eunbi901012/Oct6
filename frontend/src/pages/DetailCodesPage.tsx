import { useCallback, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { detailCodesApi } from "../api/detail-codes";
import { PageHeading } from "../components/PageHeading";
import { codeGroupsApi } from "../api/code-groups";
import type { CodeGroup, DetailCode, Row } from "../api/types";
import { useSession } from "../app/session";
import { DataTable, Message, Panel, Tree, useList } from "../components/common";
import type { Field } from "../components/fields";
import { ResourceEditor } from "../components/ResourceEditor";

export function DetailCodesPage() {
  const [params, setParams] = useSearchParams();
  const context = params.get("groupId") ?? "";
  return (
    <DetailCodesContext
      key={context}
      context={context}
      onContextChange={(groupId) => setParams({ groupId })}
    />
  );
}

function DetailCodesContext({
  context,
  onContextChange,
}: {
  context: string;
  onContextChange: (groupId: string) => void;
}) {
  const { refresh } = useSession();
  const [groupId, setGroupId] = useState(context);
  const groupLoader = useCallback(() => codeGroupsApi.list(), []);
  const groups = useList(groupLoader, true, false);
  const loader = useCallback(() => detailCodesApi.list(context), [context]);
  const list = useList(loader, !!context);
  const group = groups.rows.find((item) => item.group_id === context);
  const [selected, setSelected] = useState<DetailCode | null>(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedNotice, setSavedNotice] = useState("");
  const fields = useMemo<Field[]>(
    () => [
      {
        key: "code_group_id",
        label: "그룹 내부 식별자",
        immutable: true,
        type: "number",
      },
      { key: "code_value", label: "코드값" },
      { key: "code_name", label: "코드명" },
      {
        key: "parent_detail_code_id",
        label: "상위코드",
        type: "number",
        choices: list.rows
          .filter((row) => row.detail_code_id !== selected?.detail_code_id)
          .map((row) => ({
            value: String(row.detail_code_id),
            label: `${row.code_name ?? row.code_value} (${row.detail_code_id})`,
          })),
      },
      { key: "display_order", label: "정렬순서", type: "number" },
      {
        key: "additional_attributes",
        label: "추가속성 · 연계 코드 매핑 JSON",
        type: "json",
      },
      { key: "use_status", label: "사용여부" },
      { key: "valid_start", label: "유효 시작일", type: "date" },
      { key: "valid_end", label: "유효 종료일", type: "date" },
    ],
    [list.rows, selected?.detail_code_id],
  );
  return (
    <>
      <PageHeading title="상세코드 관리" />
      <Panel title="검색조건">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (saving || list.busy) return;
            if (groupId === context) void list.reload().catch(() => undefined);
            else onContextChange(groupId);
          }}
        >
          <label>
            코드그룹
            <select
              value={groupId}
              onChange={(event) => setGroupId(event.target.value)}
              disabled={saving || list.busy || groups.busy || !!groups.error}
            >
              <option value="">그룹을 선택하세요</option>
              {groupId &&
                !groups.rows.some((row) => row.group_id === groupId) && (
                  <option value={groupId}>{groupId} · 전달된 그룹ID</option>
                )}
              {groups.rows.map((row) => (
                <option key={row.code_group_id} value={row.group_id}>
                  {row.group_name} ({row.group_id})
                </option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={!groupId || list.busy || saving}>
            조회
          </button>
        </form>
        <Message error={groups.error} busy={groups.busy} />
        {groups.error && (
          <p>
            그룹 내부 식별자를 확인할 수 없어 등록이 제한됩니다. OQ-UI-012 확인
            필요.
          </p>
        )}
        <p>추가 검색 필드는 OQ-UI-001 확인 필요.</p>
      </Panel>
      <Message error={list.error} busy={list.busy} />
      <Message success={savedNotice} />
      {context && list.error?.status !== 403 && list.error?.status !== 401 && (
        <>
          <Panel title="그룹별 상세코드 목록">
            <p>그룹ID: {context}</p>
            <DataTable
              rows={list.rows}
              loading={list.busy}
              failed={!!list.error}
              columns={fields
                .filter((field) => field.key !== "code_group_id")
                .map((field) => ({
                  label: field.label,
                  value: (row: DetailCode) => row[field.key],
                }))}
              rowKey={(row) => row.detail_code_id}
              disabled={saving || list.busy || !!list.error}
              selectedKey={selected?.detail_code_id}
              onSelect={(row) => {
                if (saving || list.busy || list.error) return;
                setSelected(row);
                setEditing(true);
              }}
            />
          </Panel>
          <Panel title="선택 그룹 코드 계층">
            <Tree
              rows={list.rows}
              idKey="detail_code_id"
              parentKey="parent_detail_code_id"
              labelKey="code_name"
            />
          </Panel>
          <Panel title="상세코드 등록 / 수정">
            <button
              type="button"
              disabled={!group || saving || list.busy}
              onClick={() => {
                if (saving || list.busy) return;
                setSelected(null);
                setEditing(true);
              }}
            >
              등록
            </button>
            {editing && (selected || group) && (
              <DetailEditor
                key={`${context}-${selected?.detail_code_id ?? "new"}`}
                fields={fields}
                selected={selected}
                group={group}
                onBusyChange={setSaving}
                onSaved={async () => {
                  const updated = await list.reload();
                  const row =
                    updated?.find(
                      (item) =>
                        item.detail_code_id === selected?.detail_code_id,
                    ) ?? null;
                  setSelected(row);
                  if (!row) setEditing(false);
                  await refresh();
                  setSavedNotice("저장 후 재조회되었습니다.");
                }}
              />
            )}
            <p>
              추가속성은 JSON 객체로만 저장하며 외부 기관 API를 호출하지
              않습니다.
            </p>
          </Panel>
        </>
      )}
    </>
  );
}

function DetailEditor({
  fields,
  selected,
  group,
  onSaved,
  onBusyChange,
}: {
  fields: Field[];
  selected: DetailCode | null;
  group: CodeGroup | undefined;
  onSaved: (body?: Row) => Promise<void>;
  onBusyChange: (busy: boolean) => void;
}) {
  const contextualFields = useMemo(
    () => fields.filter((field) => field.key !== "code_group_id"),
    [fields],
  );
  return (
    <>
      <p>그룹 내부 식별자: {selected?.code_group_id ?? group?.code_group_id}</p>
      <ResourceEditor
        endpoint="detail-codes"
        fields={contextualFields}
        idKey="detail_code_id"
        selected={selected}
        onSaved={onSaved}
        onBusyChange={onBusyChange}
        extraBody={
          selected ? undefined : { code_group_id: group?.code_group_id }
        }
      />
    </>
  );
}
