import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi, afterEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { RouteGate } from "../../src/app/App";
import { useSession } from "../../src/app/session";
import { AssignmentEditor } from "../../src/components/AssignmentEditor";
import { ApiFailure, api } from "../../src/api/client";
import { Message } from "../../src/components/common";

vi.mock("../../src/app/session", () => ({ useSession: vi.fn() }));
afterEach(() => vi.restoreAllMocks());

describe("인증·권한·상태", () => {
  it("유효 인증이 있어도 R09 빈 허용목록은 보호 컴포넌트를 마운트하지 않는다", () => {
    vi.mocked(useSession).mockReturnValue({
      user: {
        accountId: 44,
        loginId: "계정",
        roles: ["R09"],
        allowedMenus: [],
      },
      loading: false,
      error: "",
      refresh: vi.fn().mockResolvedValue(undefined),
    });
    render(
      <MemoryRouter initialEntries={["/admin/users"]}>
        <RouteGate>
          <p>보호 데이터</p>
        </RouteGate>
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("heading", { name: "권한 없음" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("보호 데이터")).not.toBeInTheDocument();
  });
  it("401 안내는 공개 로그인 링크를 제공한다", () => {
    render(
      <MemoryRouter>
        <Message error={new ApiFailure(401, "세션 만료")} />
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("link", { name: "로그인이 필요합니다" }),
    ).toHaveAttribute("href", "/login");
  });
  it("부여 승인자는 비어 있고 추가 역할마다 개별 기록을 입력한다", async () => {
    vi.spyOn(api, "list").mockResolvedValue([]);
    const change = vi.fn();
    render(
      <MemoryRouter>
        <AssignmentEditor initial={[{}]} onChange={change} />
      </MemoryRouter>,
    );
    const approver = await screen.findByLabelText("승인자");
    expect(approver).toHaveValue("");
    fireEvent.click(screen.getByRole("button", { name: "역할 추가" }));
    expect(screen.getAllByLabelText("승인자")).toHaveLength(2);
    expect(change).toHaveBeenLastCalledWith([{}, {}]);
  });
});
