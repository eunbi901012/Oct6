import { expect, test } from "@playwright/test";
import type { Role } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-013-01 / AC-014-01: 실제 역할 명칭 저장·재조회와 역할코드 불변", async ({
  page,
}) => {
  const user = await login(page);
  const roles = await list<Role>(page.request, "/api/roles");
  const role = roles.find((row) => row.role_name !== null);
  expect(role, "FIX-ROLES 필요").toBeDefined();
  if (!role) throw new Error("역할 fixture 없음");
  await clickMenu(page, user, "/admin/roles");
  const row = page.getByRole("row").filter({
    has: page.getByRole("cell", { name: role.role_code, exact: true }),
  });
  await row.getByRole("button", { name: "상세 선택" }).click();
  await expect(page.getByLabel("역할코드")).toHaveAttribute("readonly");
  const original = role.role_name ?? "";
  const changed = `${original} 검증`;
  try {
    await page.getByLabel("역할명").fill(changed);
    const saved = page.waitForResponse(
      (response) =>
        response.url().includes(`/api/roles/${role.role_code}`) &&
        response.request().method() === "PATCH",
    );
    await page.getByRole("button", { name: "저장", exact: true }).click();
    expect((await saved).ok()).toBeTruthy();
    await expect(page.getByLabel("역할명")).toHaveValue(changed);
    await expect(page.getByLabel("역할코드")).toHaveValue(role.role_code);
    const result = (await list<Role>(page.request, "/api/roles")).find(
      (item) => item.role_code === role.role_code,
    );
    expect(result?.role_name).toBe(changed);
  } finally {
    const restored = await page.request.patch(`/api/roles/${role.role_code}`, {
      data: { role_name: original },
    });
    expect(restored.ok()).toBeTruthy();
  }
});
