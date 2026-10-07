package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class RolesContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("listRoles", "createRole", "updateRole"); }

    @Test void invalidCodeImmutableCodeAndScalarCoercionAreRejected() throws Exception {
        mvc.perform(post("/api/roles").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"role_code\":\"R10\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("role_code"));
        mvc.perform(patch("/api/roles/R09").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"role_code\":\"R01\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/roles/R09").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"role_name\":false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("role_name"));
    }

    @Test void technicalPrimaryKeyConflictHasSafeErrorAndMissingRoleReturns400() throws Exception {
        mvc.perform(post("/api/roles").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"role_code\":\"R09\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Database internal"))));
        mvc.perform(patch("/api/roles/R07").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"role_name\":\"missing\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("role_code"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested record does not exist"));
    }
}
