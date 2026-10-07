import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";
import { menuStructureApi } from "../../src/api/menu-structure";
import type { Menu } from "../../src/api/types";
import { useSession } from "../../src/app/session";
import { MenuStructurePage } from "../../src/pages/MenuStructurePage";

vi.mock("../../src/app/session", () => ({ useSession: vi.fn() }));

it("부모 저장 후 같은 메뉴를 선택한 상태로 갱신된 관계를 표시한다", async () => {
  const menus: Menu[] = [17, 29].map((menu_id) => ({
    menu_id,
    menu_name: `시험 메뉴 ${menu_id}`,
    parent_menu_id: null,
    display_order: menu_id,
    screen_id: `test-${menu_id}`,
    url: null,
    icon: null,
    business_category: null,
    description: null,
    use_status: null,
    created_at: "",
    updated_at: "",
  }));
  vi.mocked(useSession).mockReturnValue({
    user: null,
    loading: false,
    error: "",
    refresh: vi.fn().mockResolvedValue(undefined),
  });
  vi.spyOn(menuStructureApi, "list")
    .mockResolvedValueOnce(menus)
    .mockResolvedValueOnce([{ ...menus[0]!, parent_menu_id: 29 }, menus[1]!]);
  const save = vi
    .spyOn(menuStructureApi, "parent")
    .mockResolvedValue(undefined);
  render(
    <MemoryRouter initialEntries={["/admin/menu-structure"]}>
      <MenuStructurePage />
    </MemoryRouter>,
  );
  fireEvent.click(
    (await screen.findAllByRole("button", { name: "상세 선택" }))[0]!,
  );
  fireEvent.change(screen.getByLabelText("부모메뉴"), {
    target: { value: "29" },
  });
  fireEvent.click(screen.getByRole("button", { name: "부모 저장" }));
  await screen.findByText("저장 후 재조회되었습니다.");
  expect(save).toHaveBeenCalledWith(17, 29);
  await waitFor(() =>
    expect(screen.getByLabelText("부모메뉴")).toHaveValue("29"),
  );
  expect(screen.getByText("선택 메뉴: 시험 메뉴 17")).toBeVisible();
});
