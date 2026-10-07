package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class UsersIT extends PostgreSqlHttpSupport {
    @Test void compositeSourceIsReadOnlyAndLocalStatusRolesArePersistedAndRequeried() throws Exception {
        var source = snapshot("korus_personnel_snapshot");
        var positions = snapshot("organization_user_mapping");
        var items = ok(HttpMethod.GET, "/api/users?employeeNumber=MOCK-EMP-01&page=0&size=1", null);
        assertThat(items.path("meta").path("total").asInt()).isEqualTo(1);
        assertThat(items.path("data").get(0).path("personnel").path("employee_number").asText()).isEqualTo("MOCK-EMP-01");
        assertThat(items.path("data").get(0).path("positions").get(0).path("position_name").asText()).isEqualTo("예시 보직");
        Long target = createLocal("target");
        var created = jdbc.queryForMap("SELECT created_at, personnel_id FROM user_account WHERE account_id=?", target);
        ok(HttpMethod.PATCH, "/api/users/" + target + "/use-status", "{\"use_status\":\"opaque-test-value\"}");
        assertThat(jdbc.queryForObject("SELECT use_status FROM user_account WHERE account_id=?", String.class, target)).isEqualTo("opaque-test-value");
        assertThat(jdbc.queryForObject("SELECT created_at FROM user_account WHERE account_id=?", java.sql.Timestamp.class, target)).isEqualTo(created.get("created_at"));
        assertThat(ok(HttpMethod.GET, "/api/users?useStatus=opaque-test-value", null).path("data").size()).isEqualTo(1);
        String batch = "{\"assignments\":[{\"role_code\":\"R01\",\"approver_id\":" + adminId + ",\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\",\"assignment_source\":\"opaque-test-source\"}]}";
        ok(HttpMethod.PUT, "/api/users/" + target + "/roles", batch);
        var roles = ok(HttpMethod.GET, "/api/user-roles?userId=" + target, null).path("data");
        assertThat(roles.size()).isEqualTo(1);
        assertThat(roles.get(0).path("approver_id").asLong()).isEqualTo(adminId);
        assertThat(roles.get(0).path("assignment_source").asText()).isEqualTo("opaque-test-source");
        assertThat(roles.get(0).path("valid_start").asText()).isEqualTo("2026-01-01");
        assertThat(call(HttpMethod.PATCH, "/api/users/" + target + "/use-status", "{\"person_name\":\"overwrite\",\"use_status\":\"wrong\"}", session).getStatusCode().value()).isEqualTo(400);
        assertThat(snapshot("korus_personnel_snapshot")).isEqualTo(source);
        assertThat(snapshot("organization_user_mapping")).isEqualTo(positions);
    }
}
