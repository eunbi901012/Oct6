package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class UsersContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("searchUsers", "updateUserUseStatus", "updateUserRoles"); }

    @Test void compositeUsesExactFilterAndReadOnlySourceFields() throws Exception {
        mvc.perform(get("/api/users").param("personName", "합성 사용자").param("employeeNumber", "EMP")
            .param("page", "0").param("size", "1").cookie(new Cookie("CMSSESSION", "valid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.total").value(1))
            .andExpect(jsonPath("$.data[0].personnel.person_name").value("합성 사용자"))
            .andExpect(jsonPath("$.data[0].positions[0].position_name").value("합성 보직"))
            .andExpect(jsonPath("$.data[0].account.use_status").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data[0].roles[0].role_code").value("R09"));
    }

    @Test void emptyAssignmentsAndSourceWriteRejected() throws Exception {
        mvc.perform(put("/api/users/1/roles").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"assignments\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("assignments"));
        mvc.perform(patch("/api/users/1/use-status").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"use_status\":\"opaque\",\"person_name\":\"overwrite\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("person_name"));
    }

    @Test void missingAccountReturnsContract400() throws Exception {
        mvc.perform(patch("/api/users/999/use-status").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"use_status\":\"opaque\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("account_id"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested record does not exist"));
    }
}
