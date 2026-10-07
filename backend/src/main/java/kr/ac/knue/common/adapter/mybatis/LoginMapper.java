package kr.ac.knue.common.adapter.mybatis;

import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** Restricts credential/session SQL to the authentication adapter, separate from business response projections. */
public interface LoginMapper {
    /** Reads a private verifier for credential checking; never returns it through an API DTO. */
    Map<String, Object> credentials(@Param("loginId") String loginId);

    /** Resolves a persisted credential and account state without guessing expiry/status effects. */
    Map<String, Object> sessionAccount(@Param("sessionId") String sessionId);

    /** Persists a random session credential with database-owned timestamps. */
    int createSession(@Param("sessionId") String sessionId, @Param("accountId") Long accountId);

    /** Removes only the session credential, not role or account history. */
    int deleteSession(@Param("sessionId") String sessionId);
}
