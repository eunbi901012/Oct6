package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class RolesIT extends PostgreSqlHttpSupport {
    @Test void roleNamePurposeCriteriaAndScopePersistWithUnchangedRoleCodeReferences() throws Exception {
        var assignments = snapshot("user_role");
        var before = jdbc.queryForMap("SELECT * FROM role WHERE role_code='R09'");
        ok(HttpMethod.PATCH, "/api/roles/R09", "{\"role_name\":\"새 관리자명\",\"purpose\":\"새 목적\",\"assignment_criteria\":\"저장 기준\",\"default_data_scope\":\"저장 기본값\"}");
        var after = jdbc.queryForMap("SELECT * FROM role WHERE role_code='R09'");
        assertThat(after.get("role_name")).isEqualTo("새 관리자명");
        assertThat(after.get("purpose")).isEqualTo("새 목적");
        assertThat(after.get("assignment_criteria")).isEqualTo("저장 기준");
        assertThat(after.get("default_data_scope")).isEqualTo("저장 기본값");
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("role_code")).isEqualTo(before.get("role_code"));
        assertThat(snapshot("user_role")).isEqualTo(assignments);
        assertThat(ok(HttpMethod.GET, "/api/roles", null).path("data").toString()).contains("새 관리자명");
    }

    @Test void supportedRoleCreationAndDuplicateConflictRespectOriginalNineCodes() throws Exception {
        jdbc.update("DELETE FROM role WHERE role_code='R08'");
        ok(HttpMethod.POST, "/api/roles", "{\"role_code\":\"R08\",\"role_name\":\"재등록 이름\"}");
        assertThat(jdbc.queryForObject("SELECT role_name FROM role WHERE role_code='R08'", String.class)).isEqualTo("재등록 이름");
        assertThat(call(HttpMethod.POST, "/api/roles", "{\"role_code\":\"R08\"}", session).getStatusCode().value()).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/api/roles", "{\"role_code\":\"R10\"}", session).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role", Integer.class)).isEqualTo(9);
    }
}
