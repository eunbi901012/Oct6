package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import kr.ac.knue.common.application.OrganizationsService;
import org.springframework.web.bind.annotation.*;

/** Exposes protected source reads; local relationship writes remain OQ-001 approval-dependent. */
@RestController

@MenuAccess("SCR-ORGANIZATIONS")
public class OrganizationsController {
    private static final String RELATIONSHIP = "parent_organization_id:long effective_start:date effective_end:date";
    private final OrganizationsService service;

    /** Binds read-only organization and approval-gated relationship services. */
    public OrganizationsController(OrganizationsService service) {
        this.service = service;
    }

    /** Reads explicitly filtered organizations without source mutation or inferred period behavior. */
    @GetMapping("/api/organizations")
    public Map<String, Object> list(@RequestParam Map<String, String> raw) {
        var query = Input.query(raw, "organizationCode:string organizationId:long");
        var rows = service.list((String) query.get("organizationCode"), (Long) query.get("organizationId"));
        return Responses.list(rows, query);
    }

    /** Validates editable relationship input, then fails closed until source/local ownership is approved. */
    @PostMapping("/api/organizations")
    public Map<String, Object> create(@RequestBody JsonNode body) {
        service.create(Input.object(body, "organization_code:string " + RELATIONSHIP));
        throw new IllegalStateException("Organization policy gate must not permit source writes");
    }

    /** Validates relationship-only changes without enabling unapproved source or history writes. */
    @PatchMapping("/api/organizations/{organizationId}")
    public Map<String, Object> update(@PathVariable Long organizationId, @RequestBody JsonNode body) {
        service.update(organizationId, Input.object(body, "organization_code:string " + RELATIONSHIP));
        throw new IllegalStateException("Organization policy gate must not permit source writes");
    }
}
