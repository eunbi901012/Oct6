package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class UserRolesIT extends PostgreSqlHttpSupport {
    @Test void grantChangeReadbackExplicitApproverSourceAndPendingRevokePreserveHistory() throws Exception {
        Long target = createLocal("assignment-target");
        Long approver = createLocal("explicit-approver");
        String grant = "{\"account_id\":" + target + ",\"assignments\":[{\"role_code\":\"R01\",\"approver_id\":" + approver + ",\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\",\"assignment_source\":\"opaque-source\"}]}";
        var granted = ok(HttpMethod.POST, "/api/user-roles", grant).path("data").get(0);
        long id = granted.path("assignment_id").asLong();
        assertThat(granted.path("approver_id").asLong()).isEqualTo(approver).isNotEqualTo(adminId);
        var before = jdbc.queryForMap("SELECT * FROM user_role WHERE assignment_id=?", id);
        ok(HttpMethod.PATCH, "/api/user-roles/" + id, "{\"role_code\":\"R02\",\"approver_id\":" + approver + ",\"valid_start\":\"2026-02-01\",\"valid_end\":\"2026-10-31\",\"assignment_source\":\"changed-source\"}");
        var after = jdbc.queryForMap("SELECT * FROM user_role WHERE assignment_id=?", id);
        assertThat(after.get("account_id")).isEqualTo(target);
        assertThat(after.get("assignment_id")).isEqualTo(before.get("assignment_id"));
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("role_code")).isEqualTo("R02");
        assertThat(after.get("approver_id")).isEqualTo(approver);
        assertThat(after.get("assignment_source")).isEqualTo("changed-source");
        assertThat(after.get("valid_start").toString()).isEqualTo("2026-02-01");
        assertThat(after.get("valid_end").toString()).isEqualTo("2026-10-31");
        assertThat(ok(HttpMethod.GET, "/api/user-roles?userId=" + target, null).path("data").get(0).path("role_code").asText()).isEqualTo("R02");
        var revoke = call(HttpMethod.DELETE, "/api/user-roles/" + id,
            "{\"approver_id\":" + approver + ",\"valid_start\":\"2026-02-01\",\"valid_end\":\"2026-10-31\"}", session);
        assertThat(revoke.getStatusCode().value()).isEqualTo(400);
        assertThat(revoke.getBody()).contains("OQ-DATA-004");
        assertThat(jdbc.queryForMap("SELECT * FROM user_role WHERE assignment_id=?", id)).isEqualTo(after);
    }

    @Test void secondForeignKeyFailureRollsBackTheWholeGrantBatch() throws Exception {
        Long target = createLocal("rollback-target");
        var source = snapshot("korus_personnel_snapshot");
        var roles = snapshot("user_role");
        String grant = "{\"account_id\":" + target + ",\"assignments\":[{\"role_code\":\"R01\",\"approver_id\":" + adminId + "},{\"role_code\":\"R02\",\"approver_id\":9223372036854775807}]}";
        var response = call(HttpMethod.POST, "/api/user-roles", grant, session);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).doesNotContain("SQLException", "constraint", "jdbc", "password");
        assertThat(snapshot("user_role")).isEqualTo(roles);
        assertThat(snapshot("korus_personnel_snapshot")).isEqualTo(source);
    }

    @Test void replacementRequiringRevocationIsPendingBeforeAnyMappingWrite() throws Exception {
        Long target = createLocal("replace-target");
        ok(HttpMethod.POST, "/api/user-roles", "{\"account_id\":" + target + ",\"assignments\":[{\"role_code\":\"R01\",\"approver_id\":" + adminId + "}]}");
        var before = snapshot("user_role");
        var response = call(HttpMethod.PUT, "/api/users/" + target + "/roles", "{\"assignments\":[{\"role_code\":\"R02\",\"approver_id\":" + adminId + "}]}", session);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("OQ-DATA-004");
        assertThat(snapshot("user_role")).isEqualTo(before);
    }
}
