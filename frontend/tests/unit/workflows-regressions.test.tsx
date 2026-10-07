import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { api, ApiFailure } from "../../src/api/client";
import { codeGroupsApi } from "../../src/api/code-groups";
import { detailCodesApi } from "../../src/api/detail-codes";
import { menuPermissionsApi } from "../../src/api/menu-permissions";
import { menuStructureApi } from "../../src/api/menu-structure";
import { rolesApi } from "../../src/api/roles";
import { userRolesApi } from "../../src/api/user-roles";
import { usersApi } from "../../src/api/users";
import type {
  DetailCode,
  Menu,
  Role,
  Row,
  UserItem,
  UserRole,
} from "../../src/api/types";
import { useSession } from "../../src/app/session";
import { ResourceEditor } from "../../src/components/ResourceEditor";
import { CatalogPage } from "../../src/pages/CatalogPage";
import { DetailCodesPage } from "../../src/pages/DetailCodesPage";
import { MenuPermissionsPage } from "../../src/pages/MenuPermissionsPage";
import { MenuStructurePage } from "../../src/pages/MenuStructurePage";
import { UserRolesPage } from "../../src/pages/UserRolesPage";
import { UsersPage } from "../../src/pages/UsersPage";
import { roleFields } from "../../src/pages/catalog";

vi.mock("../../src/app/session", () => ({ useSession: vi.fn() }));
afterEach(() => vi.restoreAllMocks());

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
const assignment: UserRole = {
  assignment_id: 17,
  account_id: 23,
  role_code: "R01",
  approver_id: 31,
  valid_start: null,
  valid_end: null,
  assignment_source: null,
  assignment_status: null,
};
const user: UserItem = {
  account: { account_id: 23, login_id: "사용자", use_status: "fixture" },
  personnel: null,
  positions: [],
  roles: [assignment],
};
const otherUser: UserItem = {
  ...user,
  account: { ...user.account!, account_id: 31, login_id: "승인자" },
};
const menus: Menu[] = [1, 2].map((menu_id) => ({
  menu_id,
  menu_name: `메뉴 ${menu_id}`,
  parent_menu_id: null,
  display_order: menu_id,
  screen_id: `screen-${menu_id}`,
  url: null,
  icon: null,
  business_category: null,
  description: null,
  use_status: null,
  created_at: "",
  updated_at: "",
}));
const codes: DetailCode[] = [11, 12].map((detail_code_id) => ({
  detail_code_id,
  code_group_id: 1,
  parent_detail_code_id: null,
  code_value: String(detail_code_id),
  code_name: `코드 ${detail_code_id}`,
}));

beforeEach(() => {
  vi.mocked(useSession).mockReturnValue({
    user: null,
    loading: false,
    error: "",
    refresh: vi.fn().mockResolvedValue(undefined),
  });
  vi.spyOn(api, "list").mockImplementation(
    async <T,>(path: string): Promise<T[]> => {
      const rows: Row[] =
        path === "/api/users"
          ? [user, otherUser]
          : path === "/api/roles"
            ? [{ role_code: "R01", role_name: "역할" }]
            : [];
      return rows as T[];
    },
  );
});
function mount(page: React.ReactNode, entry = "/") {
  return render(<MemoryRouter initialEntries={[entry]}>{page}</MemoryRouter>);
}
async function selectFirst() {
  const buttons = await screen.findAllByRole("button", { name: "상세 선택" });
  fireEvent.click(buttons[0]!);
}
function expectRowsLocked() {
  screen
    .getAllByRole("button", { name: "상세 선택" })
    .forEach((button) => expect(button).toBeDisabled());
}

describe("mutation workflow regressions", () => {
  it.each(["사용여부 저장", "업무 역할 저장"])(
    "Users: %s 동안 검색과 행 전환을 막는다",
    async (label) => {
      vi.spyOn(usersApi, "search").mockResolvedValue([user, otherUser]);
      const pending = deferred<void>();
      vi.spyOn(usersApi, "useStatus").mockReturnValue(pending.promise);
      vi.spyOn(usersApi, "roles").mockReturnValue(pending.promise);
      mount(<UsersPage />);
      await selectFirst();
      fireEvent.click(screen.getByRole("button", { name: label }));
      expect(screen.getByRole("button", { name: "검색" })).toBeDisabled();
      expectRowsLocked();
      expect(screen.getByLabelText("교번")).toBeDisabled();
      await act(async () => pending.reject(new Error("저장 실패")));
    },
  );

  it("Users: 역할 저장 후 재조회 실패에도 역할 초안을 유지한다", async () => {
    vi.spyOn(usersApi, "search")
      .mockResolvedValueOnce([user])
      .mockRejectedValue(new Error("재조회 실패"));
    vi.spyOn(usersApi, "roles").mockResolvedValue(undefined);
    mount(<UsersPage />);
    await selectFirst();
    fireEvent.change(screen.getByLabelText("유효 시작일"), {
      target: { value: "2026-02-01" },
    });
    const field = screen.getByLabelText("유효 시작일");
    fireEvent.click(screen.getByRole("button", { name: "업무 역할 저장" }));
    await screen.findAllByText("재조회 실패");
    expect(screen.getByLabelText("유효 시작일")).toBe(field);
    expect(field).toHaveValue("2026-02-01");
  });

  it("Catalog: 저장 동안 대상·등록·조회를 잠그고 실패한 재조회에도 editor를 유지한다", async () => {
    const pending = deferred<Role[]>();
    vi.spyOn(rolesApi, "list")
      .mockResolvedValueOnce([
        { role_code: "R01", role_name: "기존" },
        { role_code: "R02", role_name: "다른" },
      ])
      .mockReturnValue(pending.promise);
    vi.spyOn(rolesApi, "update").mockResolvedValue(undefined);
    mount(
      <CatalogPage
        endpoint="roles"
        title="역할"
        fields={roleFields}
        idKey="role_code"
      />,
    );
    await selectFirst();
    fireEvent.change(screen.getByLabelText("역할명"), {
      target: { value: "초안" },
    });
    const field = screen.getByLabelText("역할명");
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
    expectRowsLocked();
    await waitFor(() => expect(rolesApi.list).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText("역할명")).toBe(field);
    await act(async () => pending.reject(new Error("재조회 실패")));
    expect(screen.getByLabelText("역할명")).toBe(field);
    expect(field).toHaveValue("초안");
    expect(
      screen.queryByText("저장 후 재조회되었습니다."),
    ).not.toBeInTheDocument();
  });

  it("Catalog: 수동 재조회도 편집 초안을 폐기하지 않는다", async () => {
    vi.spyOn(rolesApi, "list").mockResolvedValue([
      { role_code: "R01", role_name: "기존" },
    ]);
    mount(
      <CatalogPage
        endpoint="roles"
        title="역할"
        fields={roleFields}
        idKey="role_code"
      />,
    );
    await selectFirst();
    fireEvent.change(screen.getByLabelText("역할명"), {
      target: { value: "초안" },
    });
    const field = screen.getByLabelText("역할명");
    fireEvent.click(screen.getByRole("button", { name: "조회" }));
    await waitFor(() => expect(rolesApi.list).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText("역할명")).toBe(field);
    expect(field).toHaveValue("초안");
  });

  it("Catalog: 신규 저장 후 editor를 닫아도 조회·등록 잠금이 해제된다", async () => {
    vi.spyOn(rolesApi, "list").mockResolvedValue([]);
    vi.spyOn(rolesApi, "create").mockResolvedValue(undefined);
    mount(
      <CatalogPage
        endpoint="roles"
        title="역할"
        fields={roleFields}
        idKey="role_code"
      />,
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "등록" })).not.toBeDisabled(),
    );
    fireEvent.click(screen.getByRole("button", { name: "등록" }));
    fireEvent.change(screen.getByLabelText("역할코드"), {
      target: { value: "R01" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findByText("저장 후 재조회되었습니다.");
    expect(screen.getByRole("button", { name: "등록" })).not.toBeDisabled();
    expect(screen.getByRole("button", { name: "조회" })).not.toBeDisabled();
  });

  async function mountCodes() {
    vi.spyOn(codeGroupsApi, "list").mockResolvedValue([
      { code_group_id: 1, group_id: "A", group_name: "그룹 A" },
      { code_group_id: 2, group_id: "B", group_name: "그룹 B" },
    ]);
    mount(<DetailCodesPage />, "/admin/detail-codes?groupId=A");
    await selectFirst();
  }
  it("DetailCodes: 저장/재조회 중 문맥·대상을 잠그고 실패 시 초안을 유지한다", async () => {
    const pending = deferred<DetailCode[]>();
    vi.spyOn(detailCodesApi, "list")
      .mockResolvedValueOnce(codes)
      .mockReturnValue(pending.promise);
    vi.spyOn(detailCodesApi, "update").mockResolvedValue(undefined);
    await mountCodes();
    fireEvent.change(screen.getByLabelText("코드명"), {
      target: { value: "초안" },
    });
    const field = screen.getByLabelText("코드명");
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByLabelText("코드그룹")).toBeDisabled();
    expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
    expectRowsLocked();
    await waitFor(() => expect(detailCodesApi.list).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText("코드명")).toBe(field);
    await act(async () => pending.reject(new Error("재조회 실패")));
    expect(screen.getByLabelText("코드명")).toBe(field);
    expect(field).toHaveValue("초안");
  });

  it("DetailCodes: 수정 후 저장된 같은 내부 식별자를 계속 선택한다", async () => {
    vi.spyOn(detailCodesApi, "list")
      .mockResolvedValueOnce(codes)
      .mockResolvedValue([
        { ...codes[0]!, code_name: "저장된 이름" },
        codes[1]!,
      ]);
    const save = vi
      .spyOn(detailCodesApi, "update")
      .mockResolvedValue(undefined);
    await mountCodes();
    fireEvent.change(screen.getByLabelText("코드명"), {
      target: { value: "저장된 이름" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findAllByText("저장 후 재조회되었습니다.");
    expect(screen.getByLabelText("코드명")).toHaveValue("저장된 이름");
    expect(screen.getByText("선택 상세 수정")).toBeInTheDocument();
    expect(save).toHaveBeenCalledWith(11, { code_name: "저장된 이름" });
  });

  it("ResourceEditor: 선택 옵션 갱신은 작성 중인 초안을 초기화하지 않는다", () => {
    const selected = { id: 1, name: "기존" };
    const onSaved = vi.fn().mockResolvedValue(undefined);
    const { rerender } = render(
      <ResourceEditor
        endpoint="roles"
        idKey="id"
        selected={selected}
        fields={[{ key: "name", label: "이름" }]}
        onSaved={onSaved}
      />,
    );
    fireEvent.change(screen.getByLabelText("이름"), {
      target: { value: "초안" },
    });
    rerender(
      <ResourceEditor
        endpoint="roles"
        idKey="id"
        selected={selected}
        fields={[
          { key: "name", label: "이름" },
          { key: "parent", label: "부모", choices: [] },
        ]}
        onSaved={onSaved}
      />,
    );
    expect(screen.getByLabelText("이름")).toHaveValue("초안");
  });

  it("UserRoles: 저장 중 문맥과 행을 잠그고 재조회 실패 시 변경 초안을 유지한다", async () => {
    const pending = deferred<UserRole[]>();
    vi.spyOn(userRolesApi, "list")
      .mockResolvedValueOnce([assignment, { ...assignment, assignment_id: 18 }])
      .mockReturnValue(pending.promise);
    vi.spyOn(userRolesApi, "change").mockResolvedValue(undefined);
    mount(<UserRolesPage />);
    await screen.findByRole("option", { name: "사용자 (23)" });
    fireEvent.change(screen.getByLabelText("역할 조회 대상 사용자"), {
      target: { value: "23" },
    });
    fireEvent.click(screen.getByRole("button", { name: "조회" }));
    await selectFirst();
    fireEvent.change(screen.getByLabelText("유효 시작일"), {
      target: { value: "2026-02-01" },
    });
    const field = screen.getByLabelText("유효 시작일");
    fireEvent.click(screen.getByRole("button", { name: "변경 저장" }));
    expect(screen.getByLabelText("역할 조회 대상 사용자")).toBeDisabled();
    expectRowsLocked();
    await waitFor(() => expect(userRolesApi.list).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText("유효 시작일")).toBe(field);
    await act(async () => pending.reject(new Error("재조회 실패")));
    expect(field).toHaveValue("2026-02-01");
    expect(screen.getByLabelText("유효 시작일")).toBe(field);
  });

  it("MenuPermissions: 저장 중 종류·대상·행을 잠그고 재조회 실패 시 선택을 유지한다", async () => {
    const pending = deferred<import("../../src/api/types").Permission[]>();
    vi.spyOn(menuStructureApi, "list").mockResolvedValue(menus);
    vi.spyOn(menuPermissionsApi, "list")
      .mockResolvedValueOnce([{ menu_id: 1, access_allowed: false }])
      .mockReturnValue(pending.promise);
    vi.spyOn(menuPermissionsApi, "save").mockResolvedValue(undefined);
    mount(<MenuPermissionsPage />);
    await screen.findByRole("option", { name: "역할 (R01)" });
    fireEvent.change(screen.getByLabelText("대상 역할"), {
      target: { value: "R01" },
    });
    fireEvent.click(screen.getByRole("button", { name: "조회" }));
    await selectFirst();
    fireEvent.change(screen.getByLabelText("접근 허용 여부"), {
      target: { value: "true" },
    });
    const field = screen.getByLabelText("접근 허용 여부");
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByLabelText("대상 종류")).toBeDisabled();
    expect(screen.getByLabelText("대상 역할")).toBeDisabled();
    expectRowsLocked();
    await waitFor(() =>
      expect(menuPermissionsApi.list).toHaveBeenCalledTimes(2),
    );
    expect(screen.getByLabelText("접근 허용 여부")).toBe(field);
    await act(async () => pending.reject(new Error("재조회 실패")));
    expect(screen.getByLabelText("접근 허용 여부")).toBe(field);
    expect(field).toHaveValue("true");
  });

  it("MenuStructure: 저장 중 조회·행을 잠그고 재조회 실패에도 형제 순서 초안을 유지한다", async () => {
    const pending = deferred<Menu[]>();
    vi.spyOn(menuStructureApi, "list")
      .mockResolvedValueOnce(menus)
      .mockReturnValue(pending.promise);
    vi.spyOn(menuStructureApi, "order").mockResolvedValue(undefined);
    mount(<MenuStructurePage />);
    await selectFirst();
    fireEvent.change(screen.getByLabelText("메뉴 1 표시순서"), {
      target: { value: "7" },
    });
    const field = screen.getByLabelText("메뉴 1 표시순서");
    fireEvent.click(screen.getByRole("button", { name: "순서 저장" }));
    expect(screen.getByRole("button", { name: "조회" })).toBeDisabled();
    expectRowsLocked();
    await waitFor(() => expect(menuStructureApi.list).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText("메뉴 1 표시순서")).toBe(field);
    await act(async () => pending.reject(new Error("재조회 실패")));
    expect(screen.getByLabelText("메뉴 1 표시순서")).toBe(field);
    expect(field).toHaveValue(7);
  });

  it("MenuStructure: 재조회 실패 시 선택한 부모와 순서 초안 모두 남는다", async () => {
    vi.spyOn(menuStructureApi, "list")
      .mockResolvedValueOnce(menus)
      .mockRejectedValue(new Error("재조회 실패"));
    vi.spyOn(menuStructureApi, "parent").mockResolvedValue(undefined);
    mount(<MenuStructurePage />);
    await selectFirst();
    fireEvent.change(screen.getByLabelText("부모메뉴"), {
      target: { value: "2" },
    });
    fireEvent.change(screen.getByLabelText("메뉴 1 표시순서"), {
      target: { value: "7" },
    });
    fireEvent.click(screen.getByRole("button", { name: "부모 저장" }));
    await screen.findAllByText("재조회 실패");
    expect(screen.getByLabelText("부모메뉴")).toHaveValue("2");
    expect(screen.getByLabelText("메뉴 1 표시순서")).toHaveValue(7);
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.getByLabelText("메뉴 1 표시순서")).toHaveValue(1);
  });

  it("UserRoles: 부여 초안과 editor는 재조회 실패 시에도 유지한다", async () => {
    vi.spyOn(userRolesApi, "list")
      .mockResolvedValueOnce([])
      .mockRejectedValue(new Error("재조회 실패"));
    const save = vi.spyOn(userRolesApi, "grant").mockResolvedValue(undefined);
    mount(<UserRolesPage />);
    await screen.findByRole("option", { name: "사용자 (23)" });
    fireEvent.change(screen.getByLabelText("역할 조회 대상 사용자"), {
      target: { value: "23" },
    });
    fireEvent.click(screen.getByRole("button", { name: "조회" }));
    await screen.findByRole("button", { name: "부여 저장" });
    await screen.findByRole("option", { name: "역할 (R01)" });
    fireEvent.change(screen.getByLabelText("역할"), {
      target: { value: "R01" },
    });
    fireEvent.change(screen.getByLabelText("승인자"), {
      target: { value: "31" },
    });
    fireEvent.change(screen.getByLabelText("유효 시작일"), {
      target: { value: "2026-02-01" },
    });
    const field = screen.getByLabelText("유효 시작일");
    fireEvent.click(screen.getByRole("button", { name: "부여 저장" }));
    await screen.findAllByText("재조회 실패");
    expect(screen.getByLabelText("유효 시작일")).toBe(field);
    expect(field).toHaveValue("2026-02-01");
    expect(save).toHaveBeenCalledWith(23, [
      { role_code: "R01", approver_id: 31, valid_start: "2026-02-01" },
    ]);
  });

  it("Catalog: 보호된 목록 403은 편집 내용을 숨긴다", async () => {
    vi.spyOn(rolesApi, "list")
      .mockResolvedValueOnce([{ role_code: "R01", role_name: "기존" }])
      .mockRejectedValue(new ApiFailure(403, "권한 거부"));
    vi.spyOn(rolesApi, "update").mockResolvedValue(undefined);
    mount(
      <CatalogPage
        endpoint="roles"
        title="역할"
        fields={roleFields}
        idKey="role_code"
      />,
    );
    await selectFirst();
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findAllByText(/권한 거부/);
    expect(screen.queryByLabelText("역할명")).not.toBeInTheDocument();
  });
});
