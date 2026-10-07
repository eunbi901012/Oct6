package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;

public class OrganizationsIT extends PostgreSqlHttpSupport {
    @Test void organizationReadAndBothWriteApprovalGatesPreserveOriginalRowsAndNoInventedHistory() throws Exception {
        var before = snapshot("organization");
        var organization = ok(HttpMethod.GET, "/api/organizations?organizationCode=MOCK-ORG-01", null).path("data").get(0);
        Long id = organization.path("organization_id").asLong();
        var create = call(HttpMethod.POST, "/api/organizations", "{\"organization_code\":\"LOCAL\",\"effective_start\":\"2026-01-01\"}", session);
        assertThat(create.getStatusCode().value()).isEqualTo(400);
        assertThat(create.getBody()).contains("OQ-001");
        var update = call(HttpMethod.PATCH, "/api/organizations/" + id, "{\"effective_end\":\"2026-12-31\"}", session);
        assertThat(update.getStatusCode().value()).isEqualTo(400);
        assertThat(update.getBody()).contains("OQ-001");
        assertThat(snapshot("organization")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.organization_relationship_history')::text", String.class)).isNull();
    }
}
