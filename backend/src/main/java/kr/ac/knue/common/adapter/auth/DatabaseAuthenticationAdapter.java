package kr.ac.knue.common.adapter.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import kr.ac.knue.common.adapter.mybatis.LoginMapper;
import kr.ac.knue.common.adapter.mybatis.ManagementMapper;
import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.domain.CurrentPrincipal;
import kr.ac.knue.common.domain.ports.AuthenticationPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Verifies BCrypt credentials and persisted random sessions; unresolved state/lifetime effects remain gated. */
@Component
public class DatabaseAuthenticationAdapter implements AuthenticationPort {
    private final LoginMapper loginMapper;
    private final ManagementMapper mapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    /** Binds private credential SQL and public role projections. */
    public DatabaseAuthenticationAdapter(LoginMapper loginMapper, ManagementMapper mapper) {
        this.loginMapper = loginMapper;
        this.mapper = mapper;
    }

    /** Rejects incorrect verifiers without session creation and blocks unapproved account-state effects. */
    @Override
    public Long verify(String loginId, String password) {
        Map<String, Object> account = loginMapper.credentials(loginId);
        boolean matches = false;
        if (account != null && account.get("password_hash") instanceof String hash) {
            try {
                matches = encoder.matches(password, hash);
            } catch (IllegalArgumentException ignored) {
                matches = false;
            }
        }
        if (!matches) {
            throw new ApiException(401, "Invalid credentials", null);
        }
        if (account.get("use_status") != null) {
            throw ApiException.pending("OQ-006: account status authentication effect");
        }
        return ((Number) account.get("account_id")).longValue();
    }

    /** Persists a cryptographically random credential; no unapproved lifetime or status is invented. */
    @Override
    public String createSession(Long accountId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        loginMapper.createSession(sessionId, accountId);
        return sessionId;
    }

    /** Resolves only a valid persisted credential and stored roles, failing closed on unresolved states. */
    @Override
    public CurrentPrincipal authenticate(String sessionId) {
        Map<String, Object> account = sessionId == null ? null : loginMapper.sessionAccount(sessionId);
        if (account == null) {
            throw new ApiException(401, "Authentication required", null);
        }
        if (account.get("session_status") != null || account.get("use_status") != null) {
            throw ApiException.pending("OQ-006/OQ-DATA-004: session and account status effect");
        }
        Long id = ((Number) account.get("account_id")).longValue();
        var roles = mapper.list(Resource.USER_ROLES, Map.of("account_id", id)).stream()
            .map(row -> (String) row.get("role_code"))
            .filter(java.util.Objects::nonNull)
            .distinct().toList();
        return new CurrentPrincipal(id, (String) account.get("login_id"), roles);
    }

    /** Deletes the ephemeral credential while preserving business account and assignment rows. */
    @Override
    public void terminate(String sessionId) {
        // Delete only the ephemeral credential, not business role/account history. No invented status code.
        loginMapper.deleteSession(sessionId);
    }
}
