package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class DetailCodesIT extends PostgreSqlHttpSupport {
    @Test void jsonbParentAndDatesPersistAndRenamePreservesSurrogateAndChildReference() throws Exception {
        var group = ok(HttpMethod.POST, "/api/code-groups", "{\"group_id\":\"JSON-GROUP\"}").path("data").path("code_group_id").asLong();
        var parent = ok(HttpMethod.POST, "/api/detail-codes", "{\"code_group_id\":" + group + ",\"code_value\":\"PARENT\",\"code_name\":\"부모\",\"display_order\":1,\"additional_attributes\":{\"mapping\":\"external-code\"},\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\"}").path("data");
        long id = parent.path("detail_code_id").asLong();
        var child = ok(HttpMethod.POST, "/api/detail-codes", "{\"code_group_id\":" + group + ",\"code_value\":\"CHILD\",\"parent_detail_code_id\":" + id + "}").path("data").path("detail_code_id").asLong();
        var before = jdbc.queryForMap("SELECT * FROM detail_code WHERE detail_code_id=?", id);
        ok(HttpMethod.PATCH, "/api/detail-codes/" + id, "{\"code_value\":\"RENAMED\",\"code_name\":\"새 이름\",\"additional_attributes\":{\"mapping\":\"new-code\",\"nested\":{\"flag\":true}}}");
        var after = jdbc.queryForMap("SELECT * FROM detail_code WHERE detail_code_id=?", id);
        assertThat(after.get("detail_code_id")).isEqualTo(before.get("detail_code_id"));
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("valid_start").toString()).isEqualTo("2026-01-01");
        assertThat(jdbc.queryForObject("SELECT additional_attributes->>'mapping' FROM detail_code WHERE detail_code_id=?", String.class, id)).isEqualTo("new-code");
        assertThat(jdbc.queryForObject("SELECT parent_detail_code_id FROM detail_code WHERE detail_code_id=?", Long.class, child)).isEqualTo(id);
        var read = ok(HttpMethod.GET, "/api/detail-codes?groupId=JSON-GROUP", null).path("data");
        assertThat(read.toString()).contains("RENAMED", "new-code", "\"flag\":true");
    }

    @Test void invalidForeignKeyAndJsonTypeLeaveCodesAndGroupsUnchanged() throws Exception {
        var before = snapshot("detail_code");
        var groups = snapshot("code_group");
        assertThat(call(HttpMethod.POST, "/api/detail-codes", "{\"code_group_id\":9223372036854775807,\"code_value\":\"bad-reference\"}", session).getStatusCode().value()).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/api/detail-codes", "{\"additional_attributes\":[]}", session).getStatusCode().value()).isEqualTo(400);
        assertThat(snapshot("detail_code")).isEqualTo(before);
        assertThat(snapshot("code_group")).isEqualTo(groups);
    }
}
