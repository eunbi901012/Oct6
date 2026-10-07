import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { MenuChoices, isAllowed } from "../../src/app/navigation";
import type { CurrentUser, Menu } from "../../src/api/types";

const leaf = (url: string, name: string): Menu => ({
  menu_id: 23,
  parent_menu_id: null,
  display_order: 1,
  menu_name: name,
  screen_id: "SCR-ROLES",
  url,
  icon: null,
  business_category: null,
  description: null,
  use_status: null,
  created_at: "",
  updated_at: "",
});

describe("서버 결정 메뉴", () => {
  it("R09 자체로 권한을 우회하지 않는다", () => {
    const user: CurrentUser = {
      accountId: 7,
      loginId: "관리자",
      roles: ["R09"],
      allowedMenus: [],
    };
    expect(isAllowed(user, "/admin/roles")).toBe(false);
  });
  it("R01도 서버가 허용한 메뉴로 진입하며 다른 메뉴는 숨긴다", () => {
    const user: CurrentUser = {
      accountId: 8,
      loginId: "사용자",
      roles: ["R01"],
      allowedMenus: [leaf("/admin/roles", "역할 관리")],
    };
    render(
      <MemoryRouter>
        <MenuChoices user={user} />
      </MemoryRouter>,
    );
    expect(screen.getByRole("link", { name: "역할 관리" })).toHaveAttribute(
      "href",
      "/admin/roles",
    );
    expect(
      screen.queryByRole("link", { name: "사용자 관리" }),
    ).not.toBeInTheDocument();
    expect(isAllowed(user, "/admin/users")).toBe(false);
  });
  it("누락된 allowedMenus는 실패 폐쇄한다", () => {
    const user = {
      accountId: 8,
      loginId: "사용자",
      roles: ["R09"],
    } as CurrentUser;
    expect(isAllowed(user, "/admin/roles")).toBe(false);
  });
});
