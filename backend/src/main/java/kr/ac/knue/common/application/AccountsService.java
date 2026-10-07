package kr.ac.knue.common.application;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns local credentials and account edits; personnel linking and password-reset policy are not inferred. */
@Service
public class AccountsService {
    private final ManagementService management;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Binds local account persistence and the credential hashing boundary. */
    public AccountsService(ManagementService management) {
        this.management = management;
    }

    /** Creates an unlinked local account, storing only a password verifier and never exposing its hash. */
    @Transactional
    public Map<String, Object> create(Map<String, Object> input, CurrentPrincipal actor) {
        if (input.containsKey("personnel_id")) {
            throw ApiException.pending("OQ-006: personnel connection policy");
        }
        Map<String, Object> values = new LinkedHashMap<>(input);
        String password = (String) values.remove("password");
        if (password != null) {
            if (password.isBlank()) {
                throw new ApiException(400, "Password must not be blank", "password");
            }
            try {
                values.put("password_hash", encoder.encode(password));
            } catch (IllegalArgumentException exception) {
                throw new ApiException(
                    400, "Password cannot be encoded by the configured credential verifier", "password");
            }
        }
        return management.create(Resource.ACCOUNTS, values, actor);
    }

    /** Updates approved non-password fields while preserving OQ-006 personnel-linking gates. */
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> values, CurrentPrincipal actor) {
        if (values.containsKey("personnel_id")) {
            throw ApiException.pending("OQ-006: personnel connection policy");
        }
        return management.update(Resource.ACCOUNTS, id, values, actor);
    }
}
