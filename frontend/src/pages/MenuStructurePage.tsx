import { useCallback, useState } from "react";
import { menuStructureApi } from "../api/menu-structure";
import { PageHeading } from "../components/PageHeading";
import type { Menu } from "../api/types";
import { useSession } from "../app/session";
import {
  DataTable,
  Message,
  Panel,
  Tree,
  useAction,
  useList,
} from "../components/common";

export function MenuStructurePage() {
  const { refresh } = useSession();
  const loader = useCallback(() => menuStructureApi.list(), []);
  const list = useList(loader);
  const [selected, setSelected] = useState<Menu | null>(null);
  const [parent, setParent] = useState("");
  const [orders, setOrders] = useState<Record<string, string>>({});
  const [selectedMenus, setSelectedMenus] = useState<Menu[]>([]);
  const action = useAction();
  const applySelection = (menu: Menu, rows: Menu[]) => {
    setSelected(menu);
    setSelectedMenus(rows);
    setParent(menu.parent_menu_id == null ? "" : String(menu.parent_menu_id));
    setOrders(
      Object.fromEntries(
        rows
          .filter((row) => row.parent_menu_id === menu.parent_menu_id)
          .map((row) => [
            String(row.menu_id),
            row.display_order == null ? "" : String(row.display_order),
          ]),
      ),
    );
  };
  const select = (menu: Menu, rows = list.rows) => {
    if (action.busy || list.busy) return;
    applySelection(menu, rows);
  };
  const refetch = async () => {
    const rows = await list.reload();
    if (!rows) throw new Error("목록 재조회 결과를 확인할 수 없습니다.");
    const updated = rows.find((row) => row.menu_id === selected?.menu_id);
    if (updated) applySelection(updated, rows);
    else setSelected(null);
    await refresh();
  };
  const siblings = selected
    ? selectedMenus.filter(
        (row) => row.parent_menu_id === selected.parent_menu_id,
      )
    : [];
  return (
    <>
      <PageHeading title="메뉴 구조 관리" />
      <Panel title="검색조건">
        <p>추가 검색 필드는 OQ-UI-001 확인 필요.</p>
        <button
          type="button"
          disabled={list.busy || action.busy}
          onClick={() => {
            if (action.busy || list.busy) return;
            void list.reload().catch(() => undefined);
          }}
        >
          조회
        </button>
      </Panel>
      <Message error={list.error} busy={list.busy} />
      {list.error?.status !== 403 && list.error?.status !== 401 && (
        <>
          <Panel title="대메뉴 / 중메뉴 / 소메뉴 트리">
            <Tree
              rows={list.rows}
              idKey="menu_id"
              parentKey="parent_menu_id"
              labelKey="menu_name"
            />
          </Panel>
          <Panel title="계층 목록">
            <DataTable
              rows={list.rows}
              loading={list.busy}
              failed={!!list.error}
              columns={[
                { label: "메뉴", value: (row) => row.menu_name },
                {
                  label: "부모메뉴",
                  value: (row) =>
                    list.rows.find(
                      (menu) => menu.menu_id === row.parent_menu_id,
                    )?.menu_name,
                },
                { label: "표시순서", value: (row) => row.display_order },
              ]}
              rowKey={(row) => row.menu_id}
              onSelect={select}
              disabled={action.busy || list.busy || !!list.error}
              selectedKey={selected?.menu_id}
            />
          </Panel>
          {selected && (
            <Panel title="부모메뉴 / 동일 계층 표시순서">
              <p>선택 메뉴: {selected.menu_name}</p>
              <fieldset
                disabled={
                  action.busy || list.busy || action.error?.status === 403
                }
              >
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    void action.run(async () => {
                      if (!parent)
                        throw new Error("실제 부모메뉴를 선택하세요.");
                      await menuStructureApi.parent(
                        selected.menu_id,
                        Number(parent),
                      );
                      await refetch();
                    });
                  }}
                >
                  <label>
                    부모메뉴
                    <select
                      value={parent}
                      onChange={(event) => setParent(event.target.value)}
                    >
                      <option value="">최상위 / 부모 미선택</option>
                      {selectedMenus
                        .filter((menu) => menu.menu_id !== selected.menu_id)
                        .map((menu) => (
                          <option key={menu.menu_id} value={menu.menu_id}>
                            {menu.menu_name} ({menu.menu_id})
                          </option>
                        ))}
                    </select>
                  </label>
                  <button type="submit" disabled={!parent}>
                    부모 저장
                  </button>
                  <p>
                    부모 제거의 null 입력은 OpenAPI 미정이므로 전송하지 않습니다
                    (OQ-DATA-001).
                  </p>
                </form>
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    void action.run(async () => {
                      const items = siblings.map((menu) => {
                        const value = orders[String(menu.menu_id)] ?? "";
                        const order = Number(value);
                        if (value === "" || !Number.isSafeInteger(order))
                          throw new Error(
                            "형제 메뉴의 정수 순서를 입력하세요.",
                          );
                        return { menu_id: menu.menu_id, display_order: order };
                      });
                      await menuStructureApi.order(
                        selected.parent_menu_id,
                        items,
                      );
                      await refetch();
                    });
                  }}
                >
                  <h3>같은 부모의 전체 형제 메뉴</h3>
                  {siblings.map((menu) => (
                    <label key={menu.menu_id}>
                      {menu.menu_name} 표시순서
                      <input
                        type="number"
                        step="1"
                        value={orders[String(menu.menu_id)] ?? ""}
                        onChange={(event) =>
                          setOrders((current) => ({
                            ...current,
                            [menu.menu_id]: event.target.value,
                          }))
                        }
                      />
                    </label>
                  ))}
                  <button type="submit">순서 저장</button>
                </form>
                <button
                  type="button"
                  onClick={() => select(selected, selectedMenus)}
                >
                  취소
                </button>
              </fieldset>
            </Panel>
          )}
          <Message {...action} />
        </>
      )}
    </>
  );
}
