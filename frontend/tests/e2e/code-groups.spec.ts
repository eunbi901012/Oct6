import { expect, test } from "@playwright/test";
import type { CodeGroup } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-034: 그룹 업무 키와 내부 키를 보존하고 명칭 변경 후 재조회", async ({
  page,
}) => {
  const current = await login(page);
  const groups = await list<CodeGroup>(page.request, "/api/code-groups");
  const group = groups.find((item) => item.group_name !== null);
  if (!group) throw new Error("FIX-CODES 그룹 fixture 필요");
  await clickMenu(page, current, "/admin/code-groups");
  await page
    .getByRole("row")
    .filter({
      has: page.getByRole("cell", {
        name: group.group_id,
        exact: true,
      }),
    })
    .getByRole("button", { name: "상세 선택" })
    .click();
  const changed = `${group.group_name} 검증`;
  try {
    await page.getByLabel("명칭", { exact: true }).fill(changed);
    const saved = page.waitForResponse(
      (response) =>
        response
          .url()
          .includes(`/api/code-groups/${encodeURIComponent(group.group_id)}`) &&
        response.request().method() === "PATCH",
    );
    await page.getByRole("button", { name: "저장", exact: true }).click();
    expect((await saved).ok()).toBeTruthy();
    await expect(page.getByLabel("명칭", { exact: true })).toHaveValue(changed);
    const after = (
      await list<CodeGroup>(page.request, "/api/code-groups")
    ).find((item) => item.group_id === group.group_id);
    expect(after?.group_name).toBe(changed);
    expect(after?.code_group_id).toBe(group.code_group_id);
  } finally {
    const restored = await page.request.patch(
      `/api/code-groups/${encodeURIComponent(group.group_id)}`,
      {
        data: { group_name: group.group_name },
      },
    );
    expect(restored.ok()).toBeTruthy();
  }
});
