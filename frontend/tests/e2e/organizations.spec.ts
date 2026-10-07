import { expect, test } from "@playwright/test";
import type { Organization } from "../../src/api/types";
import { clickMenu, list, login } from "./helpers";

test("AC-006/007: 실제 조직코드 조회, 관계 상세, OQ-001 쓰기 차단", async ({
  page,
}) => {
  const current = await login(page);
  const organizations = await list<Organization>(
    page.request,
    "/api/organizations",
  );
  const selected = organizations.find((row) => row.organization_code);
  expect(selected, "FIX-ORGANIZATION 필요").toBeDefined();
  if (!selected) throw new Error("조직 fixture 없음");
  await clickMenu(page, current, "/admin/organizations");
  await page
    .getByLabel("조직코드", { exact: true })
    .first()
    .fill(selected.organization_code ?? "");
  await page.getByRole("button", { name: "조회", exact: true }).click();
  await page
    .getByRole("row")
    .filter({
      has: page.getByRole("cell", {
        name: selected.organization_code ?? "",
        exact: true,
      }),
    })
    .getByRole("button", { name: "상세 선택" })
    .click();
  await expect(page.getByLabel("적용 시작일")).toHaveAttribute("readonly");
  await expect(
    page.getByRole("button", { name: "저장 · OQ-001 승인 필요" }),
  ).toBeDisabled();
  await expect(
    page.getByText(
      "OQ-001: 원천/로컬 쓰기 경계 승인 전 등록과 저장이 차단됩니다.",
    ),
  ).toBeVisible();
});
