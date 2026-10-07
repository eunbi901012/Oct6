package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.application.DetailCodesService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Protects local detailed-code fields and JSON mappings, without calling external integration services. */
@RestController

@MenuAccess("SCR-DETAIL-CODES")
public class DetailCodesController {
    private final DetailCodesService service;

    /** Binds local detailed-code persistence. */
    public DetailCodesController(DetailCodesService service) {
        this.service = service;
    }

    /** Validates explicit group/pagination filters and returns the matching stored codes. */
    @GetMapping("/api/detail-codes")
    public Map<String, Object> list(@RequestParam Map<String, String> raw) {
        var query = Input.query(raw, "groupId:string");
        return Responses.list(service.list((String) query.get("groupId")), query);
    }

    /** Registers declared code fields; group information and server metadata are not accepted. */
    @PostMapping("/api/detail-codes")
    public Map<String, Object> create(@RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.create(Input.object(body, Resource.CODES.input()), actor));
    }

    /** Updates explicit code fields while preserving identity and related child references. */
    @PatchMapping("/api/detail-codes/{detailCodeId}")
    public Map<String, Object> update(@PathVariable Long detailCodeId, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.update(detailCodeId, Input.object(body, Resource.CODES.input()), actor));
    }
}
