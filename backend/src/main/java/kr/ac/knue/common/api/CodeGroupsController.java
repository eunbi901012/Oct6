package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.application.CodeGroupsService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Protects local code-group information, without exposing source-organization or detailed-code edits. */
@RestController

@MenuAccess("SCR-CODE-GROUPS")
public class CodeGroupsController {
    private final CodeGroupsService service;

    /** Binds local group persistence. */
    public CodeGroupsController(CodeGroupsService service) {
        this.service = service;
    }

    /** Lists stored groups using explicit pagination only. */
    @GetMapping("/api/code-groups")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return Responses.list(service.list(), Input.query(query, ""));
    }

    /** Validates only editable group fields before registration. */
    @PostMapping("/api/code-groups")
    public Map<String, Object> create(@RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.create(Input.object(body, Resource.GROUPS.input()), actor));
    }

    /** Changes the unambiguously resolved group while retaining numeric code references. */
    @PatchMapping("/api/code-groups/{groupId}")
    public Map<String, Object> update(@PathVariable String groupId, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.update(groupId, Input.object(body, Resource.GROUPS.input()), actor));
    }
}
