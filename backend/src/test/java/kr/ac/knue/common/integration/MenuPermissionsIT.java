package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class MenuPermissionsIT extends PostgreSqlHttpSupport {
    @Test void explicitSubjectSettingsInsertUpdateAndReadbackDoNotModifyMenusOrRoles() throws Exception {
        Long target = createLocal("permission-target");
        Long menu = menu("SCR-USERS");
        var menus = snapshot("menu");
        var roles = snapshot("role");
        var created = ok(HttpMethod.PUT, "/api/menu-permissions", "{\"permissions\":[{\"menu_id\":" + menu + ",\"account_id\":" + target + ",\"access_allowed\":true}]}")
            .path("data").get(0);
        long permission = created.path("permission_id").asLong();
        var before = jdbc.queryForMap("SELECT * FROM menu_permission WHERE permission_id=?", permission);
        ok(HttpMethod.PUT, "/api/menu-permissions", "{\"permissions\":[{\"menu_id\":" + menu + ",\"account_id\":" + target + ",\"access_allowed\":false}]}");
        var after = jdbc.queryForMap("SELECT * FROM menu_permission WHERE permission_id=?", permission);
        assertThat(after.get("access_allowed")).isEqualTo(false);
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("permission_id")).isEqualTo(before.get("permission_id"));
        var readback = ok(HttpMethod.GET, "/api/menu-permissions?userId=" + target + "&menuId=" + menu, null).path("data");
        assertThat(readback.size()).isEqualTo(1);
        assertThat(readback.get(0).path("access_allowed").asBoolean()).isFalse();
        assertThat(snapshot("menu")).isEqualTo(menus);
        assertThat(snapshot("role")).isEqualTo(roles);
    }

    @Test void secondPermissionForeignKeyFailureRollsBackFirstInsert() throws Exception {
        Long target = createLocal("permission-rollback");
        var before = snapshot("menu_permission");
        String body = "{\"permissions\":[{\"menu_id\":" + menu("SCR-USERS") + ",\"account_id\":" + target + ",\"access_allowed\":true},{\"menu_id\":9223372036854775807,\"account_id\":" + target + ",\"access_allowed\":false}]}";
        assertThat(call(HttpMethod.PUT, "/api/menu-permissions", body, session).getStatusCode().value()).isEqualTo(400);
        assertThat(snapshot("menu_permission")).isEqualTo(before);
    }

    @Test void duplicateStoredTargetDoesNotSelectAnArbitraryPermission() throws Exception {
        Long target = createLocal("ambiguous-target");
        Long menu = menu("SCR-USERS");
        jdbc.update("INSERT INTO menu_permission(menu_id,account_id,access_allowed) VALUES (?,?,true),(?,?,false)", menu, target, menu, target);
        var before = snapshot("menu_permission");
        var response = call(HttpMethod.PUT, "/api/menu-permissions", "{\"permissions\":[{\"menu_id\":" + menu + ",\"account_id\":" + target + ",\"access_allowed\":true}]}", session);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("OQ-003");
        assertThat(snapshot("menu_permission")).isEqualTo(before);
    }
}
