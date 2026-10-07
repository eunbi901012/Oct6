import { expect, test } from "@playwright/test";
import type { UserItem } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-001/002/003/005: 실제 사용자 조회·원천 읽기 전용·사용여부 저장과 재조회", async ({
  page,
}) => {
  const current = await login(page);
  const users = await list<UserItem>(page.request, "/api/users");
  const user = users.find(
    (row) =>
      row.account &&
      row.account.account_id !== current.accountId &&
      row.personnel?.employee_number &&
      row.account.use_status !== null,
  );
  expect(user, "별도의 FIX-PERSONNEL 계정 필요").toBeDefined();
  if (!user?.account || !user.personnel)
    throw new Error("선택할 사용자 fixture 없음");
  await clickMenu(page, current, "/admin/users");
  await page
    .getByLabel("교번", { exact: true })
    .fill(user.personnel.employee_number ?? "");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  const row = page.getByRole("row").filter({
    has: page.getByRole("cell", {
      name: user.personnel.employee_number ?? "",
      exact: true,
    }),
  });
  await row.getByRole("button", { name: "상세 선택" }).click();
  await expect(
    page.getByRole("heading", { name: "KORUS 원천정보 · 읽기 전용" }),
  ).toBeVisible();
  for (const name of ["보직", "퇴직일자", "최종 동기화일시"]) {
    await expect(
      page.getByRole("columnheader", { name, exact: true }),
    ).toBeVisible();
  }
  await expect(page.getByLabel("시스템 사용여부")).toHaveValue(
    user.account.use_status ?? "",
  );
  // A real idempotent save avoids inventing a use_status enum or altering authentication policy.
  const saved = page.waitForResponse(
    (response) =>
      response
        .url()
        .includes(`/api/users/${user.account?.account_id}/use-status`) &&
      response.request().method() === "PATCH",
  );
  await page
    .getByRole("button", { name: "사용여부 저장", exact: true })
    .click();
  expect((await saved).ok()).toBeTruthy();
  await expect(page.getByText("저장 후 재조회되었습니다.")).toBeVisible();
  const after = (await list<UserItem>(page.request, "/api/users")).find(
    (item) => item.account?.account_id === user.account?.account_id,
  );
  expect(after?.account?.use_status).toBe(user.account.use_status);
  expect(after?.personnel).toEqual(user.personnel);
  const rejected = await page.request.patch(
    `/api/users/${user.account.account_id}/use-status`,
    {
      data: {
        use_status: user.account.use_status,
        person_name: "원천 수정 거부 검증",
      },
    },
  );
  expect(rejected.status()).toBe(400);
  const unchanged = (await list<UserItem>(page.request, "/api/users")).find(
    (item) => item.account?.account_id === user.account?.account_id,
  );
  expect(unchanged?.personnel).toEqual(user.personnel);
});
