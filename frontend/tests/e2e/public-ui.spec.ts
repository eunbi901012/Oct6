import { expect, test } from "@playwright/test";
import { routes } from "../../src/app/navigation";

test("공개 로그인은 실제 API 연결 상태와 반응형 브랜드·폼을 표시한다", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const session = page.waitForResponse((response) =>
    response.url().endsWith("/api/auth/me"),
  );
  await page.goto("/login");
  expect((await session).status()).toBe(401);
  await expect(
    page.getByRole("heading", { name: "로그인", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("아이디", { exact: true })).toBeEditable();
  await expect(page.getByLabel("비밀번호", { exact: true })).toHaveAttribute(
    "type",
    "password",
  );
  await expect(page.locator(".login-brand-panel")).toBeVisible();
  await page.screenshot({
    path: test.info().outputPath("login-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("button", { name: "로그인", exact: true }),
  ).toBeVisible();
  const overflows = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(overflows).toBe(false);
  await page.screenshot({
    path: test.info().outputPath("login-mobile.png"),
    fullPage: true,
  });
  expect(errors).toEqual([]);
});

for (const route of routes) {
  test(`${route}: 비인증 직접 진입은 404 없이 로그인 안내와 실제 링크를 표시한다`, async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    const response = await page.goto(route);
    expect(response?.status()).toBe(200);
    await expect(
      page.getByRole("heading", { name: "로그인이 필요합니다" }),
    ).toBeVisible();
    await page.getByRole("link", { name: "로그인 화면", exact: true }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByLabel("아이디", { exact: true })).toBeVisible();
    expect(errors).toEqual([]);
  });
}
