package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.application.MenuInformationService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Protects execution-information editing, separate from hierarchy and permission APIs. */
@RestController

@MenuAccess("SCR-MENU-INFORMATION")
public class MenuInformationController {
    private final MenuInformationService service;

    /** Binds execution-information persistence. */
    public MenuInformationController(MenuInformationService service) {
        this.service = service;
    }

    /** Reads menu execution fields without redirects or implied screen creation. */
    @GetMapping("/api/menu-information")
    public Map<String, Object> list(@RequestParam Map<String, String> raw) {
        return Responses.list(service.list(), Input.query(raw, ""));
    }

    /** Registers explicit execution information only; hierarchy and permission fields are rejected. */
    @PostMapping("/api/menu-information")
    public Map<String, Object> create(@RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.create(Input.object(body, Resource.MENUS.input()), actor));
    }

    /** Changes execution fields while preserving hierarchy and permission references. */
    @PatchMapping("/api/menu-information/{menuId}")
    public Map<String, Object> update(@PathVariable Long menuId, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.update(menuId, Input.object(body, Resource.MENUS.input()), actor));
    }
}
