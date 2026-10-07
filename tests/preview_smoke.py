"""Runner 전용: 실제 preview의 인증·9개 읽기 API envelope를 점검한다.

표준 라이브러리만 사용하며 코드 생성 단계에서는 실행하지 않는다.
BASE_URL, SMOKE_LOGIN_ID, SMOKE_PASSWORD를 환경변수로 공급한다.
쓰기 및 정책 승인, mapper materialization, 재시작 영속성을 증명하지 않는다.
"""

import http.cookiejar
import json
import os
import urllib.error
import urllib.parse
import urllib.request


BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8080").rstrip("/")
jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def request(method, path, payload=None, expected=200):
    """민감 응답을 출력하지 않고 상태와 계약 envelope를 확인한다."""
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE_URL + path,
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        response = opener.open(req, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status = response.code
        result = json.load(response)
    assert status == expected, f"{method} {path}: HTTP {status}, expected {expected}"
    assert isinstance(result, dict), f"{path}: object envelope required"
    assert "success" in result and "meta" in result, f"{path}: envelope missing"
    assert result["success"] is (expected == 200), f"{path}: success mismatch"
    assert isinstance(result["meta"], dict), f"{path}: meta object required"
    if expected != 200:
        assert isinstance(result.get("message"), str), f"{path}: message missing"
    print(f"{method} {path}: HTTP {status}, envelope checked")
    return result


def list_pages(path):
    """첫 페이지의 부재를 전체 부재로 오해하지 않고 모든 페이지를 모은다."""
    rows = []
    page = 0
    while True:
        query = urllib.parse.urlencode({"page": page, "size": 20})
        result = request("GET", path + "?" + query)
        assert isinstance(result.get("data"), list), f"{path}: list data required"
        meta = result["meta"]
        assert meta.get("page") == page and meta.get("size") == 20
        assert isinstance(meta.get("total"), int)
        rows.extend(result["data"])
        assert meta["total"] >= len(rows)
        if len(rows) == meta["total"]:
            return rows
        assert result["data"], f"{path}: premature empty page"
        page += 1


def main():
    """현재 세션과 실제 목록을 확인한 뒤 로그아웃과 인증 종료를 검증한다."""
    login = os.environ.get("SMOKE_LOGIN_ID")
    password = os.environ.get("SMOKE_PASSWORD")
    if not login or not password:
        raise SystemExit("SMOKE_LOGIN_ID and SMOKE_PASSWORD are required")
    health = request("GET", "/api/health")
    assert health.get("data", {}).get("healthy") is True
    request("GET", "/api/auth/me", expected=401)
    request("POST", "/api/auth/login", {"login_id": login, "password": password})
    current = request("GET", "/api/auth/me")["data"]
    assert isinstance(current.get("accountId"), int)
    assert current.get("loginId") == login
    assert isinstance(current.get("roles"), list)
    assert "R09" in current["roles"], "local acceptance requires approved R09 seed"
    paginated = (
        "/api/users",
        "/api/organizations",
        "/api/roles",
        "/api/user-roles",
        "/api/menu-permissions",
        "/api/menu-information",
        "/api/code-groups",
        "/api/detail-codes",
    )
    lists = {path: list_pages(path) for path in paginated}
    assert any(
        (row.get("account") or {}).get("account_id") == current["accountId"]
        for row in lists["/api/users"]
    ), "authenticated local seed must be discoverable in users"
    assigned_roles = {
        row.get("role_code") for row in lists["/api/user-roles"]
        if row.get("account_id") == current["accountId"]
    }
    assert set(current["roles"]).issubset(assigned_roles)
    assert set(current["roles"]).issubset(
        {row.get("role_code") for row in lists["/api/roles"]}
    )
    menus = request("GET", "/api/menu-structure")["data"]
    assert isinstance(menus, list)
    expected_routes = {
        "/admin/users",
        "/admin/organizations",
        "/admin/roles",
        "/admin/user-roles",
        "/admin/menu-permissions",
        "/admin/menu-structure",
        "/admin/menu-information",
        "/admin/code-groups",
        "/admin/detail-codes",
    }
    assert expected_routes.issubset({row.get("url") for row in menus})
    assert "/login" not in {row.get("url") for row in menus}
    try:
        request("POST", "/api/auth/logout")
        request("GET", "/api/auth/me", expected=401)
    finally:
        jar.clear()
    print("Read/auth smoke completed; writes/UI/SQL/restart remain separate checks.")


if __name__ == "__main__":
    main()
