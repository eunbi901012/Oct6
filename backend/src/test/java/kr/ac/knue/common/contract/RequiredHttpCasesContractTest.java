package kr.ac.knue.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;

/** Executes the contract's concrete invalid-field and cross-boundary HTTP examples, not invented DTO fields. */
public class RequiredHttpCasesContractTest extends ContractMvcSupport {
    @Override
    protected Set<String> operations() {
        return Set.of("createDetailCode", "updateDetailCode");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource({"validationCases", "businessBoundaryCases"})
    void rejectsConcreteContractFieldAndPreservesAllStoredRows(HttpCase sample) throws Exception {
        String before = json.writeValueAsString(rows);
        var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(sample.method()), sample.path())
            .cookie(new Cookie("CMSSESSION", "valid"));
        if (sample.body() != null) {
            builder.contentType("application/json").content(sample.body());
        }
        mvc.perform(builder)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value(sample.field()))
            .andExpect(jsonPath("$.fieldErrors[0].message")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())));
        assertThat(json.writeValueAsString(rows)).isEqualTo(before);
        verify(mapper, never()).insert(any(), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    static Stream<HttpCase> validationCases() {
        return Stream.of(
            invalid("GET", "/api/users?page=text", null, "page"),
            invalid("GET", "/api/organizations?page=text", null, "page"),
            invalid("GET", "/api/roles?size=0", null, "size"),
            invalid("GET", "/api/user-roles?userId=text", null, "userId"),
            invalid("GET", "/api/menu-information?page=-1", null, "page"),
            invalid("GET", "/api/menu-permissions?roleCode=R10", null, "roleCode"),
            invalid("GET", "/api/code-groups?size=text", null, "size"),
            invalid("GET", "/api/detail-codes?page=-1", null, "page"),
            invalid("POST", "/api/accounts", "{\"personnel_id\":[]}", "personnel_id"),
            invalid("PATCH", "/api/accounts/1", "{\"login_id\":{}}", "login_id"),
            invalid("PATCH", "/api/users/1/use-status", "{\"use_status\":{}}", "use_status"),
            invalid("PUT", "/api/users/1/roles", "{\"assignments\":[]}", "assignments"),
            invalid("POST", "/api/organizations", "{\"parent_organization_id\":{}}", "parent_organization_id"),
            invalid("PATCH", "/api/organizations/1", "{\"effective_start\":[]}", "effective_start"),
            invalid("POST", "/api/roles", "{\"assignment_criteria\":{}}", "assignment_criteria"),
            invalid("PATCH", "/api/roles/R09", "{\"role_name\":[]}", "role_name"),
            invalid("POST", "/api/user-roles", "{\"account_id\":{}}", "account_id"),
            invalid("PATCH", "/api/user-roles/2", "{\"approver_id\":[]}", "approver_id"),
            invalid("DELETE", "/api/user-roles/2", "{\"valid_end\":{}}", "valid_end"),
            invalid("PATCH", "/api/menu-structure/3/parent", "{\"parent_menu_id\":[]}", "parent_menu_id"),
            invalid("PUT", "/api/menu-structure/order", "{\"items\":\"invalid\"}", "items"),
            invalid("POST", "/api/menu-information", "{\"screen_id\":{}}", "screen_id"),
            invalid("PATCH", "/api/menu-information/2", "{\"url\":[]}", "url"),
            invalid("PUT", "/api/menu-permissions",
                "{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R09\",\"access_allowed\":\"true\"}]}",
                "permissions[0].access_allowed"),
            invalid("POST", "/api/code-groups", "{\"managing_organization_id\":{}}", "managing_organization_id"),
            invalid("PATCH", "/api/code-groups/GROUP", "{\"group_name\":[]}", "group_name"),
            invalid("POST", "/api/detail-codes", "{\"additional_attributes\":[]}", "additional_attributes"),
            invalid("PATCH", "/api/detail-codes/1", "{\"parent_detail_code_id\":{}}", "parent_detail_code_id")
        );
    }

    static Stream<HttpCase> businessBoundaryCases() {
        return Stream.of(
            invalid("POST", "/api/accounts", "{\"employee_number\":\"source\"}", "employee_number"),
            invalid("PATCH", "/api/accounts/1", "{\"person_name\":\"source\"}", "person_name"),
            invalid("PATCH", "/api/users/1/use-status", "{\"person_name\":\"source\"}", "person_name"),
            invalid("POST", "/api/organizations", "{\"organization_name\":\"source\"}", "organization_name"),
            invalid("PATCH", "/api/organizations/1", "{\"organization_type\":\"source\"}", "organization_type"),
            invalid("POST", "/api/roles", "{\"role_code\":\"R10\"}", "role_code"),
            invalid("PATCH", "/api/roles/R09", "{\"role_code\":\"R01\"}", "role_code"),
            invalid("POST", "/api/user-roles",
                "{\"account_id\":2,\"assignments\":[{\"role_code\":\"R10\"}]}", "assignments[0].role_code"),
            invalid("PATCH", "/api/user-roles/2", "{\"role_code\":\"R10\",\"approver_id\":1}", "role_code"),
            invalid("DELETE", "/api/user-roles/2", "{\"account_id\":1,\"approver_id\":1}", "account_id"),
            invalid("PUT", "/api/users/1/roles", "{\"job_grade\":\"source\"}", "job_grade"),
            invalid("PATCH", "/api/menu-structure/3/parent", "{\"menu_name\":\"execution\"}", "menu_name"),
            invalid("PUT", "/api/menu-structure/order",
                "{\"items\":[{\"menu_id\":3,\"display_order\":1,\"parent_menu_id\":2}]}", "items[0].parent_menu_id"),
            invalid("POST", "/api/menu-information", "{\"parent_menu_id\":1}", "parent_menu_id"),
            invalid("PATCH", "/api/menu-information/2", "{\"display_order\":1}", "display_order"),
            invalid("PUT", "/api/menu-permissions",
                "{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R09\",\"access_allowed\":true,\"url\":\"/x\"}]}",
                "permissions[0].url"),
            invalid("POST", "/api/code-groups", "{\"code_value\":\"detail\"}", "code_value"),
            invalid("PATCH", "/api/code-groups/GROUP", "{\"code_name\":\"detail\"}", "code_name"),
            invalid("POST", "/api/detail-codes", "{\"group_name\":\"group\"}", "group_name"),
            invalid("PATCH", "/api/detail-codes/1",
                "{\"managing_organization_id\":1}", "managing_organization_id")
        );
    }

    private static HttpCase invalid(String method, String path, String body, String field) {
        return new HttpCase(method, path, body, field);
    }

    record HttpCase(String method, String path, String body, String field) {
    }
}
