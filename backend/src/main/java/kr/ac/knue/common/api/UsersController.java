package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.application.UsersService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Exposes authenticated user composites and local-only edits behind the persisted SCR-USERS gate. */
@RestController

@MenuAccess("SCR-USERS")
public class UsersController {
    private final UsersService service;

    /** Binds source-read and local-edit orchestration. */
    public UsersController(UsersService service) {
        this.service = service;
    }

    /** Validates explicit filters and returns paginated read-only composite records. */
    @GetMapping("/api/users")
    public Map<String, Object> search(@RequestParam Map<String, String> raw) {
        var query = Input.query(raw, "employeeNumber:string personName:string organizationId:long jobGrade:string "
            + "employmentStatus:string roleCode:role useStatus:string");
        return Responses.list(service.search(query), query);
    }

    /** Edits only account use status; source personnel fields are not accepted. */
    @PatchMapping("/api/users/{userId}/use-status")
    public Map<String, Object> status(@PathVariable Long userId, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, "use_status:string");
        return Responses.success(service.status(userId, values, actor), Resource.ACCOUNTS);
    }

    /** Validates every assignment before an atomic local-role change, preserving explicit approval. */
    @PutMapping("/api/users/{userId}/roles")
    public Map<String, Object> roles(@PathVariable Long userId, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, "assignments:array", "assignments");
        var assignments = BatchInput.rows(
            (JsonNode) values.get("assignments"), "assignments", Resource.USER_ROLES.input(), true);
        return Responses.success(service.roles(userId, assignments, actor));
    }
}
