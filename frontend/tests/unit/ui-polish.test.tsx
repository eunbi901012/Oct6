import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { isAllowed } from "../../src/app/navigation";
import { DataTable, Message } from "../../src/components/common";
import type { CurrentUser, Menu } from "../../src/api/types";

const menu: Menu = {
  menu_id: 1,
  parent_menu_id: null,
  display_order: 1,
  menu_name: "역할 관리",
  screen_id: "SCR-ROLES",
  url: "/admin/users",
  icon: null,
  business_category: null,
  description: null,
  use_status: null,
  created_at: "",
  updated_at: "",
};

describe("UI 상태와 화면 식별", () => {
  it("URL만 일치하고 screenId가 다른 메뉴는 허용하지 않는다", () => {
    const user: CurrentUser = {
      accountId: 1,
      loginId: "계정",
      roles: ["R09"],
      allowedMenus: [menu],
    };
    expect(isAllowed(user, "/admin/users")).toBe(false);
    expect(
      isAllowed(
        { ...user, allowedMenus: [{ ...menu, url: "/admin/roles" }] },
        "/admin/roles",
      ),
    ).toBe(true);
  });

  it("로딩은 문구와 skeleton을 함께 제공한다", () => {
    render(
      <MemoryRouter>
        <Message busy />
      </MemoryRouter>,
    );
    expect(screen.getByRole("status")).toHaveTextContent("처리 중");
    expect(screen.getByTestId("loading-skeleton")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
  });

  it("빈 목록에 다음 행동을 안내한다", () => {
    render(
      <DataTable
        rows={[]}
        columns={[{ label: "명칭", value: (row: { id: number }) => row.id }]}
        rowKey={(row) => row.id}
        onSelect={vi.fn()}
      />,
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "조회 결과가 없습니다.",
    );
    expect(
      screen.getByText(
        "조회 조건을 확인하거나 등록 가능한 화면에서 새 항목을 등록하세요.",
      ),
    ).toBeInTheDocument();
  });

  it.each(["loading", "failed"] as const)(
    "%s 목록은 빈 결과로 오인시키지 않는다",
    (state) => {
      render(
        <DataTable
          rows={[]}
          columns={[{ label: "명칭", value: (row: { id: number }) => row.id }]}
          rowKey={(row) => row.id}
          onSelect={vi.fn()}
          loading={state === "loading"}
          failed={state === "failed"}
        />,
      );
      expect(
        screen.queryByText("조회 결과가 없습니다."),
      ).not.toBeInTheDocument();
      expect(
        screen.getByText(
          state === "loading"
            ? "목록을 불러오는 중입니다."
            : "목록 조회에 실패했습니다. 오류를 확인하고 다시 조회하세요.",
        ),
      ).toBeVisible();
    },
  );

  it("저장 중에는 목록 상세 선택을 막는다", () => {
    const select = vi.fn();
    render(
      <DataTable
        rows={[{ id: 1 }]}
        columns={[{ label: "명칭", value: (row) => row.id }]}
        rowKey={(row) => row.id}
        onSelect={select}
        disabled
      />,
    );
    const button = screen.getByRole("button", { name: "상세 선택" });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(select).not.toHaveBeenCalled();
  });
});
