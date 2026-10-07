package kr.ac.knue.common.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import kr.ac.knue.common.CommonApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Runner-owned execution. No test-time schema substitute: exact packaged Flyway migrations.
 * One singleton PostgreSQL 16 container per Failsafe JVM prevents cached-context stale ports. */
@SpringBootTest(classes = CommonApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
public abstract class PostgreSqlHttpSupport {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.8")
        .withDatabaseName("cms_contract").withUsername("cms_test").withPassword("cms_test");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.clean-disabled", () -> false);
        registry.add("cms.cookie-secure", () -> false);
    }

    @Autowired protected TestRestTemplate http;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected Flyway flyway;
    @Autowired protected ObjectMapper json;
    protected String session;
    protected Long adminId;

    @BeforeEach
    void restoreExactLocalFixture() throws Exception {
        flyway.clean();
        flyway.migrate();
        adminId = jdbc.queryForObject("SELECT account_id FROM user_account WHERE login_id='admin'", Long.class);
        var login = call(HttpMethod.POST, "/api/auth/login", "{\"login_id\":\"admin\",\"password\":\"admin\"}", null);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        session = cookie.split(";", 2)[0];
    }

    protected ResponseEntity<String> call(HttpMethod method, String path, String body, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    protected JsonNode ok(HttpMethod method, String path, String body) throws Exception {
        var response = call(method, path, body, session);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        var payload = json.readTree(response.getBody());
        assertThat(payload.path("success").asBoolean()).isTrue();
        assertThat(payload.path("meta").isObject()).isTrue();
        assertThat(response.getBody()).doesNotContain("password_hash", "session_id");
        return payload;
    }

    protected Long menu(String screen) {
        return jdbc.queryForObject("SELECT menu_id FROM menu WHERE screen_id=?", Long.class, screen);
    }

    protected Long createLocal(String login) throws Exception {
        return ok(HttpMethod.POST, "/api/accounts", "{\"login_id\":\"" + login + "\",\"password\":\"fixture-secret\"}")
            .path("data").path("account_id").asLong();
    }

    protected List<java.util.Map<String, Object>> snapshot(String table) {
        // Test-owned fixed identifiers only; no HTTP data reaches this SQL identifier.
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY created_at");
    }
}
