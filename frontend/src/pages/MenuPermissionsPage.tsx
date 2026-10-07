import { useCallback, useState } from "react";
import { menuPermissionsApi } from "../api/menu-permissions";
import { PageHeading } from "../components/PageHeading";
import { menuStructureApi } from "../api/menu-structure";
import type { Menu, Permission, Query } from "../api/types";
import { useSession } from "../app/session";
import {
  DataTable,
  Message,
  Panel,
  useAction,
  useList,
} from "../components/common";
import { InputField, ReferenceSelect } from "../components/fields";
import {
  organizationsReference,
  rolesReference,
  usersReference,
} from "./catalog";

interface MatrixRow {
  menu: Menu | null;
  menuId: number;
  permission: Permission | undefined;
}
export function MenuPermissionsPage() {
  const { refresh } = useSession();
  const [kind, setKind] = useState("role");
  const [target, setTarget] = useState("");
  const [context, setContext] = useState<{
    kind: string;
    target: string;
  } | null>(null);
  const targetQuery =
    context?.kind === "role"
      ? "roleCode"
      : context?.kind === "organization"
        ? "organizationId"
        : "userId";
  const query: Query = context ? { [targetQuery]: context.target } : {};
  const queryKey = JSON.stringify(query);
  const loader = useCallback(
    () => menuPermissionsApi.list(JSON.parse(queryKey) as Query),
    [queryKey],
  );
  const list = useList(loader, !!context);
  const menuLoader = useCallback(() => menuStructureApi.list(), []);
  const menus = useList(menuLoader, true, false);
  const [selected, setSelected] = useState<MatrixRow | null>(null);
  const [access, setAccess] = useState("");
  const action = useAction();
  const matrix: MatrixRow[] = menus.rows.length
    ? menus.rows.map((menu) => ({
        menu,
        menuId: menu.menu_id,
        permission: list.rows.find(
          (permission) => permission.menu_id === menu.menu_id,
        ),
      }))
    : list.rows.map((permission) => ({
        menu: null,
        menuId: permission.menu_id,
        permission,
      }));
  const trail = (row: MatrixRow) => {
    if (!row.menu) return `메뉴 식별자 ${row.menuId}`;
    const names: string[] = [];
    const seen = new Set<number>();
    let menu: Menu | undefined = row.menu;
    while (menu && !seen.has(menu.menu_id)) {
      seen.add(menu.menu_id);
      names.unshift(menu.menu_name ?? menu.screen_id);
      menu = menus.rows.find((item) => item.menu_id === menu?.parent_menu_id);
    }
    return names.join(" / ");
  };
  const select = (row: MatrixRow) => {
    if (action.busy || list.busy) return;
    setSelected(row);
    setAccess(
      row.permission?.access_allowed == null
        ? ""
        : String(row.permission.access_allowed),
    );
  };
  return (
    <>
      <PageHeading title="메뉴 권한 관리" />
      <Panel title="검색조건">
        <p>
          권한 충돌 합성은 서버 판정입니다. OQ-003·OQ-004 미확정 사례는 실패
          폐쇄합니다.
        </p>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (action.busy || list.busy) return;
            if (context?.kind === kind && context.target === target) {
              void list.reload().catch(() => undefined);
            } else {
              setSelected(null);
              setContext({ kind, target });
            }
          }}
        >
          <label>
            대상 종류
            <select
              value={kind}
              disabled={action.busy || list.busy}
              onChange={(event) => {
                if (action.busy || list.busy) return;
                setKind(event.target.value);
                setTarget("");
                setContext(null);
                setSelected(null);
              }}
            >
              <option value="role">역할</option>
              <option value="organization">조직</option>
              <option value="user">사용자</option>
            </select>
          </label>
          {kind === "role" ? (
            <InputField
              field={{
                key: "roleCode",
                label: "대상 역할",
                reference: rolesReference,
              }}
              value={target}
              onChange={setTarget}
              disabled={action.busy || list.busy}
            />
          ) : (
            <ReferenceSelect
              reference={
                kind === "organization"
                  ? organizationsReference
                  : usersReference
              }
              label={kind === "organization" ? "대상 조직" : "대상 사용자"}
              value={target}
              onChange={setTarget}
              disabled={action.busy || list.busy}
            />
          )}
          <button type="submit" disabled={!target || list.busy || action.busy}>
            조회
          </button>
        </form>
      </Panel>
      <Message error={list.error} busy={list.busy} />
      <Message error={menus.error} busy={menus.busy} />
      {menus.error && (
        <p>
          메뉴 계층 보조 조회 실패. 권한 행의 실제 메뉴 식별자만 표시합니다
          (OQ-UI-012).
        </p>
      )}
      {context && list.error?.status !== 403 && list.error?.status !== 401 && (
        <>
          <Panel title="대상별 계층 접근권한 표">
            <p>
              조회 대상: {context.kind} / {context.target}
            </p>
            <DataTable
              rows={matrix}
              loading={list.busy || menus.busy}
              failed={!!list.error}
              columns={[
                { label: "대상", value: () => context.target },
                { label: "대메뉴 / 중메뉴 / 화면", value: trail },
                { label: "화면ID", value: (row) => row.menu?.screen_id },
                {
                  label: "접근 허용 여부",
                  value: (row) => row.permission?.access_allowed,
                },
              ]}
              rowKey={(row) => row.menuId}
              onSelect={select}
              disabled={action.busy || list.busy || !!list.error}
              selectedKey={selected?.menuId}
            />
          </Panel>
          {selected && (
            <Panel title="선택 대상 / 메뉴 접근 설정">
              <p>
                {context.target} / {trail(selected)}
              </p>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void action.run(async () => {
                    const targetKey =
                      context.kind === "role"
                        ? "role_code"
                        : context.kind === "organization"
                          ? "organization_id"
                          : "account_id";
                    if (access !== "true" && access !== "false")
                      throw new Error("접근 허용 여부를 선택하세요.");
                    await menuPermissionsApi.save({
                      menu_id: selected.menuId,
                      [targetKey]:
                        context.kind === "role"
                          ? context.target
                          : Number(context.target),
                      access_allowed: access === "true",
                    });
                    await list.reload();
                    setSelected(null);
                    await refresh();
                  });
                }}
              >
                <fieldset
                  disabled={
                    action.busy || list.busy || action.error?.status === 403
                  }
                >
                  <label>
                    접근 허용 여부
                    <select
                      value={access}
                      onChange={(event) => setAccess(event.target.value)}
                    >
                      <option value="">미설정 · 선택 필요</option>
                      <option value="true">허용</option>
                      <option value="false">거부</option>
                    </select>
                  </label>
                  <button type="submit" disabled={access === ""}>
                    저장
                  </button>
                  <button type="button" onClick={() => select(selected)}>
                    취소
                  </button>
                </fieldset>
                <Message {...action} />
              </form>
            </Panel>
          )}
          {!selected && <Message {...action} />}
        </>
      )}
    </>
  );
}
