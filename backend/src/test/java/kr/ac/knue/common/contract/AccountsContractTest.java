package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class AccountsContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("createAccount", "updateAccount"); }

    @Test void noPasswordResetAndUnapprovedSourceConnection() throws Exception {
        mvc.perform(patch("/api/accounts/1").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"password\":\"reset\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        mvc.perform(post("/api/accounts").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"login_id\":\"fixture\",\"password\":\"test\",\"personnel_id\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OQ-006")));
        mvc.perform(patch("/api/accounts/999").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"login_id\":\"missing\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("account_id"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested record does not exist"));
    }
}
