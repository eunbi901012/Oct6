package kr.ac.knue.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import kr.ac.knue.common.api.ResponseProjection;
import kr.ac.knue.common.application.Resource;
import org.junit.jupiter.api.Test;

/** HTTP regressions for contract status alignment and non-mutating, schema-bounded response projection. */
public class ResponseBoundaryContractTest extends ContractMvcSupport {
    @Override
    protected Set<String> operations() {
        return Set.of("listMenuPermissions", "createAccount", "searchUsers", "listDetailCodes", "getMenuStructure");
    }

    @Test
    void missingMutationTargetReturnsContractBadRequestAndFieldErrorWithoutWrite() throws Exception {
        mvc.perform(patch("/api/detail-codes/999").cookie(session()).contentType("application/json")
                .content("{\"code_name\":\"missing\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("detail_code_id"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested record does not exist"));
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void missingBusinessGroupReturnsContractBadRequestWithoutWrite() throws Exception {
        mvc.perform(get("/api/detail-codes").cookie(session()).param("groupId", "MISSING")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("groupId"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested code group does not exist"));
        verify(mapper, never()).insert(any(), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void absentOptionalPermissionTargetsAreOmittedButRequiredAndNullableFieldsRemain() throws Exception {
        var result = mvc.perform(get("/api/menu-permissions").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].role_code").value("R09"))
            .andExpect(jsonPath("$.data[0].permission_id").value(1))
            .andExpect(jsonPath("$.data[0].access_allowed").value(true)).andReturn();
        var permission = json.readTree(result.getResponse().getContentAsString()).path("data").get(0);
        assertThat(permission.has("organization_id")).isFalse();
        assertThat(permission.has("account_id")).isFalse();
        assertThat(permission.has("use_status")).isTrue();
        assertThat(permission.path("use_status").isNull()).isTrue();
        // MyBatis/policy input remains presence-preserving; projection must not mutate it.
        assertThat(rows.get(Resource.PERMISSIONS).get(0)).containsKeys("organization_id", "account_id");
    }

    @Test
    void createdUnlinkedAccountOmitsOnlyOptionalPersonnelReference() throws Exception {
        var result = mvc.perform(post("/api/accounts").cookie(session()).contentType("application/json")
                .content("{\"login_id\":\"boundary-local\",\"password\":\"secret\"}"))
            .andExpect(status().isOk()).andReturn();
        var account = json.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(account.has("personnel_id")).isFalse();
        assertThat(account.has("use_status")).isTrue();
        assertThat(account.path("use_status").isNull()).isTrue();
        assertThat(account.has("created_at")).isTrue();
    }

    @Test
    void organizationPermissionResponseOmitsAbsentRoleAndAccountButRetainsSubject() throws Exception {
        var result = mvc.perform(put("/api/menu-permissions").cookie(session()).contentType("application/json")
                .content("{\"permissions\":[{\"menu_id\":2,\"organization_id\":1,\"access_allowed\":true}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].organization_id").value(1))
            .andExpect(jsonPath("$.data[0].menu_id").value(2))
            .andExpect(jsonPath("$.data[0].access_allowed").value(true)).andReturn();
        var permission = json.readTree(result.getResponse().getContentAsString()).path("data").get(0);
        assertThat(permission.has("role_code")).isFalse();
        assertThat(permission.has("account_id")).isFalse();
        assertThat(permission.has("created_at")).isTrue();
        assertThat(permission.has("updated_at")).isTrue();
        var stored = rows.get(Resource.PERMISSIONS).get(rows.get(Resource.PERMISSIONS).size() - 1);
        assertThat(stored).containsEntry("role_code", null).containsEntry("account_id", null);
    }

    @Test
    void nestedUserRowsProjectOptionalReferencesWithoutRemovingNullableContainers() throws Exception {
        rows.get(Resource.POSITIONS).get(0).put("organization_id", null);
        var result = mvc.perform(get("/api/users").cookie(session()).param("page", "0").param("size", "10"))
            .andExpect(status().isOk()).andReturn();
        var data = json.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.get(0).path("positions").get(0).has("organization_id")).isFalse();
        assertThat(data.get(1).has("personnel")).isTrue();
        assertThat(data.get(1).path("personnel").isNull()).isTrue();
        assertThat(data.get(1).path("account").has("personnel_id")).isFalse();
        assertThat(rows.get(Resource.POSITIONS).get(0)).containsKey("organization_id");
    }

    @Test
    void requiredNonnullableFieldsAndArbitraryAttributesAreNeverSilentlyRemoved() throws Exception {
        rows.get(Resource.CODES).get(0).put("additional_attributes",
            "{\"personnel_id\":null,\"organization_id\":null,\"nested\":{\"role_code\":null}}");
        var result = mvc.perform(get("/api/detail-codes").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andReturn();
        var attributes = json.readTree(result.getResponse().getContentAsString())
            .path("data").get(0).path("additional_attributes");
        assertThat(attributes.has("personnel_id")).isTrue();
        assertThat(attributes.path("nested").has("role_code")).isTrue();
        var menus = mvc.perform(get("/api/menu-structure").cookie(session()))
            .andExpect(status().isOk()).andReturn();
        var root = json.readTree(menus.getResponse().getContentAsString()).path("data").get(0);
        // The checked-in contract currently requires screen_id: this is not an optional-field repair.
        assertThat(root.has("screen_id")).isTrue();
        assertThat(root.path("screen_id").isNull()).isTrue();
    }

    @Test
    void projectionAllowlistExactlyMatchesOptionalNonnullableFieldsInCopiedContract() {
        Map<Resource, String> schemas = Map.of(
            Resource.ACCOUNTS, "UserAccount", Resource.PERMISSIONS, "MenuPermission",
            Resource.MENUS, "Menu", Resource.USER_ROLES, "UserRole", Resource.GROUPS, "CodeGroup",
            Resource.CODES, "DetailCode", Resource.ORGANIZATIONS, "Organization",
            Resource.PERSONNEL, "KorusPersonnelSnapshot", Resource.POSITIONS, "OrganizationUserMapping",
            Resource.ROLES, "Role");
        schemas.forEach((resource, name) -> {
            var schema = contract.path("components").path("schemas").path(name);
            Map<String, Object> source = new LinkedHashMap<>();
            schema.path("properties").fieldNames().forEachRemaining(property -> source.put(property, null));
            var projected = (Map<?, ?>) ResponseProjection.entity(resource, source);
            schema.path("properties").fields().forEachRemaining(property -> {
                boolean required = false;
                for (var key : schema.path("required")) {
                    required |= key.asText().equals(property.getKey());
                }
                boolean retained = required || property.getValue().path("nullable").asBoolean(false);
                assertThat(projected.containsKey(property.getKey()))
                    .as(name + "." + property.getKey()).isEqualTo(retained);
            });
            assertThat(source).hasSize(schema.path("properties").size());
        });
    }

    private Cookie session() {
        return new Cookie("CMSSESSION", "valid");
    }
}
