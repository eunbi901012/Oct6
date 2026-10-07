import { expect, test } from "@playwright/test";
import { routes } from "../../src/app/navigation";
import { clickMenu, login } from "./helpers";

test("AC-040-01: 인증 후 강제 이동 없이 허용된 9개 관리 메뉴를 선택한다", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const user = await login(page);
  for (const route of routes) {
    await page.goto("/login");
    await expect(
      page.getByText("인증되었습니다. 이용할 메뉴를 선택하세요."),
    ).toBeVisible();
    await clickMenu(page, user, route);
    await expect(page.locator("main h1")).not.toHaveText("등록되지 않은 화면");
    await expect(
      page.getByRole("heading", { name: "검색조건", exact: true }),
    ).toBeVisible();
  }
  expect(errors).toEqual([]);
});

test("미인증 직접 route와 API는 보호 데이터를 노출하지 않는다", async ({
  page,
}) => {
  await page.goto("/admin/users");
  await expect(
    page.getByRole("heading", { name: "로그인이 필요합니다" }),
  ).toBeVisible();
  await expect(page.getByRole("table")).toHaveCount(0);
  const response = await page.request.get("/api/users?page=0&size=1");
  expect(response.status()).toBe(401);
});

test("로그아웃 API는 실제 세션을 종료한다", async ({ page }) => {
  await login(page);
  const logout = await page.request.post("/api/auth/logout");
  expect(logout.ok()).toBeTruthy();
  expect((await page.request.get("/api/auth/me")).status()).toBe(401);
  await page.goto("/admin/roles");
  await expect(
    page.getByRole("heading", { name: "로그인이 필요합니다" }),
  ).toBeVisible();
});
