import { expect, test } from "@playwright/test";
import type { CodeGroup, DetailCode } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-035/038/039/052: 실제 그룹 링크 문맥과 상세코드 JSON 저장·재조회", async ({
  page,
}) => {
  const current = await login(page);
  const groups = await list<CodeGroup>(page.request, "/api/code-groups");
  let group: CodeGroup | undefined;
  let code: DetailCode | undefined;
  for (const candidate of groups) {
    const codes = await list<DetailCode>(page.request, "/api/detail-codes", {
      groupId: candidate.group_id,
    });
    if (codes.length) {
      group = candidate;
      code = codes[0];
      break;
    }
  }
  if (!group || !code)
    throw new Error("FIX-CODES 그룹 및 상세코드 fixture 필요");
  const selectedGroup = group;
  const selectedCode = code;
  await clickMenu(page, current, "/admin/code-groups");
  await page
    .getByRole("row")
    .filter({
      has: page.getByRole("cell", {
        name: selectedGroup.group_id,
        exact: true,
      }),
    })
    .getByRole("button", { name: "상세 선택" })
    .click();
  await page.getByRole("link", { name: "상세코드 목록", exact: true }).click();
  expect(new URL(page.url()).searchParams.get("groupId")).toBe(
    selectedGroup.group_id,
  );
  const codeRow = page.getByRole("row").filter({
    has: page.getByRole("cell", {
      name: selectedCode.code_value as string,
      exact: true,
    }),
  });
  await codeRow.getByRole("button", { name: "상세 선택" }).click();
  const original = selectedCode.additional_attributes as Record<
    string,
    unknown
  > | null;
  const attributes = { ...(original ?? {}), e2eVerification: "저장 재조회" };
  try {
    await page
      .getByLabel("추가속성 · 연계 코드 매핑 JSON")
      .fill(JSON.stringify(attributes));
    const saved = page.waitForResponse(
      (response) =>
        response
          .url()
          .includes(`/api/detail-codes/${selectedCode.detail_code_id}`) &&
        response.request().method() === "PATCH",
    );
    await page.getByRole("button", { name: "저장", exact: true }).click();
    expect((await saved).ok()).toBeTruthy();
    const after = (
      await list<DetailCode>(page.request, "/api/detail-codes", {
        groupId: selectedGroup.group_id,
      })
    ).find((row) => row.detail_code_id === selectedCode.detail_code_id);
    expect(after?.additional_attributes).toEqual(attributes);
    expect(after?.code_group_id).toBe(selectedGroup.code_group_id);
    expect(after?.valid_start).toBe(selectedCode.valid_start);
    expect(after?.use_status).toBe(selectedCode.use_status);
  } finally {
    const restored = await page.request.patch(
      `/api/detail-codes/${selectedCode.detail_code_id}`,
      {
        data: { additional_attributes: original ?? {} },
      },
    );
    expect(restored.ok()).toBeTruthy();
  }
});
