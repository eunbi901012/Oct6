import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiFailure } from "../../src/api/client";

afterEach(() => vi.unstubAllGlobals());

describe("상대경로 계약", () => {
  it("모든 페이지에서 실제 항목을 읽고 camelCase 검색조건을 보존한다", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: [{ account_id: 13 }],
            meta: { page: 0, size: 1, total: 2 },
          }),
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: [{ account_id: 29 }],
            meta: { page: 1, size: 1, total: 2 },
          }),
        ),
      );
    vi.stubGlobal("fetch", fetcher);
    expect(
      await api.list("/api/users", { personName: "교직원" }, true),
    ).toEqual([{ account_id: 13 }, { account_id: 29 }]);
    expect(fetcher.mock.calls[0]?.[0]).toContain("/api/users?personName=");
    expect(fetcher.mock.calls[1]?.[0]).toContain("page=1");
    expect(fetcher.mock.calls[0]?.[1]).toMatchObject({
      credentials: "same-origin",
    });
  });
  it("기본 조회는 backend의 전체 목록 응답을 한 요청으로 소비한다", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: [{ account_id: 13 }, { account_id: 29 }],
          meta: { page: 0, size: 2, total: 2 },
        }),
      ),
    );
    vi.stubGlobal("fetch", fetcher);
    expect(await api.list("/api/users")).toHaveLength(2);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0]?.[0]).toBe("/api/users");
  });
  it("403 응답을 빈 목록으로 바꾸지 않는다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            message: "권한 없음",
            meta: {},
          }),
          { status: 403 },
        ),
      ),
    );
    await expect(api.list("/api/roles")).rejects.toBeInstanceOf(ApiFailure);
  });
});
