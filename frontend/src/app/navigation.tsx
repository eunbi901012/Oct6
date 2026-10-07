import { Link, NavLink } from "react-router-dom";
import type { CurrentUser, Menu } from "../api/types";
import { Icon } from "../components/Icon";
import type { IconName } from "../components/Icon";

export const screenContracts: Record<
  string,
  { screenId: string; icon: IconName; description: string }
> = {
  "/admin/users": {
    screenId: "SCR-USERS",
    icon: "users",
    description:
      "원천 인사정보를 확인하고 사용자별 시스템 사용여부와 업무 역할을 관리합니다.",
  },
  "/admin/organizations": {
    screenId: "SCR-ORGANIZATIONS",
    icon: "organization",
    description:
      "조직코드로 상하위 관계와 적용기간을 확인합니다. 원천정보는 읽기 전용입니다.",
  },
  "/admin/roles": {
    screenId: "SCR-ROLES",
    icon: "shield",
    description: "역할의 목적, 부여 기준과 데이터 범위 기본값을 관리합니다.",
  },
  "/admin/user-roles": {
    screenId: "SCR-USER-ROLES",
    icon: "users",
    description:
      "사용자별 역할과 승인자, 유효기간을 확인하고 부여·변경·회수합니다.",
  },
  "/admin/menu-permissions": {
    screenId: "SCR-MENU-PERMISSIONS",
    icon: "shield",
    description: "역할·조직·사용자별 메뉴 접근 설정을 조회하고 관리합니다.",
  },
  "/admin/menu-structure": {
    screenId: "SCR-MENU-STRUCTURE",
    icon: "organization",
    description: "메뉴의 부모 관계와 동일 계층의 표시순서를 관리합니다.",
  },
  "/admin/menu-information": {
    screenId: "SCR-MENU-INFORMATION",
    icon: "menu",
    description: "메뉴 실행정보를 관리하고 저장된 화면 링크를 확인합니다.",
  },
  "/admin/code-groups": {
    screenId: "SCR-CODE-GROUPS",
    icon: "code",
    description:
      "코드그룹의 명칭, 설명과 관리부서를 관리하고 상세코드로 이동합니다.",
  },
  "/admin/detail-codes": {
    screenId: "SCR-DETAIL-CODES",
    icon: "code",
    description: "그룹별 코드 계층, 추가속성과 사용여부·유효기간을 관리합니다.",
  },
};

export const routes = [
  "/admin/users",
  "/admin/organizations",
  "/admin/roles",
  "/admin/user-roles",
  "/admin/menu-permissions",
  "/admin/menu-structure",
  "/admin/menu-information",
  "/admin/code-groups",
  "/admin/detail-codes",
] as const;

export function isAllowed(user: CurrentUser | null, path: string): boolean {
  return !!user?.allowedMenus?.some(
    (menu) =>
      menu.url === path &&
      menu.screen_id === screenContracts[path]?.screenId &&
      routes.some((route) => route === path),
  );
}

export function MenuChoices({ user }: { user: CurrentUser }) {
  const menus = user.allowedMenus ?? [];
  const leaves = menus.filter((menu) => menu.url && isAllowed(user, menu.url));
  if (!leaves.length)
    return <p role="status">접근이 허용된 관리 메뉴가 없습니다.</p>;
  return (
    <ul className="menu-choices">
      {leaves.map((menu) => (
        <li key={menu.menu_id}>
          <Link to={menu.url ?? "/login"}>
            <Icon name={screenContracts[menu.url ?? ""]?.icon ?? "menu"} />
            <span>{menu.menu_name ?? menu.screen_id}</span>
            <Icon name="arrow" />
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function Sidebar({ user }: { user: CurrentUser }) {
  const menus = user.allowedMenus ?? [];
  const visible = new Set<number>();
  menus
    .filter((menu) => menu.url && isAllowed(user, menu.url))
    .forEach((leaf) => {
      let node: Menu | undefined = leaf;
      const visited = new Set<number>();
      while (node && !visited.has(node.menu_id)) {
        visited.add(node.menu_id);
        visible.add(node.menu_id);
        node = menus.find((menu) => menu.menu_id === node?.parent_menu_id);
      }
    });
  const draw = (parent: number | null, seen: Set<number>): React.ReactNode => (
    <ul>
      {menus
        .filter(
          (menu) => menu.parent_menu_id === parent && visible.has(menu.menu_id),
        )
        .sort((a, b) => (a.display_order ?? 0) - (b.display_order ?? 0))
        .filter((menu) => !seen.has(menu.menu_id))
        .map((menu) => (
          <li key={menu.menu_id}>
            {menu.url && isAllowed(user, menu.url) ? (
              <NavLink to={menu.url}>
                <Icon name={screenContracts[menu.url]?.icon ?? "menu"} />
                <span>{menu.menu_name ?? menu.screen_id}</span>
              </NavLink>
            ) : (
              <span>{menu.menu_name ?? menu.screen_id}</span>
            )}
            {draw(menu.menu_id, new Set([...seen, menu.menu_id]))}
          </li>
        ))}
    </ul>
  );
  const roots = menus.filter(
    (menu) =>
      visible.has(menu.menu_id) &&
      (menu.parent_menu_id === null ||
        !menus.some((other) => other.menu_id === menu.parent_menu_id)),
  );
  return (
    <nav aria-label="관리 메뉴">
      {roots.map((root) => (
        <section key={root.menu_id}>
          {root.url && isAllowed(user, root.url) ? (
            <NavLink to={root.url}>{root.menu_name ?? root.screen_id}</NavLink>
          ) : (
            <h2>{root.menu_name ?? root.screen_id}</h2>
          )}
          {draw(root.menu_id, new Set([root.menu_id]))}
        </section>
      ))}
      {!roots.length && <p>허용된 메뉴 없음</p>}
    </nav>
  );
}
