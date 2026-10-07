package kr.ac.knue.common.domain.ports;

import kr.ac.knue.common.domain.CurrentPrincipal;

/** Replaceable credential/session boundary; callers cannot infer status, expiry or authorization policy. */
public interface AuthenticationPort {
    /** Verifies credentials and returns the persisted account identity; invalid credentials must fail. */
    Long verify(String loginId, String password);

    /** Creates an opaque persisted session only for a verified account. */
    String createSession(Long accountId);

    /** Resolves a valid session into its account and stored roles or rejects authentication. */
    CurrentPrincipal authenticate(String sessionId);

    /** Ends the selected credential without changing account or role business data. */
    void terminate(String sessionId);
}
