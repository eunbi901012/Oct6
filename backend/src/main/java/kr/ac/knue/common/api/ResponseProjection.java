package kr.ac.knue.common.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.ac.knue.common.api.dto.UserSearchItem;
import kr.ac.knue.common.application.Resource;

/**
 * Copies response rows at the HTTP boundary, omitting only optional, nonnullable null properties.
 * Required properties, nullable containers, mapper state and arbitrary JSON attribute keys remain untouched.
 */
public final class ResponseProjection {
    private ResponseProjection() {
    }

    /** Projects an explicitly identified entity or collection; never guesses a schema from arbitrary map keys. */
    public static Object entity(Resource resource, Object data) {
        if (data instanceof List<?> rows) {
            return rows.stream().map(row -> entity(resource, row)).toList();
        }
        if (!(data instanceof Map<?, ?> source)) {
            return data;
        }
        Set<String> optional = switch (resource) {
            case ACCOUNTS -> Set.of("personnel_id");
            case PERMISSIONS -> Set.of("permission_id", "role_code", "organization_id", "account_id");
            case PERSONNEL -> Set.of("personnel_id");
            case POSITIONS -> Set.of("mapping_id", "personnel_id", "organization_id");
            default -> Set.of();
        };
        Map<String, Object> response = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String property = (String) key;
            if (value != null || !optional.contains(property)) {
                response.put(property, value);
            }
        });
        return response;
    }

    /** Projects only known composite DTOs; generic maps and additional_attributes are not traversed. */
    public static Object composite(Object data) {
        if (data instanceof List<?> rows) {
            return rows.stream().map(ResponseProjection::composite).toList();
        }
        if (data instanceof UserSearchItem item) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("account", entity(Resource.ACCOUNTS, item.account()));
            response.put("personnel", entity(Resource.PERSONNEL, item.personnel()));
            response.put("positions", entity(Resource.POSITIONS, item.positions()));
            response.put("roles", entity(Resource.USER_ROLES, item.roles()));
            return response;
        }
        return data;
    }
}
