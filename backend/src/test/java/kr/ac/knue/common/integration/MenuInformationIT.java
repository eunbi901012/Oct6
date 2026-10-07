package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class MenuInformationIT extends PostgreSqlHttpSupport {
    @Test void createUpdateExecutionInformationPreservesStructureAndNeverCreatesPermission() throws Exception {
        var permissions = snapshot("menu_permission");
        var created = ok(HttpMethod.POST, "/api/menu-information", "{\"menu_name\":\"합성 실행정보\",\"screen_id\":\"FIXTURE-ONLY\",\"url\":\"/admin/fixture\",\"icon\":\"folder\",\"business_category\":\"관리\",\"description\":\"설명\"}").path("data");
        long id = created.path("menu_id").asLong();
        var before = jdbc.queryForMap("SELECT * FROM menu WHERE menu_id=?", id);
        ok(HttpMethod.PATCH, "/api/menu-information/" + id, "{\"menu_name\":\"수정 이름\",\"url\":\"/admin/fixture-updated\",\"description\":\"수정 설명\"}");
        var after = jdbc.queryForMap("SELECT * FROM menu WHERE menu_id=?", id);
        assertThat(after.get("menu_name")).isEqualTo("수정 이름");
        assertThat(after.get("url")).isEqualTo("/admin/fixture-updated");
        assertThat(after.get("description")).isEqualTo("수정 설명");
        for (String key : new String[]{"menu_id", "parent_menu_id", "display_order", "created_at"}) assertThat(after.get(key)).as(key).isEqualTo(before.get(key));
        assertThat(ok(HttpMethod.GET, "/api/menu-information", null).path("data").toString()).contains("수정 이름", "/admin/fixture-updated");
        assertThat(snapshot("menu_permission")).isEqualTo(permissions);
    }
}
