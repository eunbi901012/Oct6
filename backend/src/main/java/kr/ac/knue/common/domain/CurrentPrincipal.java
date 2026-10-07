package kr.ac.knue.common.domain;

import java.util.List;

/** Authenticated persisted identity and role codes only; no password verifier or session credential is carried. */
public record CurrentPrincipal(Long accountId, String loginId, List<String> roles) {
}
