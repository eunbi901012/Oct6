import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { rolesApi } from "../api/roles";
import { PageHeading } from "../components/PageHeading";
import { menuInformationApi } from "../api/menu-information";
import { codeGroupsApi } from "../api/code-groups";
import type { Row } from "../api/types";
import { useSession } from "../app/session";
import { isAllowed, routes } from "../app/navigation";
import { DataTable, Message, Panel, useList } from "../components/common";
import { ResourceEditor } from "../components/ResourceEditor";
import type { Field } from "../components/fields";

export function CatalogPage({
  endpoint,
  title,
  fields,
  idKey,
}: {
  endpoint: string;
  title: string;
  fields: Field[];
  idKey: string;
}) {
  const { user, refresh } = useSession();
  const loader = useCallback<() => Promise<Row[]>>(() => {
    if (endpoint === "roles") return rolesApi.list();
    if (endpoint === "menu-information") return menuInformationApi.list();
    if (endpoint === "code-groups") return codeGroupsApi.list();
    return Promise.reject(new Error("등록된 목록 API가 아닙니다."));
  }, [endpoint]);
  const list = useList(loader);
  const [selected, setSelected] = useState<Row | null>(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedNotice, setSavedNotice] = useState("");
  const listFields =
    endpoint === "roles"
      ? fields.filter((field) =>
          ["role_code", "role_name", "purpose"].includes(field.key),
        )
      : fields;
  const columns = listFields.map((field) => ({
    label: field.label,
    value: (row: Row) => row[field.key],
  }));
  const savedURL = selected?.url;
  const screenId = selected?.screen_id;
  const matchingMenu = user?.allowedMenus?.find(
    (menu) => menu.url === savedURL && menu.screen_id === screenId,
  );
  return (
    <>
      <PageHeading title={title} />
      <Panel title="검색조건">
        <p>추가 검색 필드·기본 정렬·페이지 정책은 OQ-UI-001 확인 필요.</p>
        <button
          type="button"
          disabled={list.busy || saving}
          onClick={() => {
            if (saving || list.busy) return;
            void list.reload().catch(() => undefined);
          }}
        >
          조회
        </button>
      </Panel>
      <Message error={list.error} busy={list.busy} />
      <Message success={savedNotice} />
      {list.error?.status !== 403 && list.error?.status !== 401 && (
        <>
          <Panel title="목록">
            <DataTable
              rows={list.rows}
              loading={list.busy}
              failed={!!list.error}
              columns={columns}
              rowKey={(row) => String(row[idKey])}
              disabled={saving || list.busy || !!list.error}
              selectedKey={selected ? String(selected[idKey]) : undefined}
              onSelect={(row) => {
                if (saving || list.busy || list.error) return;
                setSelected(row);
                setEditing(true);
              }}
            />
          </Panel>
          <Panel title="상세 / 등록·수정">
            <button
              type="button"
              disabled={saving || list.busy}
              onClick={() => {
                if (saving || list.busy) return;
                setSelected(null);
                setEditing(true);
              }}
            >
              등록
            </button>
            {editing ? (
              <ResourceEditor
                endpoint={endpoint}
                fields={fields}
                idKey={idKey}
                selected={selected}
                onBusyChange={setSaving}
                onSaved={async (body) => {
                  const key = selected?.[idKey] ?? body?.[idKey];
                  const updated = await list.reload();
                  const row =
                    updated?.find(
                      (item) => item[idKey] === (body?.[idKey] ?? key),
                    ) ?? null;
                  setSelected(row);
                  if (!row) setEditing(false);
                  await refresh();
                  setSavedNotice("저장 후 재조회되었습니다.");
                }}
              />
            ) : (
              <p>목록에서 상세를 선택하거나 등록을 선택하세요.</p>
            )}
            {endpoint === "code-groups" &&
              selected &&
              (isAllowed(user, "/admin/detail-codes") ? (
                <Link
                  to={`/admin/detail-codes?groupId=${encodeURIComponent(String(selected.group_id))}`}
                >
                  상세코드 목록
                </Link>
              ) : (
                <p>상세코드 목록 접근 권한이 없습니다.</p>
              ))}
            {endpoint === "menu-information" &&
              selected &&
              (typeof savedURL === "string" &&
              routes.some((route) => route === savedURL) &&
              matchingMenu &&
              isAllowed(user, savedURL) ? (
                <Link to={savedURL}>저장된 실행 화면 링크</Link>
              ) : (
                <p>
                  저장 링크의 화면ID·URL 또는 접근 권한 확인 필요 (OQ-UI-013).
                </p>
              ))}
          </Panel>
        </>
      )}
    </>
  );
}
