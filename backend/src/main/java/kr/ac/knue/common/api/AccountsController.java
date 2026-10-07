package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.application.AccountsService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

/** Exposes protected local account writes without source-personnel or password-reset endpoints. */
@RestController

@MenuAccess("SCR-USERS")
public class AccountsController {
    private final AccountsService service;

    /** Binds local credential and account services. */
    public AccountsController(AccountsService service) {
        this.service = service;
    }

    /** Validates editable account fields and returns a verifier-free account response. */
    @PostMapping("/api/accounts")
    public Map<String, Object> create(@RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, Resource.ACCOUNTS.input() + " password:string");
        return Responses.success(service.create(values, actor), Resource.ACCOUNTS);
    }

    /** Changes only declared local account fields, retaining credential and personnel-linking boundaries. */
    @PatchMapping("/api/accounts/{accountId}")
    public Map<String, Object> update(@PathVariable Long accountId, @RequestBody JsonNode body,
            @RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        var values = Input.object(body, Resource.ACCOUNTS.input());
        return Responses.success(service.update(accountId, values, actor), Resource.ACCOUNTS);
    }
}
