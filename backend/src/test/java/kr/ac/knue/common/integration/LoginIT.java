package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import static org.assertj.core.api.Assertions.assertThat;

public class LoginIT extends PostgreSqlHttpSupport {
    @Test void initialAdminBcryptDatabaseSessionNineMenusAndHttpLogout() throws Exception {
        var current = ok(HttpMethod.GET, "/api/auth/me", null).path("data");
        assertThat(current.path("accountId").asLong()).isEqualTo(adminId);
        assertThat(current.path("roles").toString()).isEqualTo("[\"R09\"]");
        assertThat(current.path("allowedMenus").size()).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session s JOIN user_account a ON a.account_id=s.account_id WHERE a.login_id='admin' AND s.created_at IS NOT NULL AND s.updated_at IS NOT NULL", Integer.class)).isEqualTo(1);
        for (String route : new String[]{"users", "organizations", "roles", "user-roles", "menu-permissions", "menu-structure", "menu-information", "code-groups", "detail-codes"}) {
            var payload = ok(HttpMethod.GET, "/api/" + route, null);
            assertThat(payload.path("data").isArray()).as(route).isTrue();
        }
        var accounts = snapshot("user_account");
        var assignments = snapshot("user_role");
        var logout = call(HttpMethod.POST, "/api/auth/logout", null, session);
        assertThat(logout.getStatusCode().value()).isEqualTo(200);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "HttpOnly", "SameSite=Lax");
        assertThat(call(HttpMethod.GET, "/api/auth/me", null, session).getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.POST, "/api/auth/logout", null, session).getStatusCode().value()).isEqualTo(401);
        assertThat(snapshot("user_account")).isEqualTo(accounts);
        assertThat(snapshot("user_role")).isEqualTo(assignments);
    }

    @Test void invalidLoginDoesNotCreateSessionsAndOrdinaryAccountNeedsNoMenuForMeOrLogout() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM session", Integer.class);
        var invalid = call(HttpMethod.POST, "/api/auth/login", "{\"login_id\":\"admin\",\"password\":\"wrong\"}", null);
        assertThat(invalid.getStatusCode().value()).isEqualTo(401);
        assertThat(invalid.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session", Integer.class)).isEqualTo(before);
        assertThat(call(HttpMethod.POST, "/api/auth/login", "{\"login_id\":{},\"password\":\"admin\"}", null).getStatusCode().value()).isEqualTo(400);
        createLocal("ordinary");
        var login = call(HttpMethod.POST, "/api/auth/login", "{\"login_id\":\"ordinary\",\"password\":\"fixture-secret\"}", null);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        var current = call(HttpMethod.GET, "/api/auth/me", null, cookie);
        assertThat(current.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(current.getBody()).path("data").path("allowedMenus").size()).isZero();
        assertThat(call(HttpMethod.GET, "/api/roles", null, cookie).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/auth/logout", null, cookie).getStatusCode().value()).isEqualTo(200);
    }

    @Test void explicitDatabasePermissionNotR09BypassAndAmbiguityIsPending() throws Exception {
        Long roleMenu = menu("SCR-ROLES");
        jdbc.update("DELETE FROM menu_permission WHERE menu_id=? AND role_code='R09'", roleMenu);
        assertThat(call(HttpMethod.GET, "/api/roles", null, session).getStatusCode().value()).isEqualTo(403);
        var current = ok(HttpMethod.GET, "/api/auth/me", null).path("data").path("allowedMenus");
        assertThat(current.toString()).doesNotContain("SCR-ROLES");
        jdbc.update("INSERT INTO menu_permission(menu_id,role_code,access_allowed) VALUES (?,'R09',true)", roleMenu);
        jdbc.update("INSERT INTO menu_permission(menu_id,account_id,access_allowed) VALUES (?,?,false)", roleMenu, adminId);
        var blocked = call(HttpMethod.GET, "/api/roles", null, session);
        assertThat(blocked.getStatusCode().value()).isEqualTo(403);
        assertThat(blocked.getBody()).contains("OQ-003");
        assertThat(ok(HttpMethod.GET, "/api/auth/me", null).path("data").path("allowedMenus").toString()).doesNotContain("SCR-ROLES");
    }
}
