package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.application.UserRolesService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Exposes protected role assignments with explicit approval; unresolved revocation stays fail-closed. */
@RestController

@MenuAccess("SCR-USER-ROLES")
public class UserRolesController {
    private final UserRolesService service;

    /** Binds local role transactions. */
    public UserRolesController(UserRolesService service) {
        this.service = service;
    }

    /** Validates the optional account filter without inventing current-period/status semantics. */
    @GetMapping("/api/user-roles")
    public Map<String, Object> list(@RequestParam Map<String, String> raw) {
        var query = Input.query(raw, "userId:long");
        Map<String, Object> filters = new LinkedHashMap<>();
        if (query.containsKey("userId")) {
            filters.put("account_id", query.get("userId"));
        }

        return Responses.list(service.list(filters), query);
    }

    /** Validates all submitted role records before any grant and never substitutes the actor as approver. */
    @PostMapping("/api/user-roles")
    public Map<String, Object> grant(@RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, "account_id:long assignments:array", "account_id", "assignments");
        var assignments = BatchInput.rows(
            (JsonNode) values.get("assignments"), "assignments", Resource.USER_ROLES.input(), true);
        return Responses.success(service.grant((Long) values.get("account_id"), assignments, actor));
    }

    /** Changes only assignment fields, preserving the account linkage and server metadata. */
    @PatchMapping("/api/user-roles/{assignmentId}")
    public Map<String, Object> change(@PathVariable Long assignmentId, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.change(assignmentId, Input.object(body, Resource.USER_ROLES.input()), actor));
    }

    /** Validates revocation input but retains OQ-DATA-004 before undefined state/history writes. */
    @DeleteMapping("/api/user-roles/{assignmentId}")
    public Map<String, Object> revoke(@PathVariable Long assignmentId, @RequestBody JsonNode body) {
        service.revoke(assignmentId, Input.object(body, "approver_id:long valid_start:date valid_end:date"));
        throw new IllegalStateException("Revocation policy gate must not permit undefined state writes");
    }
}
