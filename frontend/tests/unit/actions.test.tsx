import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, afterEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ResourceEditor } from "../../src/components/ResourceEditor";
import { api, ApiFailure } from "../../src/api/client";
import { roleFields } from "../../src/pages/catalog";

afterEach(() => vi.restoreAllMocks());

describe("저장과 상태", () => {
  it("불변 역할코드는 PATCH body에서 제외하고 저장 후 재조회한다", async () => {
    const save = vi.spyOn(api, "mutate").mockResolvedValue(undefined);
    const reload = vi.fn().mockResolvedValue(undefined);
    render(
      <MemoryRouter>
        <ResourceEditor
          endpoint="roles"
          fields={roleFields}
          idKey="role_code"
          selected={{ role_code: "R02", role_name: "기존 명칭" }}
          onSaved={reload}
        />
      </MemoryRouter>,
    );
    expect(screen.getByLabelText("역할코드")).toHaveAttribute("readonly");
    fireEvent.change(screen.getByLabelText("역할명"), {
      target: { value: "변경 명칭" },
    });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(reload).toHaveBeenCalled());
    expect(save).toHaveBeenCalledWith(
      "PATCH",
      "/api/roles/R02",
      expect.not.objectContaining({ role_code: "R02" }),
    );
  });
  it("서버 필드 오류는 초안을 유지하고 재조회 성공으로 표시하지 않는다", async () => {
    vi.spyOn(api, "mutate").mockRejectedValue(
      new ApiFailure(400, "검증 실패", [
        { field: "role_name", message: "명칭을 확인하세요" },
      ]),
    );
    const reload = vi.fn();
    render(
      <MemoryRouter>
        <ResourceEditor
          endpoint="roles"
          fields={roleFields}
          idKey="role_code"
          selected={{ role_code: "R03", role_name: "입력 유지" }}
          onSaved={reload}
        />
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "명칭을 확인하세요",
    );
    expect(screen.getByLabelText("역할명")).toHaveValue("입력 유지");
    expect(reload).not.toHaveBeenCalled();
  });
});
