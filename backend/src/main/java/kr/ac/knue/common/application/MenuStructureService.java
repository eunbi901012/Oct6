package kr.ac.knue.common.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits local menu parent/order fields atomically while preserving execution information and permission references. */
@Service
public class MenuStructureService {
    private final ManagementService management;

    /** Binds local menu persistence. */
    public MenuStructureService(ManagementService management) {
        this.management = management;
    }

    /** Reads the full hierarchy with deterministic sibling ordering, without changing permission decisions. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        Comparator<Map<String, Object>> comparator = Comparator
            .comparing((Map<String, Object> row) -> number(row.get("parent_menu_id")),
                Comparator.nullsFirst(Long::compareTo))
            .thenComparing(row -> number(row.get("display_order")), Comparator.nullsLast(Long::compareTo))
            .thenComparing(row -> number(row.get("menu_id")));
        return management.list(Resource.MENUS, Map.of()).stream().sorted(comparator).toList();
    }

    /** Changes only the requested parent after checking target references and tree integrity. */
    @Transactional
    public Map<String, Object> parent(Long id, Map<String, Object> values, CurrentPrincipal actor) {
        management.get(Resource.MENUS, id);
        Object parent = values.get("parent_menu_id");
        Set<Object> visited = new HashSet<>();
        visited.add(id);
        while (parent != null) {
            if (!visited.add(parent)) {
                throw new ApiException(400, "Parent would create an invalid menu tree", "parent_menu_id");
            }
            parent = management.get(Resource.MENUS, parent).get("parent_menu_id");
        }
        return management.update(Resource.MENUS, id, values, actor);
    }

    /** Validates the submitted sibling set before atomically updating only display order. */
    @Transactional
    public List<Map<String, Object>> order(Long parent, List<Map<String, Object>> items, CurrentPrincipal actor) {
        Set<Object> ids = new HashSet<>();
        Long siblingParent = parent;
        boolean inferred = parent != null;
        for (var item : items) {
            if (!item.containsKey("menu_id") || !item.containsKey("display_order")) {
                throw new ApiException(400, "Each item must identify menu and order", "items");
            }
            if (!ids.add(item.get("menu_id"))) {
                throw new ApiException(400, "Repeated menu cannot define a unique order", "items");
            }
            var row = management.get(Resource.MENUS, item.get("menu_id"));
            if (!inferred) {
                siblingParent = number(row.get("parent_menu_id"));
                inferred = true;
            }
            if (!Objects.equals(siblingParent, number(row.get("parent_menu_id")))) {
                throw new ApiException(400, "Order operation is limited to the submitted siblings", "items");
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var item : items) {
            result.add(management.update(Resource.MENUS, item.get("menu_id"),
                Map.of("display_order", item.get("display_order")), actor));
        }
        return result;
    }

    private Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
