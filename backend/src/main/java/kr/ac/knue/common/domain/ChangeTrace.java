package kr.ac.knue.common.domain;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Conceptual context only. No audit repository, retention policy or secret values are invented. */
public record ChangeTrace(String resource, Object identifier, Map<String, Object> before_values,
        Map<String, Object> after_values, Long actor_id, OffsetDateTime processed_at, String reason) {
    /** Snapshots change maps defensively; this context does not imply an approved audit storage policy. */
    public ChangeTrace {
        before_values = Collections.unmodifiableMap(new LinkedHashMap<>(before_values));
        after_values = Collections.unmodifiableMap(new LinkedHashMap<>(after_values));
    }
}
