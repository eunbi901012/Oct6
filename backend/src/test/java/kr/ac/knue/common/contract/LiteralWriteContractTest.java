package kr.ac.knue.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.ac.knue.common.application.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Literal HTTP examples for every non-auth write operation, using real controllers, services and policy.
 * The shared mapper fixture records writes; these assertions are not PostgreSQL/FK/transaction proof.
 * Source ownership and undefined revocation policy remain fail-closed, not invented happy paths.
 */
public class LiteralWriteContractTest extends ContractMvcSupport {
    @Override
    protected Set<String> operations() {
        return Set.of("createAccount", "updateAccount", "updateUserUseStatus", "updateUserRoles",
            "createOrganizationRelationship", "updateOrganizationRelationship", "createRole", "updateRole",
            "grantUserRoles", "changeUserRole", "revokeUserRole", "saveMenuPermissions", "changeMenuParent",
            "reorderSiblingMenus", "createMenuInformation", "updateMenuInformation", "createCodeGroup",
            "updateCodeGroup", "createDetailCode", "updateDetailCode");
    }

    @Test
    void createAccountHashesPasswordWithoutSourceLinkOrAutomaticRole() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/accounts").cookie(valid()).contentType("application/json")
            .content("{\"login_id\":\"literal-local\",\"password\":\"literal-secret\"}"))
            .andExpect(success("createAccount"))
            .andExpect(jsonPath("$.data.account_id").value(102))
            .andExpect(jsonPath("$.data.login_id").value("literal-local"))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.password_hash").doesNotExist());
        var inserted = onlyInsert(before, Resource.ACCOUNTS);
        assertThat(inserted).containsEntry("account_id", 102L).containsEntry("login_id", "literal-local");
        assertThat(inserted.get("personnel_id")).isNull();
        assertThat(inserted).doesNotContainKey("password");
        assertThat(new BCryptPasswordEncoder().matches("literal-secret", (String) inserted.get("password_hash")))
            .isTrue();
        verify(mapper).insert(eq(Resource.ACCOUNTS), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void updateAccountPreservesCredentialPersonnelAndOtherAccounts() throws Exception {
        rows.get(Resource.ACCOUNTS).get(0).put("password_hash", "stored-verifier");
        var before = snapshot();
        mvc.perform(patch("/api/accounts/1").cookie(valid()).contentType("application/json")
            .content("{\"login_id\":\"literal-renamed\"}"))
            .andExpect(success("updateAccount"))
            .andExpect(jsonPath("$.data.account_id").value(1))
            .andExpect(jsonPath("$.data.login_id").value("literal-renamed"))
            .andExpect(jsonPath("$.data.personnel_id").value(1))
            .andExpect(jsonPath("$.data.password_hash").doesNotExist());
        onlyUpdates(before, Resource.ACCOUNTS, Map.of(1L, Map.of("login_id", "literal-renamed")));
        verify(mapper).update(Resource.ACCOUNTS, 1L, Map.of("login_id", "literal-renamed"));
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void updateUserUseStatusStoresOpaqueStatusOnly() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/users/1/use-status").cookie(valid()).contentType("application/json")
            .content("{\"use_status\":\"literal-opaque-status\"}"))
            .andExpect(success("updateUserUseStatus"))
            .andExpect(jsonPath("$.data.account_id").value(1))
            .andExpect(jsonPath("$.data.use_status").value("literal-opaque-status"))
            .andExpect(jsonPath("$.data.personnel_id").value(1));
        onlyUpdates(before, Resource.ACCOUNTS, Map.of(1L, Map.of("use_status", "literal-opaque-status")));
        verify(mapper).update(Resource.ACCOUNTS, 1L, Map.of("use_status", "literal-opaque-status"));
        verify(mapper, never()).insert(any(), anyMap());
        verify(authentication, never()).createSession(any());
        verify(authentication, never()).terminate(any());
    }

    @Test
    void updateUserRolesRetainsAssignmentIdentityAndExplicitApprover() throws Exception {
        var before = snapshot();
        mvc.perform(put("/api/users/2/roles").cookie(valid()).contentType("application/json")
            .content("{\"assignments\":[{\"role_code\":\"R01\",\"approver_id\":2,\"valid_start\":\"2026-02-01\",\"valid_end\":\"2026-12-31\"}]}"))
            .andExpect(success("updateUserRoles"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].assignment_id").value(2))
            .andExpect(jsonPath("$.data[0].account_id").value(2))
            .andExpect(jsonPath("$.data[0].role_code").value("R01"))
            .andExpect(jsonPath("$.data[0].approver_id").value(2))
            .andExpect(jsonPath("$.data[0].valid_start").value("2026-02-01"));
        var changes = Map.<String, Object>of("role_code", "R01", "approver_id", 2L,
            "valid_start", LocalDate.parse("2026-02-01"), "valid_end", LocalDate.parse("2026-12-31"));
        onlyUpdates(before, Resource.USER_ROLES, Map.of(2L, changes));
        verify(mapper).update(Resource.USER_ROLES, 2L, changes);
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void createOrganizationRelationshipRemainsPendingWithoutSourceOrHistoryWrites() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/organizations").cookie(valid()).contentType("application/json")
            .content("{\"organization_code\":\"ORG\",\"parent_organization_id\":1,\"effective_start\":\"2026-01-01\"}"))
            .andExpect(failure(400, "policy"))
            .andExpect(jsonPath("$.message").value(
                "Pending approval: OQ-001: organization source/local write boundary"));
        noWrites(before);
    }

    @Test
    void updateOrganizationRelationshipRemainsPendingWithoutSourceOrHistoryWrites() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/organizations/1").cookie(valid()).contentType("application/json")
            .content("{\"parent_organization_id\":1,\"effective_start\":\"2026-02-01\",\"effective_end\":\"2026-12-31\"}"))
            .andExpect(failure(400, "policy"))
            .andExpect(jsonPath("$.message").value(
                "Pending approval: OQ-001: organization source/local write boundary"));
        noWrites(before);
    }

    @Test
    void createRoleStoresDefinitionWithoutGrantsOrPermissionChanges() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/roles").cookie(valid()).contentType("application/json")
            .content("{\"role_code\":\"R08\",\"role_name\":\"Literal audit\",\"purpose\":\"Review\",\"assignment_criteria\":\"Explicit\",\"default_data_scope\":\"Stored description\"}"))
            .andExpect(success("createRole"))
            .andExpect(jsonPath("$.data.role_code").value("R08"))
            .andExpect(jsonPath("$.data.role_name").value("Literal audit"))
            .andExpect(jsonPath("$.data.assignment_criteria").value("Explicit"))
            .andExpect(jsonPath("$.data.default_data_scope").value("Stored description"));
        assertThat(onlyInsert(before, Resource.ROLES)).containsEntry("role_code", "R08")
            .containsEntry("role_name", "Literal audit").containsEntry("purpose", "Review")
            .containsEntry("assignment_criteria", "Explicit").containsEntry("default_data_scope", "Stored description");
        verify(mapper).insert(eq(Resource.ROLES), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void createRoleConflictReturnsSafe400WithoutChangingDefinitionsAssignmentsOrPermissions() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/roles").cookie(valid()).contentType("application/json")
            .content("{\"role_code\":\"R09\",\"role_name\":\"Must not overwrite\"}"))
            .andExpect(failure(400, "reference"))
            .andExpect(jsonPath("$.message").value("Conflicting identifier or invalid reference"));
        assertThat(rows).isEqualTo(before);
        verify(mapper).insert(Resource.ROLES, Map.of("role_code", "R09", "role_name", "Must not overwrite"));
        verify(mapper, never()).update(any(), any(), anyMap());
        verifyNoInteractions(events, personnel);
    }

    @Test
    void updateRolePreservesCodeAssignmentsAndPermissions() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/roles/R01").cookie(valid()).contentType("application/json")
            .content("{\"role_name\":\"Literal role\",\"default_data_scope\":\"Opaque scope\"}"))
            .andExpect(success("updateRole"))
            .andExpect(jsonPath("$.data.role_code").value("R01"))
            .andExpect(jsonPath("$.data.role_name").value("Literal role"))
            .andExpect(jsonPath("$.data.default_data_scope").value("Opaque scope"));
        var changes = Map.<String, Object>of("role_name", "Literal role", "default_data_scope", "Opaque scope");
        onlyUpdates(before, Resource.ROLES, Map.of("R01", changes));
        verify(mapper).update(Resource.ROLES, "R01", changes);
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void grantUserRolesStoresRequestedAccountApproverDatesAndSource() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/user-roles").cookie(valid()).contentType("application/json")
            .content("{\"account_id\":2,\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":2,\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\",\"assignment_source\":\"literal-source\"}]}"))
            .andExpect(success("grantUserRoles"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].assignment_id").value(102))
            .andExpect(jsonPath("$.data[0].account_id").value(2))
            .andExpect(jsonPath("$.data[0].role_code").value("R09"))
            .andExpect(jsonPath("$.data[0].approver_id").value(2))
            .andExpect(jsonPath("$.data[0].valid_end").value("2026-12-31"))
            .andExpect(jsonPath("$.data[0].assignment_source").value("literal-source"));
        assertThat(onlyInsert(before, Resource.USER_ROLES)).containsEntry("account_id", 2L)
            .containsEntry("role_code", "R09").containsEntry("approver_id", 2L)
            .containsEntry("valid_start", LocalDate.parse("2026-01-01"))
            .containsEntry("valid_end", LocalDate.parse("2026-12-31"))
            .containsEntry("assignment_source", "literal-source");
        verify(mapper).insert(eq(Resource.USER_ROLES), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void changeUserRolePreservesAssignmentAccountAndOtherAssignments() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"role_code\":\"R09\",\"approver_id\":1,\"valid_start\":\"2026-02-01\",\"assignment_source\":\"literal-change\"}"))
            .andExpect(success("changeUserRole"))
            .andExpect(jsonPath("$.data.assignment_id").value(2))
            .andExpect(jsonPath("$.data.account_id").value(2))
            .andExpect(jsonPath("$.data.role_code").value("R09"))
            .andExpect(jsonPath("$.data.approver_id").value(1))
            .andExpect(jsonPath("$.data.valid_start").value("2026-02-01"))
            .andExpect(jsonPath("$.data.assignment_source").value("literal-change"));
        var changes = Map.<String, Object>of("role_code", "R09", "approver_id", 1L,
            "valid_start", LocalDate.parse("2026-02-01"), "assignment_source", "literal-change");
        onlyUpdates(before, Resource.USER_ROLES, Map.of(2L, changes));
        verify(mapper).update(Resource.USER_ROLES, 2L, changes);
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void revokeUserRoleRemainsPendingAndPreservesAssignmentAndHistory() throws Exception {
        var before = snapshot();
        mvc.perform(delete("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"approver_id\":1,\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\"}"))
            .andExpect(failure(400, "policy"))
            .andExpect(jsonPath("$.message").value(
                "Pending approval: OQ-DATA-004: role revocation state/preservation policy"));
        noWrites(before);
    }

    @Test
    void saveMenuPermissionsUpsertsDecisionWithoutChangingSubjectsOrMenus() throws Exception {
        var before = snapshot();
        mvc.perform(put("/api/menu-permissions").cookie(valid()).contentType("application/json")
            .content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R09\",\"access_allowed\":false}]}"))
            .andExpect(success("saveMenuPermissions"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].permission_id").value(2))
            .andExpect(jsonPath("$.data[0].menu_id").value(2))
            .andExpect(jsonPath("$.data[0].role_code").value("R09"))
            .andExpect(jsonPath("$.data[0].access_allowed").value(false))
            .andExpect(jsonPath("$.data[0].organization_id").doesNotExist())
            .andExpect(jsonPath("$.data[0].account_id").doesNotExist());
        onlyUpdates(before, Resource.PERMISSIONS, Map.of(2L, Map.of("access_allowed", false)));
        verify(mapper).update(Resource.PERMISSIONS, 2L, Map.of("access_allowed", false));
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void saveMenuPermissionsCreatesOnlyRequestedSingleSubjectMapping() throws Exception {
        var before = snapshot();
        mvc.perform(put("/api/menu-permissions").cookie(valid()).contentType("application/json")
            .content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":true}]}"))
            .andExpect(success("saveMenuPermissions"))
            .andExpect(jsonPath("$.data[0].permission_id").value(110))
            .andExpect(jsonPath("$.data[0].role_code").value("R01"))
            .andExpect(jsonPath("$.data[0].access_allowed").value(true));
        assertThat(onlyInsert(before, Resource.PERMISSIONS)).containsEntry("menu_id", 2L)
            .containsEntry("role_code", "R01").containsEntry("access_allowed", true);
        verify(mapper).insert(eq(Resource.PERMISSIONS), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void changeMenuParentPreservesExecutionOrderAndPermissionReferences() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/menu-structure/3/parent").cookie(valid()).contentType("application/json")
            .content("{\"parent_menu_id\":2}"))
            .andExpect(success("changeMenuParent"))
            .andExpect(jsonPath("$.data.menu_id").value(3))
            .andExpect(jsonPath("$.data.parent_menu_id").value(2))
            .andExpect(jsonPath("$.data.screen_id").value("SCR-ORGANIZATIONS"))
            .andExpect(jsonPath("$.data.url").value("/admin/fixture"))
            .andExpect(jsonPath("$.data.display_order").value(1));
        onlyUpdates(before, Resource.MENUS, Map.of(3L, Map.of("parent_menu_id", 2L)));
        verify(mapper).update(Resource.MENUS, 3L, Map.of("parent_menu_id", 2L));
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void reorderSiblingMenusChangesOnlySubmittedOrders() throws Exception {
        var before = snapshot();
        mvc.perform(put("/api/menu-structure/order").cookie(valid()).contentType("application/json")
            .content("{\"parent_menu_id\":1,\"items\":[{\"menu_id\":2,\"display_order\":9},{\"menu_id\":3,\"display_order\":8}]}"))
            .andExpect(success("reorderSiblingMenus"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].menu_id").value(2))
            .andExpect(jsonPath("$.data[0].display_order").value(9))
            .andExpect(jsonPath("$.data[1].menu_id").value(3))
            .andExpect(jsonPath("$.data[1].display_order").value(8))
            .andExpect(jsonPath("$.data[1].parent_menu_id").value(1));
        onlyUpdates(before, Resource.MENUS,
            Map.of(2L, Map.of("display_order", 9), 3L, Map.of("display_order", 8)));
        verify(mapper).update(Resource.MENUS, 2L, Map.of("display_order", 9));
        verify(mapper).update(Resource.MENUS, 3L, Map.of("display_order", 8));
        verify(mapper, times(2)).update(any(), any(), anyMap());
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void createMenuInformationStoresExecutionWithoutAutomaticParentOrPermission() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/menu-information").cookie(valid()).contentType("application/json")
            .content("{\"menu_name\":\"Literal menu\",\"screen_id\":\"LITERAL-SCREEN\",\"url\":\"/admin/literal\",\"icon\":\"folder\",\"description\":\"Stored only\"}"))
            .andExpect(success("createMenuInformation"))
            .andExpect(jsonPath("$.data.menu_id").value(110))
            .andExpect(jsonPath("$.data.screen_id").value("LITERAL-SCREEN"))
            .andExpect(jsonPath("$.data.url").value("/admin/literal"));
        var inserted = onlyInsert(before, Resource.MENUS);
        assertThat(inserted).containsEntry("menu_name", "Literal menu").containsEntry("screen_id", "LITERAL-SCREEN")
            .containsEntry("url", "/admin/literal").containsEntry("icon", "folder")
            .containsEntry("description", "Stored only");
        assertThat(inserted.get("parent_menu_id")).isNull();
        assertThat(inserted.get("display_order")).isNull();
        verify(mapper).insert(eq(Resource.MENUS), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void updateMenuInformationPreservesHierarchyOrderScreenAndPermissions() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/menu-information/2").cookie(valid()).contentType("application/json")
            .content("{\"menu_name\":\"Literal renamed menu\",\"url\":\"/admin/literal-renamed\"}"))
            .andExpect(success("updateMenuInformation"))
            .andExpect(jsonPath("$.data.menu_id").value(2))
            .andExpect(jsonPath("$.data.menu_name").value("Literal renamed menu"))
            .andExpect(jsonPath("$.data.url").value("/admin/literal-renamed"))
            .andExpect(jsonPath("$.data.screen_id").value("SCR-USERS"))
            .andExpect(jsonPath("$.data.parent_menu_id").value(1))
            .andExpect(jsonPath("$.data.display_order").value(0));
        var changes = Map.<String, Object>of("menu_name", "Literal renamed menu", "url", "/admin/literal-renamed");
        onlyUpdates(before, Resource.MENUS, Map.of(2L, changes));
        verify(mapper).update(Resource.MENUS, 2L, changes);
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void createCodeGroupStoresOrganizationReferenceWithoutChangingSourceOrCodes() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/code-groups").cookie(valid()).contentType("application/json")
            .content("{\"group_id\":\"LITERAL\",\"group_name\":\"Literal group\",\"managing_organization_id\":1}"))
            .andExpect(success("createCodeGroup"))
            .andExpect(jsonPath("$.data.code_group_id").value(101))
            .andExpect(jsonPath("$.data.group_id").value("LITERAL"))
            .andExpect(jsonPath("$.data.group_name").value("Literal group"))
            .andExpect(jsonPath("$.data.managing_organization_id").value(1));
        assertThat(onlyInsert(before, Resource.GROUPS)).containsEntry("group_id", "LITERAL")
            .containsEntry("group_name", "Literal group").containsEntry("managing_organization_id", 1L);
        verify(mapper).insert(eq(Resource.GROUPS), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void updateCodeGroupRenamesBusinessIdentifierWithoutChangingNumericCodeReferences() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/code-groups/GROUP").cookie(valid()).contentType("application/json")
            .content("{\"group_id\":\"LITERAL-RENAMED\",\"group_name\":\"Literal renamed group\"}"))
            .andExpect(success("updateCodeGroup"))
            .andExpect(jsonPath("$.data.code_group_id").value(1))
            .andExpect(jsonPath("$.data.group_id").value("LITERAL-RENAMED"))
            .andExpect(jsonPath("$.data.group_name").value("Literal renamed group"));
        var changes = Map.<String, Object>of("group_id", "LITERAL-RENAMED", "group_name", "Literal renamed group");
        onlyUpdates(before, Resource.GROUPS, Map.of(1L, changes));
        verify(mapper).update(Resource.GROUPS, 1L, changes);
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void createDetailCodeStoresHierarchyMappingAndDatesWithoutChangingGroupOrParent() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/detail-codes").cookie(valid()).contentType("application/json")
            .content("{\"code_group_id\":1,\"code_value\":\"LITERAL\",\"code_name\":\"Literal code\",\"parent_detail_code_id\":1,\"display_order\":2,\"additional_attributes\":{\"external\":\"literal\"},\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\"}"))
            .andExpect(success("createDetailCode"))
            .andExpect(jsonPath("$.data.detail_code_id").value(101))
            .andExpect(jsonPath("$.data.code_group_id").value(1))
            .andExpect(jsonPath("$.data.parent_detail_code_id").value(1))
            .andExpect(jsonPath("$.data.code_value").value("LITERAL"))
            .andExpect(jsonPath("$.data.additional_attributes.external").value("literal"))
            .andExpect(jsonPath("$.data.valid_end").value("2026-12-31"));
        assertThat(onlyInsert(before, Resource.CODES)).containsEntry("code_group_id", 1L)
            .containsEntry("code_value", "LITERAL").containsEntry("code_name", "Literal code")
            .containsEntry("parent_detail_code_id", 1L).containsEntry("display_order", 2)
            .containsEntry("additional_attributes", "{\"external\":\"literal\"}")
            .containsEntry("valid_start", LocalDate.parse("2026-01-01"))
            .containsEntry("valid_end", LocalDate.parse("2026-12-31"));
        verify(mapper).insert(eq(Resource.CODES), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test
    void updateDetailCodePreservesNumericKeyGroupAndChildReferences() throws Exception {
        rows.get(Resource.CODES).add(row("DetailCode", Map.of("detail_code_id", 2L, "code_group_id", 1L,
            "code_value", "CHILD", "code_name", "Child", "parent_detail_code_id", 1L)));
        var before = snapshot();
        mvc.perform(patch("/api/detail-codes/1").cookie(valid()).contentType("application/json")
            .content("{\"code_value\":\"LITERAL-RENAMED\",\"code_name\":\"Literal renamed code\",\"additional_attributes\":{\"external\":\"updated\"}}"))
            .andExpect(success("updateDetailCode"))
            .andExpect(jsonPath("$.data.detail_code_id").value(1))
            .andExpect(jsonPath("$.data.code_group_id").value(1))
            .andExpect(jsonPath("$.data.code_value").value("LITERAL-RENAMED"))
            .andExpect(jsonPath("$.data.additional_attributes.external").value("updated"));
        var changes = Map.<String, Object>of("code_value", "LITERAL-RENAMED", "code_name", "Literal renamed code",
            "additional_attributes", "{\"external\":\"updated\"}");
        onlyUpdates(before, Resource.CODES, Map.of(1L, changes));
        verify(mapper).update(Resource.CODES, 1L, changes);
        verify(mapper, never()).insert(any(), anyMap());
    }

    @Test
    void createAccountRejectsUnauthenticatedRoleMenuTypeAndPendingPersonnelLink() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/accounts").contentType("application/json")
            .content("{\"login_id\":\"literal-local\",\"password\":\"secret\"}"))
            .andExpect(failure(401, null));
        mvc.perform(post("/api/accounts").cookie(ordinary()).contentType("application/json")
            .content("{\"login_id\":\"literal-local\",\"password\":\"secret\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(post("/api/accounts").cookie(valid()).contentType("application/json")
            .content("{\"login_id\":\"literal-local\",\"password\":\"secret\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(post("/api/accounts").cookie(valid()).contentType("application/json")
            .content("{\"personnel_id\":[]}"))
            .andExpect(failure(400, "personnel_id"));
        mvc.perform(post("/api/accounts").cookie(valid()).contentType("application/json")
            .content("{\"login_id\":\"literal-linked\",\"password\":\"secret\",\"personnel_id\":1}"))
            .andExpect(failure(400, "policy"))
            .andExpect(jsonPath("$.message").value("Pending approval: OQ-006: personnel connection policy"));
        mvc.perform(post("/api/accounts").cookie(valid()).contentType("application/json")
            .content("{\"login_id\":\"literal-local\",\"password\":\"   \"}"))
            .andExpect(failure(400, "password"))
            .andExpect(jsonPath("$.message").value("Password must not be blank"));
        noWrites(before);
    }

    @Test
    void updateAccountRejectsUnauthenticatedRoleMenuTypeResetAndMissingAccount() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/accounts/1").contentType("application/json")
            .content("{\"login_id\":\"literal-renamed\"}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/accounts/1").cookie(ordinary()).contentType("application/json")
            .content("{\"login_id\":\"literal-renamed\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/accounts/1").cookie(valid()).contentType("application/json")
            .content("{\"login_id\":\"literal-renamed\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/accounts/1").cookie(valid()).contentType("application/json")
            .content("{\"login_id\":{}}"))
            .andExpect(failure(400, "login_id"));
        mvc.perform(patch("/api/accounts/1").cookie(valid()).contentType("application/json")
            .content("{\"password\":\"unapproved-reset\"}"))
            .andExpect(failure(400, "password"));
        mvc.perform(patch("/api/accounts/1").cookie(valid()).contentType("application/json")
            .content("{\"personnel_id\":1}"))
            .andExpect(failure(400, "policy"))
            .andExpect(jsonPath("$.message").value("Pending approval: OQ-006: personnel connection policy"));
        mvc.perform(patch("/api/accounts/999").cookie(valid()).contentType("application/json")
            .content("{\"login_id\":\"missing\"}"))
            .andExpect(failure(400, "account_id"))
            .andExpect(jsonPath("$.message").value("Requested record does not exist"));
        noWrites(before);
    }

    @Test
    void updateUserUseStatusRejectsUnauthenticatedRoleMenuTypeAndSourceField() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/users/1/use-status").contentType("application/json")
            .content("{\"use_status\":\"literal-status\"}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/users/1/use-status").cookie(ordinary()).contentType("application/json")
            .content("{\"use_status\":\"literal-status\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/users/1/use-status").cookie(valid()).contentType("application/json")
            .content("{\"use_status\":\"literal-status\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/users/1/use-status").cookie(valid()).contentType("application/json")
            .content("{\"use_status\":{}}"))
            .andExpect(failure(400, "use_status"));
        mvc.perform(patch("/api/users/1/use-status").cookie(valid()).contentType("application/json")
            .content("{\"person_name\":\"source overwrite\"}"))
            .andExpect(failure(400, "person_name"));
        mvc.perform(patch("/api/users/999/use-status").cookie(valid()).contentType("application/json")
            .content("{\"use_status\":\"literal-status\"}"))
            .andExpect(failure(400, "account_id"));
        noWrites(before);
    }

    @Test
    void updateUserRolesRejectsUnauthenticatedRoleMenuEmptyBatchAndUnapprovedRemoval() throws Exception {
        var before = snapshot();
        mvc.perform(put("/api/users/2/roles").contentType("application/json")
            .content("{\"assignments\":[{\"role_code\":\"R01\",\"approver_id\":2}]}"))
            .andExpect(failure(401, null));
        mvc.perform(put("/api/users/2/roles").cookie(ordinary()).contentType("application/json")
            .content("{\"assignments\":[{\"role_code\":\"R01\",\"approver_id\":2}]}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(put("/api/users/2/roles").cookie(valid()).contentType("application/json")
            .content("{\"assignments\":[{\"role_code\":\"R01\",\"approver_id\":2}]}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(put("/api/users/2/roles").cookie(valid()).contentType("application/json")
            .content("{\"assignments\":[]}"))
            .andExpect(failure(400, "assignments"));
        mvc.perform(put("/api/users/2/roles").cookie(valid()).contentType("application/json")
            .content("{\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":2}]}"))
            .andExpect(failure(400, "policy"))
            .andExpect(jsonPath("$.message").value(
                "Pending approval: OQ-DATA-004: replacement requires unresolved role revocation"));
        mvc.perform(put("/api/users/2/roles").cookie(valid()).contentType("application/json")
            .content("{\"assignments\":[{\"role_code\":\"R01\"}]}"))
            .andExpect(failure(400, "approver_id"));
        noWrites(before);
    }

    @Test
    void createOrganizationRejectsUnauthenticatedRoleMenuTypeAndSourceName() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/organizations").contentType("application/json")
            .content("{\"organization_code\":\"ORG\",\"parent_organization_id\":1}"))
            .andExpect(failure(401, null));
        mvc.perform(post("/api/organizations").cookie(ordinary()).contentType("application/json")
            .content("{\"organization_code\":\"ORG\",\"parent_organization_id\":1}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(post("/api/organizations").cookie(valid()).contentType("application/json")
            .content("{\"organization_code\":\"ORG\",\"parent_organization_id\":1}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(post("/api/organizations").cookie(valid()).contentType("application/json")
            .content("{\"parent_organization_id\":{}}"))
            .andExpect(failure(400, "parent_organization_id"));
        mvc.perform(post("/api/organizations").cookie(valid()).contentType("application/json")
            .content("{\"organization_name\":\"source overwrite\"}"))
            .andExpect(failure(400, "organization_name"));
        noWrites(before);
    }

    @Test
    void updateOrganizationRejectsUnauthenticatedRoleMenuDateTypeAndSourceType() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/organizations/1").contentType("application/json")
            .content("{\"effective_start\":\"2026-02-01\"}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/organizations/1").cookie(ordinary()).contentType("application/json")
            .content("{\"effective_start\":\"2026-02-01\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/organizations/1").cookie(valid()).contentType("application/json")
            .content("{\"effective_start\":\"2026-02-01\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/organizations/1").cookie(valid()).contentType("application/json")
            .content("{\"effective_start\":[]}"))
            .andExpect(failure(400, "effective_start"));
        mvc.perform(patch("/api/organizations/1").cookie(valid()).contentType("application/json")
            .content("{\"organization_type\":\"source overwrite\"}"))
            .andExpect(failure(400, "organization_type"));
        noWrites(before);
    }

    @Test
    void createRoleRejectsUnauthenticatedRoleMenuTypeAndOutOfRangeCode() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/roles").contentType("application/json")
            .content("{\"role_code\":\"R08\",\"role_name\":\"Literal\"}"))
            .andExpect(failure(401, null));
        mvc.perform(post("/api/roles").cookie(ordinary()).contentType("application/json")
            .content("{\"role_code\":\"R08\",\"role_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(post("/api/roles").cookie(valid()).contentType("application/json")
            .content("{\"role_code\":\"R08\",\"role_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(post("/api/roles").cookie(valid()).contentType("application/json")
            .content("{\"assignment_criteria\":{}}"))
            .andExpect(failure(400, "assignment_criteria"));
        mvc.perform(post("/api/roles").cookie(valid()).contentType("application/json")
            .content("{\"role_code\":\"R10\",\"role_name\":\"Invalid\"}"))
            .andExpect(failure(400, "role_code"))
            .andExpect(jsonPath("$.message").value("Expected R01 through R09"));
        noWrites(before);
    }

    @Test
    void updateRoleRejectsUnauthenticatedRoleMenuTypeAndCodeRetargeting() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/roles/R01").contentType("application/json")
            .content("{\"role_name\":\"Literal\"}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/roles/R01").cookie(ordinary()).contentType("application/json")
            .content("{\"role_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/roles/R01").cookie(valid()).contentType("application/json")
            .content("{\"role_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/roles/R01").cookie(valid()).contentType("application/json")
            .content("{\"role_name\":[]}"))
            .andExpect(failure(400, "role_name"));
        mvc.perform(patch("/api/roles/R01").cookie(valid()).contentType("application/json")
            .content("{\"role_code\":\"R09\"}"))
            .andExpect(failure(400, "role_code"));
        mvc.perform(patch("/api/roles/R08").cookie(valid()).contentType("application/json")
            .content("{\"role_name\":\"Missing\"}"))
            .andExpect(failure(400, "role_code"));
        noWrites(before);
    }

    @Test
    void grantUserRolesRejectsUnauthenticatedRoleMenuNestedTypeAndMissingApproval() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/user-roles").contentType("application/json")
            .content("{\"account_id\":2,\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":1}]}"))
            .andExpect(failure(401, null));
        mvc.perform(post("/api/user-roles").cookie(ordinary()).contentType("application/json")
            .content("{\"account_id\":2,\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":1}]}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(post("/api/user-roles").cookie(valid()).contentType("application/json")
            .content("{\"account_id\":2,\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":1}]}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(post("/api/user-roles").cookie(valid()).contentType("application/json")
            .content("{\"account_id\":2,\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":[]}]}"))
            .andExpect(failure(400, "assignments[0].approver_id"));
        mvc.perform(post("/api/user-roles").cookie(valid()).contentType("application/json")
            .content("{\"account_id\":2,\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":1},{\"role_code\":\"R01\"}]}"))
            .andExpect(failure(400, "approver_id"));
        mvc.perform(post("/api/user-roles").cookie(valid()).contentType("application/json")
            .content("{\"account_id\":999,\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":1}]}"))
            .andExpect(failure(400, "account_id"));
        noWrites(before);
    }

    @Test
    void changeUserRoleRejectsUnauthenticatedRoleMenuTypeMissingApprovalAndRetargeting() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/user-roles/2").contentType("application/json")
            .content("{\"role_code\":\"R09\",\"approver_id\":1}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/user-roles/2").cookie(ordinary()).contentType("application/json")
            .content("{\"role_code\":\"R09\",\"approver_id\":1}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"role_code\":\"R09\",\"approver_id\":1}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"approver_id\":[]}"))
            .andExpect(failure(400, "approver_id"));
        mvc.perform(patch("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"role_code\":\"R09\"}"))
            .andExpect(failure(400, "approver_id"));
        mvc.perform(patch("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"account_id\":1,\"approver_id\":1}"))
            .andExpect(failure(400, "account_id"));
        mvc.perform(patch("/api/user-roles/999").cookie(valid()).contentType("application/json")
            .content("{\"approver_id\":1}"))
            .andExpect(failure(400, "assignment_id"));
        noWrites(before);
    }

    @Test
    void revokeUserRoleRejectsUnauthenticatedRoleMenuDateTypeAndAccountRetargeting() throws Exception {
        var before = snapshot();
        mvc.perform(delete("/api/user-roles/2").contentType("application/json")
            .content("{\"approver_id\":1}"))
            .andExpect(failure(401, null));
        mvc.perform(delete("/api/user-roles/2").cookie(ordinary()).contentType("application/json")
            .content("{\"approver_id\":1}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(delete("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"approver_id\":1}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(delete("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"valid_end\":{}}"))
            .andExpect(failure(400, "valid_end"));
        mvc.perform(delete("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"account_id\":1,\"approver_id\":1}"))
            .andExpect(failure(400, "account_id"));
        mvc.perform(delete("/api/user-roles/2").cookie(valid()).contentType("application/json")
            .content("{\"valid_end\":\"2026-12-31\"}"))
            .andExpect(failure(400, "approver_id"));
        noWrites(before);
    }

    @Test
    void saveMenuPermissionsRejectsUnauthenticatedRoleMenuTypeAndCombinedSubject() throws Exception {
        var before = snapshot();
        mvc.perform(put("/api/menu-permissions").contentType("application/json")
            .content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":true}]}"))
            .andExpect(failure(401, null));
        mvc.perform(put("/api/menu-permissions").cookie(ordinary()).contentType("application/json")
            .content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":true}]}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(put("/api/menu-permissions").cookie(valid()).contentType("application/json")
            .content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":true}]}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(put("/api/menu-permissions").cookie(valid()).contentType("application/json")
            .content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":\"true\"}]}"))
            .andExpect(failure(400, "permissions[0].access_allowed"));
        mvc.perform(put("/api/menu-permissions").cookie(valid()).contentType("application/json")
            .content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":true},{\"menu_id\":3,\"role_code\":\"R01\",\"account_id\":2,\"access_allowed\":false}]}"))
            .andExpect(failure(400, "policy"))
            .andExpect(jsonPath("$.message").value(
                "Pending approval: OQ-003: unspecified or combined permission subject/decision"));
        noWrites(before);
    }

    @Test
    void changeMenuParentRejectsUnauthenticatedRoleMenuTypeCycleAndExecutionField() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/menu-structure/3/parent").contentType("application/json")
            .content("{\"parent_menu_id\":2}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/menu-structure/3/parent").cookie(ordinary()).contentType("application/json")
            .content("{\"parent_menu_id\":2}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/menu-structure/3/parent").cookie(valid()).contentType("application/json")
            .content("{\"parent_menu_id\":2}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/menu-structure/3/parent").cookie(valid()).contentType("application/json")
            .content("{\"parent_menu_id\":[]}"))
            .andExpect(failure(400, "parent_menu_id"));
        mvc.perform(patch("/api/menu-structure/1/parent").cookie(valid()).contentType("application/json")
            .content("{\"parent_menu_id\":3}"))
            .andExpect(failure(400, "parent_menu_id"))
            .andExpect(jsonPath("$.message").value("Parent would create an invalid menu tree"));
        mvc.perform(patch("/api/menu-structure/3/parent").cookie(valid()).contentType("application/json")
            .content("{\"parent_menu_id\":2,\"menu_name\":\"execution overwrite\"}"))
            .andExpect(failure(400, "menu_name"));
        noWrites(before);
    }

    @Test
    void reorderSiblingMenusRejectsUnauthenticatedRoleMenuTypeAndLaterNonSibling() throws Exception {
        var before = snapshot();
        mvc.perform(put("/api/menu-structure/order").contentType("application/json")
            .content("{\"parent_menu_id\":1,\"items\":[{\"menu_id\":2,\"display_order\":9}]}"))
            .andExpect(failure(401, null));
        mvc.perform(put("/api/menu-structure/order").cookie(ordinary()).contentType("application/json")
            .content("{\"parent_menu_id\":1,\"items\":[{\"menu_id\":2,\"display_order\":9}]}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(put("/api/menu-structure/order").cookie(valid()).contentType("application/json")
            .content("{\"parent_menu_id\":1,\"items\":[{\"menu_id\":2,\"display_order\":9}]}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(put("/api/menu-structure/order").cookie(valid()).contentType("application/json")
            .content("{\"items\":\"invalid\"}"))
            .andExpect(failure(400, "items"));
        mvc.perform(put("/api/menu-structure/order").cookie(valid()).contentType("application/json")
            .content("{\"parent_menu_id\":1,\"items\":[{\"menu_id\":2,\"display_order\":9},{\"menu_id\":1,\"display_order\":8}]}"))
            .andExpect(failure(400, "items"))
            .andExpect(jsonPath("$.message").value("Order operation is limited to the submitted siblings"));
        mvc.perform(put("/api/menu-structure/order").cookie(valid()).contentType("application/json")
            .content("{\"items\":[{\"menu_id\":2,\"display_order\":9},{\"menu_id\":2,\"display_order\":8}]}"))
            .andExpect(failure(400, "items"));
        noWrites(before);
    }

    @Test
    void createMenuInformationRejectsUnauthenticatedRoleMenuTypeAndHierarchyField() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/menu-information").contentType("application/json")
            .content("{\"menu_name\":\"Literal\",\"url\":\"/admin/literal\"}"))
            .andExpect(failure(401, null));
        mvc.perform(post("/api/menu-information").cookie(ordinary()).contentType("application/json")
            .content("{\"menu_name\":\"Literal\",\"url\":\"/admin/literal\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(post("/api/menu-information").cookie(valid()).contentType("application/json")
            .content("{\"menu_name\":\"Literal\",\"url\":\"/admin/literal\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(post("/api/menu-information").cookie(valid()).contentType("application/json")
            .content("{\"screen_id\":{}}"))
            .andExpect(failure(400, "screen_id"));
        mvc.perform(post("/api/menu-information").cookie(valid()).contentType("application/json")
            .content("{\"menu_name\":\"Literal\",\"parent_menu_id\":1}"))
            .andExpect(failure(400, "parent_menu_id"));
        noWrites(before);
    }

    @Test
    void updateMenuInformationRejectsUnauthenticatedRoleMenuTypeOrderAndMissingMenu() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/menu-information/2").contentType("application/json")
            .content("{\"menu_name\":\"Literal\"}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/menu-information/2").cookie(ordinary()).contentType("application/json")
            .content("{\"menu_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/menu-information/2").cookie(valid()).contentType("application/json")
            .content("{\"menu_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/menu-information/2").cookie(valid()).contentType("application/json")
            .content("{\"url\":[]}"))
            .andExpect(failure(400, "url"));
        mvc.perform(patch("/api/menu-information/2").cookie(valid()).contentType("application/json")
            .content("{\"display_order\":1}"))
            .andExpect(failure(400, "display_order"));
        mvc.perform(patch("/api/menu-information/999").cookie(valid()).contentType("application/json")
            .content("{\"menu_name\":\"Missing\"}"))
            .andExpect(failure(400, "menu_id"));
        noWrites(before);
    }

    @Test
    void createCodeGroupRejectsUnauthenticatedRoleMenuTypeAndDetailField() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/code-groups").contentType("application/json")
            .content("{\"group_id\":\"LITERAL\",\"group_name\":\"Literal\"}"))
            .andExpect(failure(401, null));
        mvc.perform(post("/api/code-groups").cookie(ordinary()).contentType("application/json")
            .content("{\"group_id\":\"LITERAL\",\"group_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(post("/api/code-groups").cookie(valid()).contentType("application/json")
            .content("{\"group_id\":\"LITERAL\",\"group_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(post("/api/code-groups").cookie(valid()).contentType("application/json")
            .content("{\"managing_organization_id\":{}}"))
            .andExpect(failure(400, "managing_organization_id"));
        mvc.perform(post("/api/code-groups").cookie(valid()).contentType("application/json")
            .content("{\"group_id\":\"LITERAL\",\"code_value\":\"detail overwrite\"}"))
            .andExpect(failure(400, "code_value"));
        noWrites(before);
    }

    @Test
    void updateCodeGroupRejectsUnauthenticatedRoleMenuTypeDetailFieldAndMissingGroup() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/code-groups/GROUP").contentType("application/json")
            .content("{\"group_name\":\"Literal\"}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/code-groups/GROUP").cookie(ordinary()).contentType("application/json")
            .content("{\"group_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/code-groups/GROUP").cookie(valid()).contentType("application/json")
            .content("{\"group_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/code-groups/GROUP").cookie(valid()).contentType("application/json")
            .content("{\"group_name\":[]}"))
            .andExpect(failure(400, "group_name"));
        mvc.perform(patch("/api/code-groups/GROUP").cookie(valid()).contentType("application/json")
            .content("{\"code_name\":\"detail overwrite\"}"))
            .andExpect(failure(400, "code_name"));
        mvc.perform(patch("/api/code-groups/MISSING").cookie(valid()).contentType("application/json")
            .content("{\"group_name\":\"Literal\"}"))
            .andExpect(failure(400, "groupId"))
            .andExpect(jsonPath("$.message").value("Requested code group does not exist"));
        noWrites(before);
    }

    @Test
    void createDetailCodeRejectsUnauthenticatedRoleMenuMappingTypeAndGroupField() throws Exception {
        var before = snapshot();
        mvc.perform(post("/api/detail-codes").contentType("application/json")
            .content("{\"code_group_id\":1,\"code_value\":\"LITERAL\",\"code_name\":\"Literal\"}"))
            .andExpect(failure(401, null));
        mvc.perform(post("/api/detail-codes").cookie(ordinary()).contentType("application/json")
            .content("{\"code_group_id\":1,\"code_value\":\"LITERAL\",\"code_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(post("/api/detail-codes").cookie(valid()).contentType("application/json")
            .content("{\"code_group_id\":1,\"code_value\":\"LITERAL\",\"code_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(post("/api/detail-codes").cookie(valid()).contentType("application/json")
            .content("{\"additional_attributes\":[]}"))
            .andExpect(failure(400, "additional_attributes"));
        mvc.perform(post("/api/detail-codes").cookie(valid()).contentType("application/json")
            .content("{\"group_name\":\"group overwrite\"}"))
            .andExpect(failure(400, "group_name"));
        mvc.perform(post("/api/detail-codes").cookie(valid()).contentType("application/json")
            .content("{\"valid_start\":\"not-a-date\"}"))
            .andExpect(failure(400, "valid_start"));
        noWrites(before);
    }

    @Test
    void updateDetailCodeRejectsUnauthenticatedRoleMenuTypeGroupFieldAndMissingCode() throws Exception {
        var before = snapshot();
        mvc.perform(patch("/api/detail-codes/1").contentType("application/json")
            .content("{\"code_name\":\"Literal\"}"))
            .andExpect(failure(401, null));
        mvc.perform(patch("/api/detail-codes/1").cookie(ordinary()).contentType("application/json")
            .content("{\"code_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = List.of();
        mvc.perform(patch("/api/detail-codes/1").cookie(valid()).contentType("application/json")
            .content("{\"code_name\":\"Literal\"}"))
            .andExpect(failure(403, null));
        decisions = rows.get(Resource.PERMISSIONS);
        mvc.perform(patch("/api/detail-codes/1").cookie(valid()).contentType("application/json")
            .content("{\"parent_detail_code_id\":{}}"))
            .andExpect(failure(400, "parent_detail_code_id"));
        mvc.perform(patch("/api/detail-codes/1").cookie(valid()).contentType("application/json")
            .content("{\"managing_organization_id\":1}"))
            .andExpect(failure(400, "managing_organization_id"));
        mvc.perform(patch("/api/detail-codes/999").cookie(valid()).contentType("application/json")
            .content("{\"code_name\":\"Missing\"}"))
            .andExpect(failure(400, "detail_code_id"));
        noWrites(before);
    }

    private Cookie valid() {
        return new Cookie("CMSSESSION", "valid");
    }

    private Cookie ordinary() {
        return new Cookie("CMSSESSION", "ordinary");
    }

    /** Assertion-only helper: never constructs or executes an HTTP request. */
    private ResultMatcher success(String operationId) {
        return result -> {
            status().isOk().match(result);
            jsonPath("$.success").value(true).match(result);
            jsonPath("$.meta").isMap().match(result);
            jsonPath("$.data").exists().match(result);
            JsonNode payload = json.readTree(result.getResponse().getContentAsString());
            JsonNode operation = null;
            for (JsonNode path : contract.path("paths")) {
                for (JsonNode candidate : path) {
                    if (operationId.equals(candidate.path("operationId").asText())) {
                        operation = candidate;
                    }
                }
            }
            assertThat(operation).as("contract operation %s", operationId).isNotNull();
            requiredKeys(operation.path("responses").path("200").path("content")
                .path("application/json").path("schema"), payload);
            assertThat(result.getResponse().getContentAsString())
                .doesNotContain("password_hash", "session_id", "literal-secret", "Database internal");
        };
    }

    /** Assertion-only helper for the actual error envelope and exact rejected field. */
    private ResultMatcher failure(int expectedStatus, String field) {
        return result -> {
            status().is(expectedStatus).match(result);
            jsonPath("$.success").value(false).match(result);
            jsonPath("$.meta").isMap().match(result);
            jsonPath("$.message").isNotEmpty().match(result);
            jsonPath("$.fieldErrors").isArray().match(result);
            if (field != null) {
                jsonPath("$.fieldErrors[0].field").value(field).match(result);
                jsonPath("$.fieldErrors[0].message").isNotEmpty().match(result);
            } else {
                jsonPath("$.fieldErrors.length()").value(0).match(result);
            }
            assertThat(result.getResponse().getContentAsString())
                .doesNotContain("password_hash", "session_id", "Database internal");
        };
    }

    private Map<Resource, List<Map<String, Object>>> snapshot() {
        Map<Resource, List<Map<String, Object>>> copy = new EnumMap<>(Resource.class);
        rows.forEach((resource, stored) -> {
            List<Map<String, Object>> values = new ArrayList<>();
            stored.forEach(value -> values.add(new LinkedHashMap<>(value)));
            copy.put(resource, values);
        });
        return copy;
    }

    /** Compares every resource and every existing row, not only the returned inserted object. */
    private Map<String, Object> onlyInsert(Map<Resource, List<Map<String, Object>>> before, Resource target) {
        for (Resource resource : Resource.values()) {
            if (resource != target) {
                assertThat(rows.get(resource)).as("unchanged %s", resource).isEqualTo(before.get(resource));
            }
        }
        var stored = rows.get(target);
        int oldSize = before.get(target).size();
        assertThat(stored).hasSize(oldSize + 1);
        assertThat(stored.subList(0, oldSize)).isEqualTo(before.get(target));
        return stored.get(oldSize);
    }

    /** Checks exact explicit changes and preserves all omitted fields, identities and related rows. */
    private void onlyUpdates(Map<Resource, List<Map<String, Object>>> before, Resource target,
            Map<?, ? extends Map<String, Object>> changes) {
        for (Resource resource : Resource.values()) {
            if (resource != target) {
                assertThat(rows.get(resource)).as("unchanged %s", resource).isEqualTo(before.get(resource));
                continue;
            }
            assertThat(rows.get(resource)).hasSameSizeAs(before.get(resource));
            for (int index = 0; index < before.get(resource).size(); index++) {
                var original = before.get(resource).get(index);
                var actual = rows.get(resource).get(index);
                Object id = original.get(resource.getKey());
                var expected = new LinkedHashMap<>(original);
                if (changes.containsKey(id)) {
                    expected.putAll(changes.get(id));
                    assertThat(actual.get("updated_at")).isNotNull();
                    assertThat(Objects.equals(original.get("updated_at"), actual.get("updated_at"))).isFalse();
                    expected.put("updated_at", actual.get("updated_at"));
                }
                assertThat(actual).as("%s row %s", resource, id).isEqualTo(expected);
            }
        }
    }

    private void noWrites(Map<Resource, List<Map<String, Object>>> before) {
        assertThat(rows).isEqualTo(before);
        verify(mapper, never()).insert(any(), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
        verifyNoInteractions(events, personnel);
        verify(authentication, never()).createSession(any());
        verify(authentication, never()).terminate(any());
    }
}
