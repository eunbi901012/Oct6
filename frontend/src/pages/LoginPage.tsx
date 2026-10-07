import { useState } from "react";
import { loginApi } from "../api/login";
import { useSession } from "../app/session";
import { MenuChoices } from "../app/navigation";
import { Message, useAction } from "../components/common";
import { Icon } from "../components/Icon";

export function LoginPage() {
  const { user, loading, error, refresh } = useSession();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const action = useAction();
  return (
    <main className="login" id="content">
      <section className="login-brand-panel">
        <div className="brand">
          <span className="brand-mark">
            <Icon name="organization" />
          </span>
          <strong>교수업적평가시스템</strong>
        </div>
        <div className="login-brand-copy">
          <p className="eyebrow">University Administration</p>
          <h1>
            일관된 기준으로,
            <br />
            체계적인 시스템 관리.
          </h1>
          <p>
            사용자와 조직, 역할과 권한, 메뉴와 공통코드를 한 곳에서 관리합니다.
          </p>
          <div className="login-domains">
            <span>
              <Icon name="users" />
              사용자·조직
            </span>
            <span>
              <Icon name="shield" />
              역할·권한
            </span>
            <span>
              <Icon name="menu" />
              메뉴·공통코드
            </span>
          </div>
        </div>
        <p className="login-brand-footer">
          <Icon name="lock" />
          인증과 메뉴 접근 권한에 따라 이용할 수 있습니다.
        </p>
      </section>
      <section className="login-form-panel">
        <p className="eyebrow">관리 시스템</p>
        <h2>{user ? "관리 메뉴 선택" : "로그인"}</h2>
        <p className="login-description">
          {user
            ? "서버에서 허용한 메뉴를 선택해 관리 업무를 시작하세요."
            : "내부 계정으로 로그인하여 관리 업무를 시작하세요."}
        </p>
        {!user && (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void action.run(async () => {
                await loginApi.login(loginId, password);
                setPassword("");
                await refresh();
              });
            }}
          >
            <fieldset disabled={action.busy || loading}>
              <label>
                아이디
                <input
                  autoComplete="username"
                  value={loginId}
                  onChange={(event) => setLoginId(event.target.value)}
                  required
                />
              </label>
              <label>
                비밀번호
                <input
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />
              </label>
              <button type="submit">
                <Icon name="lock" />
                로그인
                <Icon name="arrow" />
              </button>
            </fieldset>
          </form>
        )}
        <Message error={action.error} busy={action.busy || loading} />
        {error && <p role="alert">{error}</p>}
        {user && (
          <section>
            <p role="status">인증되었습니다. 이용할 메뉴를 선택하세요.</p>
            <MenuChoices user={user} />
          </section>
        )}
      </section>
    </main>
  );
}
