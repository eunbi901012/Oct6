package kr.ac.knue.common.integration;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

public class SchemaIT extends PostgreSqlHttpSupport {
    @Autowired RequestMappingHandlerMapping routes;

    @Test void exactSchemaFlywayIdentityNullableStatesAndNoActionForeignKeys() throws Exception {
        assertThat(new ClassPathResource("schema.sql").getContentAsByteArray())
            .isEqualTo(new ClassPathResource("db/migration/V1__foundation.sql").getContentAsByteArray());
        var tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'", String.class);
        assertThat(tables).containsExactlyInAnyOrder("user_account", "korus_personnel_snapshot", "organization", "organization_user_mapping",
            "role", "user_role", "menu", "menu_permission", "code_group", "detail_code", "session");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('created_at','updated_at') AND table_name<>'flyway_schema_history' AND is_nullable='NO' AND data_type='timestamp with time zone'", Integer.class)).isEqualTo(22);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND is_identity='YES'", Integer.class)).isEqualTo(9);
        var states = jdbc.queryForList("SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('use_status','assignment_status','session_status')");
        assertThat(states).isNotEmpty().allSatisfy(row -> {
            assertThat(row.get("data_type")).isEqualTo("text");
            assertThat(row.get("is_nullable")).isEqualTo("YES");
            assertThat(row.get("column_default")).isNull();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype='f' AND (confdeltype<>'a' OR confupdtype<>'a')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success=true", Integer.class)).isEqualTo(2);
    }

    @Test void exactly33ApprovedOperationsAreRegisteredAndHealthReadsRealDatabase() throws Exception {
        var yaml = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        com.fasterxml.jackson.databind.JsonNode contract;
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) { contract = yaml.readTree(input); }
        Set<String> expected = new java.util.HashSet<>();
        contract.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (method.getValue().has("operationId")) expected.add(method.getKey().toUpperCase() + " " + path.getKey());
        }));
        Set<String> actual = new java.util.HashSet<>();
        routes.getHandlerMethods().forEach((mapping, handler) -> {
            for (String path : mapping.getPatternValues()) {
                if (path.startsWith("/api/")) mapping.getMethodsCondition().getMethods().forEach(method -> actual.add(method.name() + " " + path));
            }
        });
        assertThat(expected).hasSize(33);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        var before = snapshot("menu");
        var response = call(HttpMethod.GET, "/api/health", null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(response.getBody()).path("data").path("healthy").asBoolean()).isTrue();
        assertThat(snapshot("menu")).isEqualTo(before);
    }
}
