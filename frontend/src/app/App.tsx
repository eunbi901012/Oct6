import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Link, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { Sidebar, isAllowed } from "./navigation";
import { useSession } from "./session";
import { LoginPage } from "../pages/LoginPage";
import { UsersPage } from "../pages/UsersPage";
import { OrganizationsPage } from "../pages/OrganizationsPage";
import { RolesPage } from "../pages/RolesPage";
import { MenuInformationPage } from "../pages/MenuInformationPage";
import { CodeGroupsPage } from "../pages/CodeGroupsPage";
import { UserRolesPage } from "../pages/UserRolesPage";
import { MenuPermissionsPage } from "../pages/MenuPermissionsPage";
import { MenuStructurePage } from "../pages/MenuStructurePage";
import { DetailCodesPage } from "../pages/DetailCodesPage";
import { Icon } from "../components/Icon";

function Shell() {
  const { user } = useSession();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  useEffect(() => setMenuOpen(false), [location.pathname]);
  if (!user) return <Outlet />;
  const menu = user.allowedMenus.find((item) => item.url === location.pathname);
  return (
    <div className={`shell ${menuOpen ? "menu-open" : ""}`}>
      <aside id="admin-navigation">
        <div className="brand">
          <span className="brand-mark">
            <Icon name="organization" />
          </span>
          <div>
            <strong>교수업적평가시스템</strong>
            <small>Administration</small>
          </div>
        </div>
        <Sidebar user={user} />
        <div className="sidebar-footer">
          <Icon name="shield" />
          <span>서버에서 허용된 관리 메뉴만 표시됩니다.</span>
        </div>
      </aside>
      <div className="workspace">
        <header className="workspace-header">
          <div className="header-context">
            <button
              className="navigation-toggle"
              type="button"
              aria-label={menuOpen ? "관리 메뉴 닫기" : "관리 메뉴 열기"}
              aria-controls="admin-navigation"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((current) => !current)}
            >
              <Icon name={menuOpen ? "close" : "panel"} />
            </button>
            <span className="breadcrumb">
              시스템 관리 <span>/</span> {menu?.menu_name ?? "접근 확인"}
            </span>
          </div>
          <div className="user-context">
            <span className="avatar" aria-hidden="true">
              {user.loginId.slice(0, 1)}
            </span>
            <div>
              <strong>{user.loginId}</strong>
              <small>{user.roles.join(", ")}</small>
            </div>
          </div>
        </header>
        <main id="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function RouteGate({ children }: { children: ReactNode }) {
  const { user, loading, error, refresh } = useSession();
  const location = useLocation();
  const [denied, setDenied] = useState("");
  useEffect(() => {
    setDenied("");
    const reject = () => {
      setDenied(location.pathname);
      void refresh();
    };
    window.addEventListener("action-denied", reject);
    return () => window.removeEventListener("action-denied", reject);
  }, [location.pathname, refresh]);
  if (loading)
    return (
      <p className="loading-state" role="status">
        인증과 메뉴 권한 확인 중…
      </p>
    );
  if (!user)
    return (
      <section className="access-state" role="alert">
        <span className="icon-capsule">
          <Icon name="lock" />
        </span>
        <h1>로그인이 필요합니다</h1>
        <p>{error || "보호된 데이터를 표시하지 않습니다."}</p>
        <Link to="/login">로그인 화면</Link>
      </section>
    );
  if (denied === location.pathname || !isAllowed(user, location.pathname))
    return (
      <section className="access-state" role="alert">
        <span className="icon-capsule">
          <Icon name="shield" />
        </span>
        <h1>권한 없음</h1>
        <p>
          서버가 허용한 메뉴만 이용할 수 있습니다. 역할코드만으로 우회하지
          않습니다.
        </p>
        <Link to="/login">허용 메뉴 선택</Link>
      </section>
    );
  return <>{children}</>;
}

export function App() {
  const protectedPage = (page: ReactNode) => <RouteGate>{page}</RouteGate>;
  return (
    <>
      <a className="skip-link" href="#content">
        본문으로 건너뛰기
      </a>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<Shell />}>
          <Route path="/admin/users" element={protectedPage(<UsersPage />)} />
          <Route
            path="/admin/organizations"
            element={protectedPage(<OrganizationsPage />)}
          />
          <Route path="/admin/roles" element={protectedPage(<RolesPage />)} />
          <Route
            path="/admin/user-roles"
            element={protectedPage(<UserRolesPage />)}
          />
          <Route
            path="/admin/menu-permissions"
            element={protectedPage(<MenuPermissionsPage />)}
          />
          <Route
            path="/admin/menu-structure"
            element={protectedPage(<MenuStructurePage />)}
          />
          <Route
            path="/admin/menu-information"
            element={protectedPage(<MenuInformationPage />)}
          />
          <Route
            path="/admin/code-groups"
            element={protectedPage(<CodeGroupsPage />)}
          />
          <Route
            path="/admin/detail-codes"
            element={protectedPage(<DetailCodesPage />)}
          />
        </Route>
        <Route
          path="*"
          element={
            <main>
              <h1>등록되지 않은 화면</h1>
              <Link to="/login">로그인 / 허용 메뉴</Link>
            </main>
          }
        />
      </Routes>
    </>
  );
}
