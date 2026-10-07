package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class UserRolesContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("listUserRoles", "grantUserRoles", "changeUserRole", "revokeUserRole"); }

    @Test void invalidNestedRoleFieldAndExplicitApproval() throws Exception {
        mvc.perform(post("/api/user-roles").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"account_id\":2,\"assignments\":[{\"role_code\":\"R10\"}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("assignments[0].role_code"));
        mvc.perform(patch("/api/user-roles/2").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"role_code\":\"R09\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("approver_id"));
    }

    @Test void changeStoresSourceAndDatesWithoutChangingAccountOrAssignment() throws Exception {
        mvc.perform(patch("/api/user-roles/2").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content(body("changeUserRole")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignment_id").value(2))
            .andExpect(jsonPath("$.data.account_id").value(2))
            .andExpect(jsonPath("$.data.approver_id").value(1))
            .andExpect(jsonPath("$.data.valid_start").value("2026-02-01"))
            .andExpect(jsonPath("$.data.valid_end").value("2026-10-01"))
            .andExpect(jsonPath("$.data.assignment_source").value("opaque-fixture-value"));
    }

    @Test void missingAssignmentReturns400AndRevokeCannotRetargetAccount() throws Exception {
        mvc.perform(patch("/api/user-roles/999").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"approver_id\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("assignment_id"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested record does not exist"));
        mvc.perform(delete("/api/user-roles/2").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"account_id\":1,\"approver_id\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("account_id"));
    }
}
