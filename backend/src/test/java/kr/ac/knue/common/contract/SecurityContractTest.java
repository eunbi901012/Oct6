package kr.ac.knue.common.contract;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import kr.ac.knue.common.application.Resource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class SecurityContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("listRoles", "getCurrentUser", "getMenuStructure"); }

    @Test void ambiguousMatchingPermissionsAreApprovalPendingNotUnionOrDenyPrecedence() throws Exception {
        decisions = new ArrayList<>(decisions);
        decisions.add(row("MenuPermission", Map.of("permission_id", 99L, "menu_id", 4L, "account_id", 1L, "access_allowed", false)));
        mvc.perform(get("/api/roles").cookie(new Cookie("CMSSESSION", "valid")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OQ-003")));
        var me = mvc.perform(get("/api/auth/me").cookie(new Cookie("CMSSESSION", "valid")))
            .andExpect(status().isOk()).andReturn();
        assertThat(me.getResponse().getContentAsString()).doesNotContain("\"screen_id\":\"SCR-ROLES\"");
    }

    @Test void ancestorsIncludedInNavigationDoNotGrantTheStructureManagementOperation() throws Exception {
        decisions = decisions.stream().filter(row -> !Long.valueOf(7L).equals(row.get("menu_id"))).toList();
        var me = mvc.perform(get("/api/auth/me").cookie(new Cookie("CMSSESSION", "valid")))
            .andExpect(status().isOk()).andReturn();
        var menus = json.readTree(me.getResponse().getContentAsString()).path("data").path("allowedMenus");
        assertThat(menus.toString()).contains("\"menu_id\":1").doesNotContain("SCR-MENU-STRUCTURE");
        mvc.perform(get("/api/menu-structure").cookie(new Cookie("CMSSESSION", "valid"))).andExpect(status().isForbidden());
    }

    @Test void readonlySourceHasNoManagementMutationRouteAndNullableInitialRoleIsNotInactive() throws Exception {
        mvc.perform(get("/api/roles").cookie(new Cookie("CMSSESSION", "valid"))).andExpect(status().isOk());
        assertThat(rows.get(Resource.USER_ROLES).get(0).get("assignment_status")).isNull();
        mvc.perform(patch("/api/users/1/use-status").cookie(new Cookie("CMSSESSION", "valid")).contentType("application/json").content("{\"person_name\":\"overwrite\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("person_name"));
        assertThat(rows.get(Resource.PERSONNEL).get(0).get("person_name")).isEqualTo("합성 사용자");
    }
}
