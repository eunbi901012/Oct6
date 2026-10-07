package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class MenuStructureIT extends PostgreSqlHttpSupport {
    @Test void parentAndSiblingOrderPersistWithoutEditingExecutionInformationOrPermissions() throws Exception {
        Long selected = menu("SCR-DETAIL-CODES");
        Long newParent = jdbc.queryForObject("SELECT parent_menu_id FROM menu WHERE screen_id='SCR-USERS'", Long.class);
        var before = jdbc.queryForMap("SELECT * FROM menu WHERE menu_id=?", selected);
        var permissions = snapshot("menu_permission");
        ok(HttpMethod.PATCH, "/api/menu-structure/" + selected + "/parent", "{\"parent_menu_id\":" + newParent + "}");
        var after = jdbc.queryForMap("SELECT * FROM menu WHERE menu_id=?", selected);
        assertThat(after.get("parent_menu_id")).isEqualTo(newParent);
        for (String key : new String[]{"screen_id", "url", "menu_name", "display_order", "created_at"}) assertThat(after.get(key)).as(key).isEqualTo(before.get(key));
        Long sibling = menu("SCR-USERS");
        ok(HttpMethod.PUT, "/api/menu-structure/order", "{\"parent_menu_id\":" + newParent + ",\"items\":[{\"menu_id\":" + selected + ",\"display_order\":0},{\"menu_id\":" + sibling + ",\"display_order\":8}]}");
        assertThat(jdbc.queryForObject("SELECT display_order FROM menu WHERE menu_id=?", Integer.class, selected)).isZero();
        assertThat(jdbc.queryForObject("SELECT display_order FROM menu WHERE menu_id=?", Integer.class, sibling)).isEqualTo(8);
        assertThat(ok(HttpMethod.GET, "/api/menu-structure", null).path("data").toString()).contains("SCR-DETAIL-CODES");
        assertThat(snapshot("menu_permission")).isEqualTo(permissions);
    }

    @Test void nonSiblingOrMissingReferenceBatchCannotPartiallyChangeOrder() throws Exception {
        Long users = menu("SCR-USERS");
        Long parent = jdbc.queryForObject("SELECT parent_menu_id FROM menu WHERE menu_id=?", Long.class, users);
        var before = snapshot("menu");
        var invalid = call(HttpMethod.PUT, "/api/menu-structure/order", "{\"parent_menu_id\":" + parent + ",\"items\":[{\"menu_id\":" + users + ",\"display_order\":99},{\"menu_id\":" + menu("SCR-ROLES") + ",\"display_order\":100}]}", session);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(snapshot("menu")).isEqualTo(before);
        var missing = call(HttpMethod.PUT, "/api/menu-structure/order", "{\"parent_menu_id\":" + parent + ",\"items\":[{\"menu_id\":" + users + ",\"display_order\":99},{\"menu_id\":9223372036854775807,\"display_order\":100}]}", session);
        assertThat(missing.getStatusCode().value()).isEqualTo(400);
        var failure = json.readTree(missing.getBody());
        assertThat(failure.path("success").asBoolean()).isFalse();
        assertThat(failure.path("fieldErrors").get(0).path("field").asText()).isEqualTo("menu_id");
        assertThat(failure.path("fieldErrors").get(0).path("message").asText())
            .isEqualTo("Requested record does not exist");
        assertThat(snapshot("menu")).isEqualTo(before);
    }
}
