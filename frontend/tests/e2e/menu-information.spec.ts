import { expect, test } from "@playwright/test";
import type { Menu } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-030/031: 실제 실행정보 저장 후 승인된 저장 URL로만 이동", async ({
  page,
}) => {
  const current = await login(page);
  const menus = await list<Menu>(page.request, "/api/menu-information");
  const selected = menus.find(
    (row) =>
      row.description !== null &&
      row.url &&
      current.allowedMenus.some((menu) => menu.menu_id === row.menu_id),
  );
  if (!selected)
    throw new Error("설명과 허용 route가 있는 FIX-MENUS fixture 필요");
  await clickMenu(page, current, "/admin/menu-information");
  await page
    .getByRole("row")
    .filter({
      has: page.getByRole("cell", {
        name: selected.screen_id,
        exact: true,
      }),
    })
    .getByRole("button", { name: "상세 선택" })
    .click();
  const changed = `${selected.description} 검증`;
  try {
    await page.getByLabel("설명").fill(changed);
    const saved = page.waitForResponse(
      (response) =>
        response.url().includes(`/api/menu-information/${selected.menu_id}`) &&
        response.request().method() === "PATCH",
    );
    await page.getByRole("button", { name: "저장", exact: true }).click();
    expect((await saved).ok()).toBeTruthy();
    await expect(page.getByLabel("설명")).toHaveValue(changed);
    await expect(page).toHaveURL(/\/admin\/menu-information$/);
    await page.getByRole("link", { name: "저장된 실행 화면 링크" }).click();
    expect(new URL(page.url()).pathname).toBe(selected.url);
    const after = await list<Menu>(page.request, "/api/menu-information");
    const menu = after.find((item) => item.menu_id === selected.menu_id);
    expect(menu?.description).toBe(changed);
    expect(menu?.parent_menu_id).toBe(selected.parent_menu_id);
    expect(menu?.display_order).toBe(selected.display_order);
  } finally {
    const restored = await page.request.patch(
      `/api/menu-information/${selected.menu_id}`,
      {
        data: { description: selected.description },
      },
    );
    expect(restored.ok()).toBeTruthy();
  }
});
