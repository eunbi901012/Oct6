package kr.ac.knue.common.api.dto;

import java.util.Map;

/** Exact transient credential input; never persisted as plaintext or used as an API response. */
public record LoginInput(String login_id, String password) {
    /** Constructs credentials only from the validated JSON input boundary. */
    public static LoginInput from(Map<String, Object> validated) {
        return new LoginInput((String) validated.get("login_id"), (String) validated.get("password"));
    }
}
