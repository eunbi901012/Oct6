package kr.ac.knue.common.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.ac.knue.common.adapter.mybatis.ManagementMapper;
import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.stereotype.Service;

/** Shares fail-closed menu decisions between navigation and HTTP access, without inventing conflict precedence. */
@Service
public class MenuPolicy {
    private final ManagementMapper mapper;

    /** Binds persisted roles, hierarchy and applicable permission records. */
    public MenuPolicy(ManagementMapper mapper) {
        this.mapper = mapper;
    }

    /** Requires the declared role and an unambiguous persisted menu decision; R09 has no bypass. */
    public void require(CurrentPrincipal principal, String screen, String... requiredRoles) {
        if (principal == null) {
            throw new ApiException(401, "Authentication required", null);
        }
        if (java.util.Arrays.stream(requiredRoles).noneMatch(principal.roles()::contains)) {
            throw new ApiException(403, "Role does not permit this operation", null);
        }
        var assignments = mapper.list(Resource.USER_ROLES, Map.of("account_id", principal.accountId()));
        for (String role : principal.roles()) {
            var definition = mapper.get(Resource.ROLES, role);
            if (definition == null || definition.get("use_status") != null) {
                throw new ApiException(403, "Pending approval: OQ-DATA-004 role status effect", null);
            }
        }
        for (var row : assignments) {
            if (row.get("assignment_status") != null || row.get("valid_start") != null
                    || row.get("valid_end") != null) {
                throw new ApiException(403, "Pending approval: OQ-005/OQ-DATA-004 role effect", null);
            }
        }
        var menus = mapper.list(Resource.MENUS, Map.of());
        var candidates = menus.stream().filter(row -> screen.equals(row.get("screen_id"))).toList();
        if (candidates.size() != 1) {
            throw new ApiException(403, "Pending approval: OQ-003 ambiguous or absent screen mapping", null);
        }
        Decision decision = decision(candidates.get(0), menus, mapper.applicablePermissions(principal.accountId()));
        if (decision != Decision.ALLOWED) {
            throw new ApiException(403, decision == Decision.PENDING
                ? "Pending approval: OQ-003 permission composition" : "Menu access not permitted", null);
        }
    }

    /** Includes permitted leaves and structural ancestors only; ancestry inclusion is not an API grant. */
    public List<Map<String, Object>> allowedMenus(CurrentPrincipal principal) {
        var menus = mapper.list(Resource.MENUS, Map.of());
        var permissions = mapper.applicablePermissions(principal.accountId());
        Set<Object> ids = new HashSet<>();
        Map<Object, Map<String, Object>> index = index(menus);
        for (var menu : menus) {
            if (menu.get("screen_id") == null || !principal.roles().contains("R09")) {
                continue;
            }
            try {
                require(principal, (String) menu.get("screen_id"), "R09");
            } catch (ApiException exception) {
                continue;
            }
            if (decision(menu, menus, permissions) == Decision.ALLOWED) {
                Map<String, Object> cursor = menu;
                Set<Object> visited = new HashSet<>();
                while (cursor != null && visited.add(cursor.get("menu_id"))) {
                    ids.add(cursor.get("menu_id"));
                    cursor = index.get(cursor.get("parent_menu_id"));
                }
            }
        }
        return menus.stream().filter(row -> ids.contains(row.get("menu_id"))).toList();
    }

    private Decision decision(Map<String, Object> menu, List<Map<String, Object>> menus,
            List<Map<String, Object>> permissions) {
        Map<Object, Map<String, Object>> index = index(menus);
        Set<Object> visited = new HashSet<>();
        Map<String, Object> cursor = menu;
        Boolean unanimous = null;
        while (cursor != null) {
            Object id = cursor.get("menu_id");
            if (!visited.add(id) || cursor.get("use_status") != null) {
                return Decision.PENDING;
            }
            var matching = permissions.stream().filter(row -> Objects.equals(id, row.get("menu_id"))).toList();
            if (matching.size() > 1) {
                return Decision.PENDING;
            }
            if (matching.isEmpty()) {
                return Decision.DENIED;
            }
            var permission = matching.get(0);
            if (permission.get("organization_id") != null) {
                return Decision.PENDING; // OQ-004: effective organization/data-scope authorization is unapproved.
            }
            long targets = java.util.stream.Stream.of("role_code", "organization_id", "account_id")
                .filter(key -> permission.get(key) != null).count();
            if (targets != 1 || permission.get("access_allowed") == null || permission.get("use_status") != null) {
                return Decision.PENDING;
            }
            Boolean explicit = (Boolean) permission.get("access_allowed");
            if (unanimous != null && !unanimous.equals(explicit)) {
                return Decision.PENDING;
            }
            unanimous = explicit;
            Object parent = cursor.get("parent_menu_id");
            cursor = index.get(parent);
            if (parent != null && cursor == null) {
                return Decision.PENDING;
            }
        }
        return Boolean.TRUE.equals(unanimous) ? Decision.ALLOWED : Decision.DENIED;
    }

    private Map<Object, Map<String, Object>> index(List<Map<String, Object>> rows) {
        Map<Object, Map<String, Object>> index = new HashMap<>();
        rows.forEach(row -> index.put(row.get("menu_id"), row));
        return index;
    }

    /** Separates explicit access from unresolved policy rather than guessing a composition rule. */
    private enum Decision {
        ALLOWED, DENIED, PENDING
    }
}
