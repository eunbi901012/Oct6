import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { UsersPage } from "../../src/pages/UsersPage";
import { useSession } from "../../src/app/session";
import { usersApi } from "../../src/api/users";
import { ApiFailure, api } from "../../src/api/client";
import type { UserItem } from "../../src/api/types";

vi.mock("../../src/app/session", () => ({ useSession: vi.fn() }));
afterEach(() => vi.restoreAllMocks());

const user: UserItem = {
  account: {
    account_id: 23,
    login_id: "테스트 계정",
    use_status: "fixture-status",
  },
  personnel: {
    employee_number: "테스트 교번",
    person_name: "테스트 성명",
    organization_id: 81,
    job_grade: null,
    employment_status: null,
    retirement_date: null,
    last_synced_at: null,
  },
  positions: [],
  roles: [
    {
      assignment_id: 17,
      account_id: 23,
      role_code: "R01",
      approver_id: 31,
      valid_start: null,
      valid_end: null,
      assignment_source: null,
      assignment_status: null,
    },
  ],
};

describe("사용자 독립 transaction", () => {
  it("사용여부 저장 성공 후 역할 저장 실패를 분리하고 원천 필드를 쓰지 않는다", async () => {
    vi.mocked(useSession).mockReturnValue({
      user: null,
      loading: false,
      error: "",
      refresh: vi.fn().mockResolvedValue(undefined),
    });
    vi.spyOn(api, "list").mockResolvedValue([]);
    vi.spyOn(usersApi, "search").mockResolvedValue([user]);
    const status = vi.spyOn(usersApi, "useStatus").mockResolvedValue(undefined);
    const roles = vi
      .spyOn(usersApi, "roles")
      .mockRejectedValue(new ApiFailure(400, "역할 검증 실패"));
    render(
      <MemoryRouter>
        <UsersPage />
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole("button", { name: "상세 선택" }));
    fireEvent.click(screen.getByRole("button", { name: "사용여부 저장" }));
    await screen.findByText("저장 후 재조회되었습니다.");
    expect(status).toHaveBeenCalledWith(23, "fixture-status");
    fireEvent.click(screen.getByRole("button", { name: "업무 역할 저장" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "역할 검증 실패",
    );
    expect(screen.getByText("저장 후 재조회되었습니다.")).toBeInTheDocument();
    await waitFor(() =>
      expect(roles).toHaveBeenCalledWith(23, [
        { role_code: "R01", approver_id: 31 },
      ]),
    );
    expect(
      screen.getByRole("heading", { name: "KORUS 원천정보 · 읽기 전용" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("시스템 사용여부")).toHaveValue(
      "fixture-status",
    );
  });
});
