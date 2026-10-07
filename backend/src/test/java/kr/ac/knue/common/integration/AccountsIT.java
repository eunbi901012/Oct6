package kr.ac.knue.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

public class AccountsIT extends PostgreSqlHttpSupport {
    @Test void unlinkedInternalAccountBcryptCreateUpdateAndDuplicateRollbackDoNotAlterPersonnel() throws Exception {
        var source = snapshot("korus_personnel_snapshot");
        Long id = createLocal("account-persistence");
        var before = jdbc.queryForMap("SELECT * FROM user_account WHERE account_id=?", id);
        String hash = (String) before.get("password_hash");
        assertThat(hash).isNotEqualTo("fixture-secret");
        assertThat(new BCryptPasswordEncoder().matches("fixture-secret", hash)).isTrue();
        ok(HttpMethod.PATCH, "/api/accounts/" + id, "{\"login_id\":\"account-renamed\"}");
        var after = jdbc.queryForMap("SELECT * FROM user_account WHERE account_id=?", id);
        assertThat(after.get("account_id")).isEqualTo(id);
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("password_hash")).isEqualTo(hash);
        assertThat(after.get("login_id")).isEqualTo("account-renamed");
        var conflict = call(HttpMethod.PATCH, "/api/accounts/" + id, "{\"login_id\":\"admin\"}", session);
        assertThat(conflict.getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForMap("SELECT * FROM user_account WHERE account_id=?", id)).isEqualTo(after);
        assertThat(call(HttpMethod.PATCH, "/api/accounts/" + id, "{\"password\":\"unapproved-reset\"}", session).getStatusCode().value()).isEqualTo(400);
        assertThat(snapshot("korus_personnel_snapshot")).isEqualTo(source);
    }

    @Test void personnelConnectionPolicyGateDoesNotWriteAndUnauthenticatedOrForbiddenNeverMutates() throws Exception {
        var accounts = snapshot("user_account");
        Long personnel = jdbc.queryForObject("SELECT personnel_id FROM korus_personnel_snapshot WHERE employee_number='MOCK-EMP-01'", Long.class);
        var gate = call(HttpMethod.POST, "/api/accounts", "{\"login_id\":\"gated\",\"personnel_id\":" + personnel + "}", session);
        assertThat(gate.getStatusCode().value()).isEqualTo(400);
        assertThat(gate.getBody()).contains("OQ-006");
        assertThat(call(HttpMethod.POST, "/api/accounts", "{\"login_id\":\"unauthorized\"}", null).getStatusCode().value()).isEqualTo(401);
        assertThat(snapshot("user_account")).isEqualTo(accounts);
        Long denied = createLocal("denied");
        String ordinary = call(HttpMethod.POST, "/api/auth/login", "{\"login_id\":\"denied\",\"password\":\"fixture-secret\"}", null)
            .getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE).split(";", 2)[0];
        var afterCreate = snapshot("user_account");
        assertThat(call(HttpMethod.PATCH, "/api/accounts/" + denied, "{\"login_id\":\"not-authorized\"}", ordinary).getStatusCode().value()).isEqualTo(403);
        assertThat(snapshot("user_account")).isEqualTo(afterCreate);
    }
}
