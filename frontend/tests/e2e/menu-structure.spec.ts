import { expect, test } from "@playwright/test";
import type { Menu } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-026/027: 실제 메뉴 부모·형제 순서 저장과 재조회", async ({ page }) => {
  const current = await login(page);
  const menus = await list<Menu>(
    page.request,
    "/api/menu-structure",
    {},
    false,
  );
  const selected = menus.find(
    (row) => row.parent_menu_id !== null && row.display_order !== null,
  );
  if (!selected) throw new Error("부모 및 순서가 있는 FIX-MENUS fixture 필요");
  await clickMenu(page, current, "/admin/menu-structure");
  const row = page.getByRole("row").filter({
    has: page
      .getByRole("cell", {
        name: selected.menu_name ?? "",
        exact: true,
      })
      .first(),
  });
  await row.getByRole("button", { name: "상세 선택" }).click();
  const parentSaved = page.waitForResponse(
    (response) =>
      response
        .url()
        .includes(`/api/menu-structure/${selected.menu_id}/parent`) &&
      response.request().method() === "PATCH",
  );
  await page.getByRole("button", { name: "부모 저장", exact: true }).click();
  expect((await parentSaved).ok()).toBeTruthy();
  const afterParent = await list<Menu>(
    page.request,
    "/api/menu-structure",
    {},
    false,
  );
  expect(
    afterParent.find((menu) => menu.menu_id === selected.menu_id)
      ?.parent_menu_id,
  ).toBe(selected.parent_menu_id);
  await row.getByRole("button", { name: "상세 선택" }).click();
  const orderSaved = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/menu-structure/order") &&
      response.request().method() === "PUT",
  );
  await page.getByRole("button", { name: "순서 저장", exact: true }).click();
  expect((await orderSaved).ok()).toBeTruthy();
  const afterOrder = await list<Menu>(
    page.request,
    "/api/menu-structure",
    {},
    false,
  );
  const siblings = menus.filter(
    (menu) => menu.parent_menu_id === selected.parent_menu_id,
  );
  for (const sibling of siblings) {
    expect(
      afterOrder.find((menu) => menu.menu_id === sibling.menu_id)
        ?.display_order,
    ).toBe(sibling.display_order);
  }
});
