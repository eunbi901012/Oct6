package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.application.MenuPermissionsService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Exposes persisted menu decisions behind SCR-MENU-PERMISSIONS without inferring composition priority. */
@RestController

@MenuAccess("SCR-MENU-PERMISSIONS")
public class MenuPermissionsController {
    private final MenuPermissionsService service;

    /** Binds atomic permission persistence. */
    public MenuPermissionsController(MenuPermissionsService service) {
        this.service = service;
    }

    /** Validates subject filters and projects only optional nonnullable null targets at the response boundary. */
    @GetMapping("/api/menu-permissions")
    public Map<String, Object> list(@RequestParam Map<String, String> raw) {
        var query = Input.query(raw, "roleCode:role organizationId:long userId:long menuId:long");
        Map<String, Object> filters = new LinkedHashMap<>();
        Map.of("roleCode", "role_code", "organizationId", "organization_id",
            "userId", "account_id", "menuId", "menu_id")
            .forEach((wire, column) -> {
                if (query.containsKey(wire)) {
                    filters.put(column, query.get(wire));
                }
            });
        return Responses.list(service.list(filters), query, Resource.PERMISSIONS);
    }

    /** Validates the whole batch before persisting single-subject menu decisions. */
    @PutMapping("/api/menu-permissions")
    public Map<String, Object> save(@RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, "permissions:array", "permissions");
        var rows = BatchInput.rows(
            (JsonNode) values.get("permissions"), "permissions", Resource.PERMISSIONS.input(), false);
        return Responses.success(service.save(rows, actor), Resource.PERMISSIONS);
    }
}
