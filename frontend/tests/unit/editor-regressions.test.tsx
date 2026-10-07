import { useState } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { api } from "../../src/api/client";
import type { Row } from "../../src/api/types";
import { useSession } from "../../src/app/session";
import { AssignmentEditor } from "../../src/components/AssignmentEditor";
import { ResourceEditor } from "../../src/components/ResourceEditor";
import { toBody } from "../../src/components/fields";
import { DetailCodesPage } from "../../src/pages/DetailCodesPage";
import { roleFields } from "../../src/pages/catalog";
import { codeGroupsApi } from "../../src/api/code-groups";
import { detailCodesApi } from "../../src/api/detail-codes";

vi.mock("../../src/app/session", () => ({ useSession: vi.fn() }));
afterEach(() => vi.restoreAllMocks());

const initialAssignments: Row[] = [{ role_code: "R01", approver_id: 31 }];
function AssignmentHarness({ save }: { save: (rows: Row[]) => void }) {
  const [rows, setRows] = useState<Row[]>([]);
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        if (rows.length) save(rows);
      }}
    >
      <AssignmentEditor initial={initialAssignments} onChange={setRows} />
      <button type="submit" disabled={!rows.length}>
        저장
      </button>
    </form>
  );
}

function HistoryControls() {
  const navigate = useNavigate();
  return (
    <>
      <button onClick={() => navigate("/admin/detail-codes?groupId=B")}>
        다른 그룹
      </button>
      <button onClick={() => navigate(-1)}>뒤로</button>
    </>
  );
}

describe("편집 문맥과 payload 회귀", () => {
  it("선택 역할의 일반 문자열 지우기만 보내고 불변·미변경 필드는 제외한다", async () => {
    const save = vi.spyOn(api, "mutate").mockResolvedValue(undefined);
    render(
      <ResourceEditor
        endpoint="roles"
        idKey="role_code"
        fields={roleFields}
        selected={{
          role_code: "R02",
          role_name: "기존 명칭",
          purpose: "기존 목적",
          default_data_scope: null,
        }}
        onSaved={vi.fn().mockResolvedValue(undefined)}
      />,
    );
    fireEvent.change(screen.getByLabelText("목적"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() =>
      expect(save).toHaveBeenCalledWith("PATCH", "/api/roles/R02", {
        purpose: "",
      }),
    );
  });

  it("취소한 문자열 지우기는 저장 body에 포함하지 않는다", async () => {
    const save = vi.spyOn(api, "mutate").mockResolvedValue(undefined);
    render(
      <ResourceEditor
        endpoint="roles"
        idKey="role_code"
        fields={roleFields}
        selected={{ role_code: "R02", purpose: "조회 목적" }}
        onSaved={vi.fn().mockResolvedValue(undefined)}
      />,
    );
    fireEvent.change(screen.getByLabelText("목적"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.getByLabelText("목적")).toHaveValue("조회 목적");
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() =>
      expect(save).toHaveBeenCalledWith("PATCH", "/api/roles/R02", {}),
    );
  });

  it("역할 변환 오류는 기존 유효 payload를 무효화하고 수정 후에만 저장한다", async () => {
    vi.spyOn(api, "list").mockImplementation(
      async <T,>(path: string): Promise<T[]> => {
        const rows: Row[] =
          path === "/api/roles"
            ? [{ role_code: "R01", role_name: "역할" }]
            : [
                {
                  account: { account_id: 1.5, login_id: "잘못된 식별자" },
                  personnel: null,
                },
                {
                  account: { account_id: 31, login_id: "승인자" },
                  personnel: null,
                },
              ];
        return rows as T[];
      },
    );
    const save = vi.fn();
    render(<AssignmentHarness save={save} />);
    await screen.findByRole("option", { name: "잘못된 식별자 (1.5)" });
    fireEvent.change(screen.getByLabelText("승인자"), {
      target: { value: "1.5" },
    });
    expect(screen.getByRole("alert")).toHaveTextContent("정수 식별자");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(save).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("승인자"), {
      target: { value: "31" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(save).toHaveBeenCalledWith(initialAssignments);
  });

  it("query 이동과 뒤로가기는 이전 그룹 선택과 편집 초안을 폐기한다", async () => {
    vi.mocked(useSession).mockReturnValue({
      user: null,
      loading: false,
      error: "",
      refresh: vi.fn().mockResolvedValue(undefined),
    });
    vi.spyOn(codeGroupsApi, "list").mockResolvedValue([
      { code_group_id: 1, group_id: "A", group_name: "그룹 A" },
      { code_group_id: 2, group_id: "B", group_name: "그룹 B" },
    ]);
    const list = vi
      .spyOn(detailCodesApi, "list")
      .mockImplementation(async (groupId) => [
        {
          detail_code_id: groupId === "A" ? 11 : 22,
          code_group_id: groupId === "A" ? 1 : 2,
          parent_detail_code_id: null,
          code_value: groupId,
          code_name: `코드 ${groupId}`,
        },
      ]);
    const save = vi.spyOn(api, "mutate").mockResolvedValue(undefined);
    render(
      <MemoryRouter initialEntries={["/admin/detail-codes?groupId=A"]}>
        <HistoryControls />
        <DetailCodesPage />
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole("button", { name: "상세 선택" }));
    fireEvent.change(screen.getByLabelText("코드명"), {
      target: { value: "미저장 A" },
    });
    fireEvent.click(screen.getByRole("button", { name: "다른 그룹" }));
    await screen.findByText("코드 B", { selector: "td" });
    expect(screen.getByLabelText("코드그룹")).toHaveValue("B");
    expect(screen.queryByLabelText("코드명")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "상세 선택" }));
    expect(screen.getByLabelText("코드명")).toHaveValue("코드 B");
    fireEvent.change(screen.getByLabelText("코드명"), {
      target: { value: "변경 B" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findByText("저장 후 재조회되었습니다.");
    expect(save).toHaveBeenCalledWith("PATCH", "/api/detail-codes/22", {
      code_name: "변경 B",
    });
    fireEvent.click(screen.getByRole("button", { name: "뒤로" }));
    await screen.findByText("코드 A", { selector: "td" });
    expect(screen.getByLabelText("코드그룹")).toHaveValue("A");
    expect(screen.queryByLabelText("코드명")).not.toBeInTheDocument();
    expect(list).toHaveBeenCalledWith("B");
  });

  it("빈 숫자·날짜·JSON의 null 의미를 만들거나 미변경 null 문자열을 전송하지 않는다", () => {
    const fields = [
      { key: "description", label: "설명" },
      { key: "parent_id", label: "상위", type: "number" as const },
      { key: "valid_end", label: "종료일", type: "date" as const },
      { key: "attributes", label: "속성", type: "json" as const },
    ];
    const initial = {
      description: "",
      parent_id: "12",
      valid_end: "2026-01-01",
      attributes: "{}",
    };
    const draft = {
      description: "",
      parent_id: "",
      valid_end: "",
      attributes: "",
    };
    expect(toBody(fields, draft, true, initial)).toEqual({});
  });
});
