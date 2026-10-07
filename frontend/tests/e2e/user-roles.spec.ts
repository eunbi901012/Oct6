import { expect, test } from "@playwright/test";
import type { Role, UserItem, UserRole } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-015/017/019/020: 실제 대상의 승인자·기간을 명시하여 변경 저장과 재조회", async ({
  page,
}) => {
  const current = await login(page);
  const users = await list<UserItem>(page.request, "/api/users");
  const user = users.find(
    (row) =>
      row.account &&
      row.account.account_id !== current.accountId &&
      row.roles.length,
  );
  if (!user?.account)
    throw new Error("별도 FIX-ASSIGNMENTS 사용자 fixture 필요");
  const roles = await list<UserRole>(page.request, "/api/user-roles", {
    userId: user.account.account_id,
  });
  const role = roles[0];
  if (!role) throw new Error("현재 사용자 역할 fixture 필요");
  await clickMenu(page, current, "/admin/user-roles");
  await page
    .getByLabel("역할 조회 대상 사용자")
    .selectOption(String(user.account.account_id));
  await page.getByRole("button", { name: "조회", exact: true }).click();
  await page
    .getByRole("row")
    .filter({
      has: page.getByRole("cell", {
        name: role.role_code,
        exact: true,
      }),
    })
    .getByRole("button", { name: "상세 선택" })
    .first()
    .click();
  await expect(page.getByLabel("승인자", { exact: true })).toHaveValue(
    String(role.approver_id),
  );
  await expect(page.getByLabel("유효 시작일")).toHaveValue(
    role.valid_start ?? "",
  );
  await expect(page.getByLabel("보직/수동 구분")).toHaveValue(
    role.assignment_source ?? "",
  );
  const saved = page.waitForResponse(
    (response) =>
      response.url().includes(`/api/user-roles/${role.assignment_id}`) &&
      response.request().method() === "PATCH",
  );
  await page.getByRole("button", { name: "변경 저장", exact: true }).click();
  expect((await saved).ok()).toBeTruthy();
  const after = await list<UserRole>(page.request, "/api/user-roles", {
    userId: user.account.account_id,
  });
  const changed = after.find(
    (item) => item.assignment_id === role.assignment_id,
  );
  expect(changed?.approver_id).toBe(role.approver_id);
  expect(changed?.valid_start).toBe(role.valid_start);
  expect(changed?.valid_end).toBe(role.valid_end);
});

test("신규 부여 승인자는 현재 로그인 계정으로 자동 선택되지 않는다", async ({
  page,
}) => {
  const current = await login(page);
  const users = await list<UserItem>(page.request, "/api/users");
  const user = users.find((item) => item.account);
  if (!user?.account) throw new Error("사용자 fixture 필요");
  await clickMenu(page, current, "/admin/user-roles");
  await page
    .getByLabel("역할 조회 대상 사용자")
    .selectOption(String(user.account.account_id));
  await page.getByRole("button", { name: "조회", exact: true }).click();
  await expect(page.getByLabel("승인자", { exact: true })).toHaveValue("");
});

test("AC-016/018/019: 실제 사용자·역할·승인자 선택 후 부여와 JSON 회수 기록", async ({
  page,
}) => {
  const current = await login(page);
  const users = await list<UserItem>(page.request, "/api/users");
  const roles = await list<Role>(page.request, "/api/roles");
  const user = users.find(
    (item) => item.account && item.account.account_id !== current.accountId,
  );
  const approver = users.find(
    (item) => item.account && item.account.account_id !== current.accountId,
  );
  if (!user?.account || !approver?.account)
    throw new Error("별도의 FIX-ASSIGNMENTS 사용자·승인자 fixture 필요");
  const existing = await list<UserRole>(page.request, "/api/user-roles", {
    userId: user.account.account_id,
  });
  const selectedRole = roles.find(
    (item) =>
      !existing.some((assignment) => assignment.role_code === item.role_code),
  );
  const record = users
    .flatMap((item) => item.roles)
    .find((item) => item.assignment_source);
  if (!selectedRole || !record)
    throw new Error("미부여 역할과 승인된 부여 구분 fixture 필요");
  await clickMenu(page, current, "/admin/user-roles");
  await page
    .getByLabel("역할 조회 대상 사용자")
    .selectOption(String(user.account.account_id));
  await page.getByRole("button", { name: "조회", exact: true }).click();
  await expect(page.getByLabel("승인자", { exact: true })).toHaveValue("");
  await page
    .getByLabel("역할", { exact: true })
    .selectOption(selectedRole.role_code);
  await page
    .getByLabel("승인자", { exact: true })
    .selectOption(String(approver.account.account_id));
  await page.getByLabel("보직/수동 구분").fill(record.assignment_source ?? "");
  if (record.valid_start)
    await page.getByLabel("유효 시작일").fill(record.valid_start);
  if (record.valid_end)
    await page.getByLabel("유효 종료일").fill(record.valid_end);
  let assignmentId: number | undefined;
  try {
    const saved = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/user-roles") &&
        response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "부여 저장", exact: true }).click();
    expect((await saved).ok()).toBeTruthy();
    const granted = (
      await list<UserRole>(page.request, "/api/user-roles", {
        userId: user.account.account_id,
      })
    ).find((item) => item.role_code === selectedRole.role_code);
    expect(granted).toBeDefined();
    assignmentId = granted?.assignment_id;
    expect(granted?.approver_id).toBe(approver.account.account_id);
    await page
      .getByRole("row")
      .filter({
        has: page.getByRole("cell", {
          name: selectedRole.role_code,
          exact: true,
        }),
      })
      .getByRole("button", { name: "상세 선택" })
      .click();
    page.once("dialog", (dialog) => void dialog.accept());
    const revoked = page.waitForResponse(
      (response) =>
        response.url().includes(`/api/user-roles/${assignmentId}`) &&
        response.request().method() === "DELETE",
    );
    await page.getByRole("button", { name: "회수", exact: true }).click();
    const response = await revoked;
    expect(response.ok()).toBeTruthy();
    expect(response.request().postDataJSON()).toEqual({
      approver_id: approver.account.account_id,
      ...(record.valid_start ? { valid_start: record.valid_start } : {}),
      ...(record.valid_end ? { valid_end: record.valid_end } : {}),
    });
    const after = await list<UserRole>(page.request, "/api/user-roles", {
      userId: user.account.account_id,
    });
    expect(after.some((item) => item.assignment_id === assignmentId)).toBe(
      false,
    );
    assignmentId = undefined;
  } finally {
    if (assignmentId !== undefined) {
      const cleanup = await page.request.delete(
        `/api/user-roles/${assignmentId}`,
        {
          data: { approver_id: approver.account.account_id },
        },
      );
      expect(cleanup.ok()).toBeTruthy();
    }
  }
});
