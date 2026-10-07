import { expect, test } from "@playwright/test";
import type { Menu, Permission } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-021/022: 대상별 실제 메뉴 접근 설정 저장과 재조회", async ({
  page,
}) => {
  const current = await login(page);
  const role = current.roles[0];
  if (!role) throw new Error("역할이 있는 관리자 fixture 필요");
  const permissions = await list<Permission>(
    page.request,
    "/api/menu-permissions",
    { roleCode: role },
  );
  const menus = await list<Menu>(
    page.request,
    "/api/menu-structure",
    {},
    false,
  );
  const permission = permissions.find(
    (row) =>
      row.access_allowed !== null &&
      menus.some((menu) => menu.menu_id === row.menu_id),
  );
  if (!permission) throw new Error("FIX-PERMISSIONS fixture 필요");
  const menu = menus.find((item) => item.menu_id === permission.menu_id)!;
  await clickMenu(page, current, "/admin/menu-permissions");
  await page.getByLabel("대상 역할").selectOption(role);
  await page.getByRole("button", { name: "조회", exact: true }).click();
  await page
    .getByRole("row")
    .filter({
      has: page.getByRole("cell", {
        name: menu.screen_id,
        exact: true,
      }),
    })
    .getByRole("button", { name: "상세 선택" })
    .click();
  await expect(page.getByLabel("접근 허용 여부")).toHaveValue(
    String(permission.access_allowed),
  );
  // Preserve the seeded decision, rather than grant/deny a new authority based on unresolved policy.
  const saved = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/menu-permissions") &&
      response.request().method() === "PUT",
  );
  await page.getByRole("button", { name: "저장", exact: true }).click();
  expect((await saved).ok()).toBeTruthy();
  const after = await list<Permission>(page.request, "/api/menu-permissions", {
    roleCode: role,
  });
  expect(
    after.find((item) => item.menu_id === permission.menu_id)?.access_allowed,
  ).toBe(permission.access_allowed);
});

test("AC-023/024: 실제 미허용 계정은 메뉴가 숨겨지고 직접 API도 403", async ({
  page,
}) => {
  test.skip(
    !process.env.E2E_DENIED_LOGIN_ID || !process.env.E2E_DENIED_PASSWORD,
    "FIX-PERMISSIONS 미허용 계정 환경변수 필요. 정책 충돌 fixture는 승인 전 대체하지 않음.",
  );
  const denied = await login(page, "E2E_DENIED");
  const path = "/admin/menu-permissions";
  expect(denied.allowedMenus.some((menu) => menu.url === path)).toBe(false);
  await expect(page.locator(`a[href="${path}"]`)).toHaveCount(0);
  await page.goto(path);
  await expect(
    page.getByRole("heading", { name: "권한 없음", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("table")).toHaveCount(0);
  const response = await page.request.get(
    "/api/menu-permissions?page=0&size=1",
  );
  expect(response.status()).toBe(403);
  const mutation = await page.request.put("/api/menu-permissions", {
    data: { permissions: [] },
  });
  expect(mutation.status()).toBe(403);
});
