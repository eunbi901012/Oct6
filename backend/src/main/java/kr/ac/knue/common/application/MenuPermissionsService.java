package kr.ac.knue.common.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores single-subject menu decisions atomically; effective conflict composition remains approval-dependent. */
@Service
public class MenuPermissionsService {
    private final ManagementService management;

    /** Binds local permission persistence. */
    public MenuPermissionsService(ManagementService management) {
        this.management = management;
    }

    /** Reads stored permission subjects and decisions without applying unapproved precedence. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Map<String, Object> filters) {
        return management.list(Resource.PERMISSIONS, filters);
    }

    /** Upserts explicit single-subject decisions and rejects ambiguous stored targets without arbitrary selection. */
    @Transactional
    public List<Map<String, Object>> save(List<Map<String, Object>> permissions, CurrentPrincipal actor) {
        java.util.Set<Map<String, Object>> submitted = new java.util.HashSet<>();
        for (var input : permissions) {
            List<String> targets = java.util.stream.Stream.of("role_code", "organization_id", "account_id")
                .filter(input::containsKey).toList();
            if (targets.size() != 1 || !input.containsKey("menu_id") || !input.containsKey("access_allowed")) {
                throw ApiException.pending("OQ-003: unspecified or combined permission subject/decision");
            }
            var target = Map.<String, Object>of(
                "menu_id", input.get("menu_id"), targets.get(0), input.get(targets.get(0)));
            if (!submitted.add(target)) {
                throw ApiException.pending("OQ-003: repeated permission target has no approved batch precedence");
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var input : permissions) {
            List<String> targets = java.util.stream.Stream.of("role_code", "organization_id", "account_id")
                .filter(input::containsKey).toList();
            if (targets.size() != 1 || !input.containsKey("menu_id") || !input.containsKey("access_allowed")) {
                throw new ApiException(400, "Specify one permission subject, menu and access decision", "permissions");
            }
            var existing = list(Map.of("menu_id", input.get("menu_id"), targets.get(0), input.get(targets.get(0))))
                .stream().filter(row -> java.util.stream.Stream.of("role_code", "organization_id", "account_id")
                    .allMatch(key -> Objects.equals(row.get(key), input.get(key)))).toList();
            if (existing.size() > 1) {
                throw ApiException.pending("OQ-003: ambiguous stored permission target");
            }
            if (existing.isEmpty()) {
                result.add(management.create(Resource.PERMISSIONS, input, actor));
            } else {
                result.add(management.update(Resource.PERMISSIONS, existing.get(0).get("permission_id"),
                    Map.of("access_allowed", input.get("access_allowed")), actor));
            }
        }
        return result;
    }
}
