package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.application.RolesService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Protects local R01 through R09 definitions with the persisted role-management menu gate. */
@RestController

@MenuAccess("SCR-ROLES")
public class RolesController {
    private final RolesService service;

    /** Binds local role-definition services. */
    public RolesController(RolesService service) {
        this.service = service;
    }

    /** Reads definitions with explicit pagination and no assignment side effects. */
    @GetMapping("/api/roles")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return Responses.list(service.list(), Input.query(query, ""));
    }

    /** Validates a closed role code and editable definition fields before persistence. */
    @PostMapping("/api/roles")
    public Map<String, Object> create(@RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, "role_code:role " + Resource.ROLES.input());
        return Responses.success(service.create(values, actor));
    }

    /** Edits definition information without allowing a role-code change or automatic permission grants. */
    @PatchMapping("/api/roles/{roleCode}")
    public Map<String, Object> update(@PathVariable String roleCode, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, Resource.ROLES.input());
        return Responses.success(service.update(roleCode, values, actor));
    }
}
