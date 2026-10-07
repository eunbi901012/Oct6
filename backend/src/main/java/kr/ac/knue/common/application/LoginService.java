package kr.ac.knue.common.application;

import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.domain.CurrentPrincipal;
import kr.ac.knue.common.domain.ports.AuthenticationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates credential verification, persisted sessions and permission-filtered navigation. */
@Service
public class LoginService {
    private final AuthenticationPort authentication;
    private final MenuPolicy menuPolicy;

    /** Binds replaceable authentication and the shared menu policy. */
    public LoginService(AuthenticationPort authentication, MenuPolicy menuPolicy) {
        this.authentication = authentication;
        this.menuPolicy = menuPolicy;
    }

    /** Creates a session only after valid credentials; lifetime and account-state effects are not guessed. */
    @Transactional
    public String login(String loginId, String password) {
        if (loginId.isBlank() || password.isBlank()) {
            throw new ApiException(400, "Credential must not be blank", loginId.isBlank() ? "login_id" : "password");
        }
        return authentication.createSession(authentication.verify(loginId, password));
    }

    /** Returns the authenticated identity and allowed navigation, never credentials or raw session identifiers. */
    @Transactional(readOnly = true)
    public kr.ac.knue.common.api.dto.CurrentUser me(CurrentPrincipal principal) {
        return new kr.ac.knue.common.api.dto.CurrentUser(principal.accountId(), principal.loginId(),
            principal.roles(), menuPolicy.allowedMenus(principal));
    }

    /** Ends only the current credential, leaving business account and role history intact. */
    @Transactional
    public void logout(String sessionId) {
        authentication.terminate(sessionId);
    }
}
