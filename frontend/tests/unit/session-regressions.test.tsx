import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { loginApi } from "../../src/api/login";
import { SessionProvider, useSession } from "../../src/app/session";
import type { CurrentUser, Envelope } from "../../src/api/types";

const user: CurrentUser = {
  accountId: 17,
  loginId: "단위시험",
  roles: [],
  allowedMenus: [],
};
const envelope: Envelope<CurrentUser> = { success: true, data: user, meta: {} };
function Probe() {
  const session = useSession();
  return (
    <>
      <p>{session.user ? "인증됨" : "비인증"}</p>
      <p>{session.error}</p>
      <button type="button" onClick={() => void session.refresh()}>
        권한 재조회
      </button>
    </>
  );
}

describe("접근 결정의 유효성과 요청 순서", () => {
  it("null 메뉴가 섞인 접근 결정은 인증 상태로 노출하지 않는다", async () => {
    vi.spyOn(loginApi, "me").mockResolvedValue({
      ...envelope,
      data: { ...user, allowedMenus: [null] },
    } as unknown as Envelope<CurrentUser>);
    render(
      <SessionProvider>
        <Probe />
      </SessionProvider>,
    );
    await screen.findByText(/서버 허용 메뉴 계약/);
    expect(screen.getByText("비인증")).toBeVisible();
  });

  it("세션 만료 이후 늦게 도착한 응답으로 인증을 복원하지 않는다", async () => {
    let resolve!: (value: Envelope<CurrentUser>) => void;
    vi.spyOn(loginApi, "me").mockReturnValue(
      new Promise((yes) => {
        resolve = yes;
      }),
    );
    render(
      <SessionProvider>
        <Probe />
      </SessionProvider>,
    );
    act(() => window.dispatchEvent(new Event("session-expired")));
    await act(async () => resolve(envelope));
    expect(screen.getByText("비인증")).toBeVisible();
  });

  it("새 권한 결정이 도착한 뒤 오래된 응답을 무시한다", async () => {
    let resolve!: (value: Envelope<CurrentUser>) => void;
    vi.spyOn(loginApi, "me")
      .mockReturnValueOnce(
        new Promise((yes) => {
          resolve = yes;
        }),
      )
      .mockResolvedValueOnce({
        ...envelope,
        data: { ...user, allowedMenus: [null] },
      } as unknown as Envelope<CurrentUser>);
    render(
      <SessionProvider>
        <Probe />
      </SessionProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "권한 재조회" }));
    await waitFor(() =>
      expect(screen.getByText(/서버 허용 메뉴 계약/)).toBeVisible(),
    );
    await act(async () => resolve(envelope));
    expect(screen.getByText("비인증")).toBeVisible();
  });
});
