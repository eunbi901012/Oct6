import { expect } from "@playwright/test";
import type { APIRequestContext, Page } from "@playwright/test";
import type { CurrentUser, Envelope, Query } from "../../src/api/types";

export function credentials(prefix = "E2E") {
  const loginId = process.env[`${prefix}_LOGIN_ID`];
  const password = process.env[`${prefix}_PASSWORD`];
  if (!loginId || !password) {
    throw new Error(
      `${prefix}_LOGIN_ID 및 ${prefix}_PASSWORD 환경변수가 필요합니다.`,
    );
  }
  return { loginId, password };
}

export async function login(page: Page, prefix = "E2E"): Promise<CurrentUser> {
  const account = credentials(prefix);
  await page.goto("/login");
  await page.getByLabel("아이디", { exact: true }).fill(account.loginId);
  await page.getByLabel("비밀번호", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(
    page.getByText("인증되었습니다. 이용할 메뉴를 선택하세요."),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
  const response = await page.request.get("/api/auth/me");
  expect(response.ok()).toBeTruthy();
  return ((await response.json()) as Envelope<CurrentUser>).data;
}

export async function list<T>(
  request: APIRequestContext,
  path: string,
  query: Query = {},
  paginated = true,
) {
  const rows: T[] = [];
  let page = 0;
  do {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== "") params.set(key, String(value));
    });
    if (paginated) {
      params.set("page", String(page));
      params.set("size", "1");
    }
    const response = await request.get(
      `${path}${params.size ? `?${params}` : ""}`,
    );
    expect(response.ok(), `${path} 응답`).toBeTruthy();
    const result = (await response.json()) as Envelope<T[]>;
    expect(result.success).toBe(true);
    rows.push(...result.data);
    if (
      !paginated ||
      (page + 1) * (result.meta.size ?? 0) >= (result.meta.total ?? 0)
    )
      break;
    expect(result.data.length).toBeGreaterThan(0);
    page += 1;
  } while (true);
  return rows;
}

export async function clickMenu(page: Page, user: CurrentUser, path: string) {
  const menu = user.allowedMenus.find((item) => item.url === path);
  expect(menu, `${path} seed 권한`).toBeDefined();
  await page
    .getByRole("link", {
      name: menu?.menu_name ?? menu?.screen_id ?? "",
      exact: true,
    })
    .click();
  await expect(page).toHaveURL(new RegExp(`${path}$`));
}
