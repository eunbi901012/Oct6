import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../src/api/client";
import type { CurrentUser, Menu } from "../../src/api/types";
import { App } from "../../src/app/App";
import { routes, screenContracts } from "../../src/app/navigation";
import { useSession } from "../../src/app/session";

vi.mock("../../src/app/session", () => ({ useSession: vi.fn() }));

const titles = [
  "사용자 관리",
  "조직 관리",
  "역할 관리",
  "사용자 역할 관리",
  "메뉴 권한 관리",
  "메뉴 구조 관리",
  "메뉴 정보 관리",
  "코드그룹 관리",
  "상세코드 관리",
];
const menus: Menu[] = routes.map((url, index) => ({
  menu_id: index + 1,
  menu_name: titles[index] ?? url,
  parent_menu_id: null,
  display_order: index,
  screen_id: screenContracts[url]!.screenId,
  url,
  icon: null,
  business_category: null,
  description: null,
  use_status: null,
  created_at: "",
  updated_at: "",
}));
const user: CurrentUser = {
  accountId: 17,
  loginId: "단위시험",
  roles: [],
  allowedMenus: menus,
};

beforeEach(() => {
  vi.mocked(useSession).mockReturnValue({
    user,
    loading: false,
    error: "",
    refresh: vi.fn().mockResolvedValue(undefined),
  });
  vi.spyOn(api, "list").mockResolvedValue([]);
});

describe("화면 목록과 공통 shell", () => {
  it.each(routes.map((path, index) => [path, titles[index] ?? path]))(
    "%s는 해당 제목과 실제 route active 메뉴를 렌더링한다",
    async (path, title) => {
      render(
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>,
      );
      expect(await screen.findByRole("heading", { name: title })).toBeVisible();
      const nav = screen.getByRole("navigation", { name: "관리 메뉴" });
      const active = nav.querySelector('[aria-current="page"]');
      expect(active).toHaveAttribute("href", path);
      expect(
        screen.getByText(screenContracts[path]!.description),
      ).toBeVisible();
      await waitFor(() =>
        expect(screen.queryByText("처리 중입니다…")).not.toBeInTheDocument(),
      );
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    },
  );

  it("작은 화면용 내비게이션은 열림 상태를 알리고 route 이동 후 닫힌다", async () => {
    render(
      <MemoryRouter initialEntries={["/admin/roles"]}>
        <App />
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole("button", { name: "관리 메뉴 열기" }));
    expect(
      screen.getByRole("button", { name: "관리 메뉴 닫기" }),
    ).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(screen.getByRole("link", { name: "메뉴 정보 관리" }));
    await screen.findByRole("heading", { name: "메뉴 정보 관리" });
    expect(
      screen.getByRole("button", { name: "관리 메뉴 열기" }),
    ).toHaveAttribute("aria-expanded", "false");
  });
});
