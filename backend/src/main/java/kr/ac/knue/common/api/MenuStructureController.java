package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import kr.ac.knue.common.application.MenuStructureService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Owns the protected hierarchy API, separate from execution-information and permission editing. */
@RestController

@MenuAccess("SCR-MENU-STRUCTURE")
public class MenuStructureController {
    private final MenuStructureService service;

    /** Binds hierarchy persistence. */
    public MenuStructureController(MenuStructureService service) {
        this.service = service;
    }

    /** Reads the full management hierarchy, not an implicit permission-filtered navigation grant. */
    @GetMapping("/api/menu-structure")
    public Map<String, Object> list() {
        return Responses.success(service.list());
    }

    /** Accepts only parent changes and leaves execution fields and permission references intact. */
    @PatchMapping("/api/menu-structure/{menuId}/parent")
    public Map<String, Object> parent(@PathVariable Long menuId, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.parent(menuId, Input.object(body, "parent_menu_id:long"), actor));
    }

    /** Validates sibling ordering input before any transactional hierarchy update. */
    @PutMapping("/api/menu-structure/order")
    public Map<String, Object> order(@RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, "parent_menu_id:long items:array", "items");
        var items = BatchInput.rows((JsonNode) values.get("items"), "items", "menu_id:long display_order:int", false);
        return Responses.success(service.order((Long) values.get("parent_menu_id"), items, actor));
    }
}
