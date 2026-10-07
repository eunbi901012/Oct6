package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class CodeGroupsIT extends PostgreSqlHttpSupport {
    @Test void groupInformationRenamePreservesSurrogateAndExistingDetailCodeForeignKeys() throws Exception {
        var created = ok(HttpMethod.POST, "/api/code-groups", "{\"group_id\":\"HTTP-GROUP\",\"group_name\":\"그룹\",\"description\":\"설명\"}").path("data");
        long id = created.path("code_group_id").asLong();
        var child = ok(HttpMethod.POST, "/api/detail-codes", "{\"code_group_id\":" + id + ",\"code_value\":\"ONE\",\"code_name\":\"일\"}").path("data");
        var before = jdbc.queryForMap("SELECT * FROM code_group WHERE code_group_id=?", id);
        ok(HttpMethod.PATCH, "/api/code-groups/HTTP-GROUP", "{\"group_id\":\"HTTP-RENAMED\",\"group_name\":\"수정 그룹\"}");
        var after = jdbc.queryForMap("SELECT * FROM code_group WHERE code_group_id=?", id);
        assertThat(after.get("code_group_id")).isEqualTo(id);
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("group_id")).isEqualTo("HTTP-RENAMED");
        assertThat(jdbc.queryForObject("SELECT code_group_id FROM detail_code WHERE detail_code_id=?", Long.class, child.path("detail_code_id").asLong())).isEqualTo(id);
        assertThat(ok(HttpMethod.GET, "/api/detail-codes?groupId=HTTP-RENAMED", null).path("data").size()).isEqualTo(1);
        assertThat(ok(HttpMethod.GET, "/api/code-groups", null).path("data").toString()).contains("HTTP-RENAMED");
    }

    @Test void businessGroupIdentifierAmbiguityIsSupported400WithoutArbitraryRowMutation() throws Exception {
        jdbc.update("INSERT INTO code_group(group_id) VALUES ('DUPLICATE'),('DUPLICATE')");
        var before = snapshot("code_group");
        var patch = call(HttpMethod.PATCH, "/api/code-groups/DUPLICATE", "{\"group_name\":\"wrong\"}", session);
        assertThat(patch.getStatusCode().value()).isEqualTo(400);
        assertThat(patch.getBody()).contains("ambiguous groupId");
        assertThat(call(HttpMethod.GET, "/api/detail-codes?groupId=DUPLICATE", null, session).getStatusCode().value()).isEqualTo(400);
        assertThat(snapshot("code_group")).isEqualTo(before);
    }
}
