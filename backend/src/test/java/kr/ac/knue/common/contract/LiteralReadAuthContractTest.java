package kr.ac.knue.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.application.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Literal HTTP coverage using real controllers/services and the durable inherited OpenAPI fixture.
 * Persistence and authentication adapters remain mocked: these are not DB/session-storage proofs.
 * Approval-dependent lifetime, account-state, revocation and permission-composition policies stay unresolved.
 */
public class LiteralReadAuthContractTest extends ContractMvcSupport {
    private String beforeRows;

    @Override
    protected Set<String> operations() {
        return Set.of("getCurrentUser", "searchUsers", "searchOrganizations", "listRoles", "listUserRoles",
            "listMenuPermissions", "getMenuStructure", "listMenuInformation", "listCodeGroups", "listDetailCodes",
            "getHealth", "login", "logout");
    }

    @BeforeEach
    void captureBusinessRows() throws Exception {
        beforeRows = json.writeValueAsString(rows);
    }

    @AfterEach
    void businessRowsAndChangeEventsRemainUntouched() throws Exception {
        assertThat(json.writeValueAsString(rows)).isEqualTo(beforeRows);
        verify(mapper, never()).insert(any(), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
        verifyNoInteractions(events);
    }

    @Test
    void currentUserReturnsOnlyCurrentIdentityAndDoesNotRequireManagementPermission() throws Exception {
        var result = mvc.perform(get("/api/auth/me").cookie(session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountId").value(1))
            .andExpect(jsonPath("$.data.loginId").value("admin"))
            .andExpect(jsonPath("$.data.roles[0]").value("R09")).andReturn();
        responseSchema("/api/auth/me", "get", 200, result);
        decisions = List.of();
        var ordinary = mvc.perform(get("/api/auth/me").cookie(ordinarySession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountId").value(2))
            .andExpect(jsonPath("$.data.loginId").value("local"))
            .andExpect(jsonPath("$.data.roles[0]").value("R01"))
            .andExpect(jsonPath("$.data.allowedMenus").isEmpty()).andReturn();
        responseSchema("/api/auth/me", "get", 200, ordinary);
        error("/api/auth/me", "get", 401, mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized()).andReturn());
        error("/api/auth/me", "get", 401,
            mvc.perform(get("/api/auth/me").cookie(brokenSession()))
                .andExpect(status().isUnauthorized()).andReturn());
        verify(authentication, never()).createSession(anyLong());
        verify(authentication, never()).terminate(any());
    }

    @Test
    void usersJoinExactPersonnelFilterWithLocalAccountPositionsAndRoles() throws Exception {
        var result = mvc.perform(get("/api/users").cookie(session()).param("employeeNumber", "EMP")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].personnel.employee_number").value("EMP"))
            .andExpect(jsonPath("$.data[0].personnel.person_name").value("합성 사용자"))
            .andExpect(jsonPath("$.data[0].personnel.organization_id").value(1))
            .andExpect(jsonPath("$.data[0].account.account_id").value(1))
            .andExpect(jsonPath("$.data[0].positions[0].position_name").value("합성 보직"))
            .andExpect(jsonPath("$.data[0].roles[0].role_code").value("R09")).andReturn();
        pageSchema("/api/users", result, 0, 1, 1);
        var empty = mvc.perform(get("/api/users").cookie(session()).param("employeeNumber", "ABSENT")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty()).andReturn();
        pageSchema("/api/users", empty, 0, 1, 0);
        fieldError("/api/users", mvc.perform(get("/api/users").cookie(session())
                .param("page", "text").param("size", "1"))
            .andExpect(status().isBadRequest()).andReturn(), "page");
    }

    @Test
    void organizationsReturnExactBusinessCodeWithoutSourceOrRelationshipWrites() throws Exception {
        var result = mvc.perform(get("/api/organizations").cookie(session()).param("organizationCode", "ORG")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].organization_id").value(1))
            .andExpect(jsonPath("$.data[0].organization_code").value("ORG"))
            .andExpect(jsonPath("$.data[0].organization_name").value("합성 조직")).andReturn();
        pageSchema("/api/organizations", result, 0, 1, 1);
        var empty = mvc.perform(get("/api/organizations").cookie(session())
                .param("organizationCode", "ABSENT").param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty()).andReturn();
        pageSchema("/api/organizations", empty, 0, 1, 0);
        fieldError("/api/organizations", mvc.perform(get("/api/organizations").cookie(session())
                .param("page", "text").param("size", "1"))
            .andExpect(status().isBadRequest()).andReturn(), "page");
    }

    @Test
    void rolesExposeBothFixturePagesAndRejectZeroSizeWithoutChangingDefinitions() throws Exception {
        var first = mvc.perform(get("/api/roles").cookie(session()).param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].role_code").value("R09"))
            .andExpect(jsonPath("$.data[0].role_name").value("관리자")).andReturn();
        pageSchema("/api/roles", first, 0, 1, 2);
        var second = mvc.perform(get("/api/roles").cookie(session()).param("page", "1").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].role_code").value("R01"))
            .andExpect(jsonPath("$.data[0].role_name").value("역할")).andReturn();
        pageSchema("/api/roles", second, 1, 1, 2);
        fieldError("/api/roles", mvc.perform(get("/api/roles").cookie(session())
                .param("page", "0").param("size", "0"))
            .andExpect(status().isBadRequest()).andReturn(), "size");
    }

    @Test
    void userRolesFilterAccountAndPreserveApproverWithoutInventingPeriodEffects() throws Exception {
        var result = mvc.perform(get("/api/user-roles").cookie(session()).param("userId", "2")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].assignment_id").value(2))
            .andExpect(jsonPath("$.data[0].account_id").value(2))
            .andExpect(jsonPath("$.data[0].role_code").value("R01"))
            .andExpect(jsonPath("$.data[0].approver_id").value(1)).andReturn();
        pageSchema("/api/user-roles", result, 0, 1, 1);
        var empty = mvc.perform(get("/api/user-roles").cookie(session()).param("userId", "999")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty()).andReturn();
        pageSchema("/api/user-roles", empty, 0, 1, 0);
        fieldError("/api/user-roles", mvc.perform(get("/api/user-roles").cookie(session())
                .param("userId", "text").param("page", "0").param("size", "1"))
            .andExpect(status().isBadRequest()).andReturn(), "userId");
    }

    @Test
    void menuPermissionsResolveExactMenuAndRoleFilterWithoutRewritingDecisions() throws Exception {
        var result = mvc.perform(get("/api/menu-permissions").cookie(session())
                .param("roleCode", "R09").param("menuId", "2").param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].permission_id").value(2))
            .andExpect(jsonPath("$.data[0].menu_id").value(2))
            .andExpect(jsonPath("$.data[0].role_code").value("R09"))
            .andExpect(jsonPath("$.data[0].access_allowed").value(true)).andReturn();
        pageSchema("/api/menu-permissions", result, 0, 1, 1);
        var empty = mvc.perform(get("/api/menu-permissions").cookie(session()).param("roleCode", "R01")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty()).andReturn();
        pageSchema("/api/menu-permissions", empty, 0, 1, 0);
        fieldError("/api/menu-permissions", mvc.perform(get("/api/menu-permissions").cookie(session())
                .param("roleCode", "R10").param("page", "0").param("size", "1"))
            .andExpect(status().isBadRequest()).andReturn(), "roleCode");
    }

    @Test
    void menuStructureKeepsParentAndSiblingOrderAndDeniedNavigationMatchesDirectAccess() throws Exception {
        var result = mvc.perform(get("/api/menu-structure").cookie(session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(10))
            .andExpect(jsonPath("$.data[0].menu_id").value(1))
            .andExpect(jsonPath("$.data[1].menu_id").value(2))
            .andExpect(jsonPath("$.data[1].parent_menu_id").value(1))
            .andExpect(jsonPath("$.data[1].display_order").value(0))
            .andExpect(jsonPath("$.data[1].screen_id").value("SCR-USERS"))
            .andExpect(jsonPath("$.data[9].display_order").value(8)).andReturn();
        responseSchema("/api/menu-structure", "get", 200, result);
        error("/api/menu-structure", "get", 401,
            mvc.perform(get("/api/menu-structure").cookie(brokenSession()))
                .andExpect(status().isUnauthorized()).andReturn());
        // A single explicit denial with no competing decisions does not approve composition precedence.
        decisions = rows.get(Resource.PERMISSIONS).stream()
            .map(permission -> {
                var copy = new java.util.LinkedHashMap<>(permission);
                copy.put("access_allowed", false);
                return (java.util.Map<String, Object>) copy;
            }).toList();
        var hidden = mvc.perform(get("/api/auth/me").cookie(session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowedMenus").isEmpty()).andReturn();
        responseSchema("/api/auth/me", "get", 200, hidden);
        error("/api/menu-structure", "get", 403, mvc.perform(get("/api/menu-structure").cookie(session()))
            .andExpect(status().isForbidden()).andReturn());
    }

    @Test
    void menuInformationReturnsExecutionFieldsOnLaterPageWithoutAlteringStructure() throws Exception {
        var root = mvc.perform(get("/api/menu-information").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].menu_id").value(1)).andReturn();
        pageSchema("/api/menu-information", root, 0, 1, 10);
        var all = mvc.perform(get("/api/menu-information").cookie(session())
                .param("page", "0").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[1].menu_id").value(2))
            .andExpect(jsonPath("$.data[1].screen_id").value("SCR-USERS"))
            .andExpect(jsonPath("$.data[1].url").value("/admin/fixture"))
            .andExpect(jsonPath("$.data[9].screen_id").value("SCR-DETAIL-CODES")).andReturn();
        pageSchema("/api/menu-information", all, 0, 10, 10);
        fieldError("/api/menu-information", mvc.perform(get("/api/menu-information").cookie(session())
                .param("page", "-1").param("size", "1"))
            .andExpect(status().isBadRequest()).andReturn(), "page");
    }

    @Test
    void codeGroupsExposeBusinessIdUsedByDetailCodeReadWithoutInventingOwnership() throws Exception {
        var result = mvc.perform(get("/api/code-groups").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code_group_id").value(1))
            .andExpect(jsonPath("$.data[0].group_id").value("GROUP"))
            .andExpect(jsonPath("$.data[0].group_name").value("합성 그룹")).andReturn();
        pageSchema("/api/code-groups", result, 0, 1, 1);
        var detail = mvc.perform(get("/api/detail-codes").cookie(session()).param("groupId", "GROUP")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code_group_id").value(1)).andReturn();
        pageSchema("/api/detail-codes", detail, 0, 1, 1);
        fieldError("/api/code-groups", mvc.perform(get("/api/code-groups").cookie(session())
                .param("page", "0").param("size", "text"))
            .andExpect(status().isBadRequest()).andReturn(), "size");
    }

    @Test
    void detailCodesResolveBusinessGroupAndNormalizeAttributesWithoutMutatingStoredJson() throws Exception {
        var result = mvc.perform(get("/api/detail-codes").cookie(session()).param("groupId", "GROUP")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].detail_code_id").value(1))
            .andExpect(jsonPath("$.data[0].code_group_id").value(1))
            .andExpect(jsonPath("$.data[0].code_value").value("ONE"))
            .andExpect(jsonPath("$.data[0].code_name").value("첫 코드"))
            .andExpect(jsonPath("$.data[0].additional_attributes.external").value("mapping")).andReturn();
        pageSchema("/api/detail-codes", result, 0, 1, 1);
        assertThat(rows.get(Resource.CODES).get(0).get("additional_attributes"))
            .isEqualTo("{\"external\":\"mapping\"}");
        fieldError("/api/detail-codes", mvc.perform(get("/api/detail-codes").cookie(session())
                .param("groupId", "GROUP").param("page", "-1").param("size", "1"))
            .andExpect(status().isBadRequest()).andReturn(), "page");
        var missing = mvc.perform(get("/api/detail-codes").cookie(session()).param("groupId", "MISSING")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested code group does not exist"))
            .andReturn();
        fieldError("/api/detail-codes", missing, "groupId");
    }

    @Test
    void healthIsPublicRepeatableBooleanReadWithoutIdentityOrBusinessData() throws Exception {
        var first = mvc.perform(get("/api/health"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.healthy").value(true)).andReturn();
        responseSchema("/api/health", "get", 200, first);
        var second = mvc.perform(get("/api/health"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.healthy").value(true)).andReturn();
        responseSchema("/api/health", "get", 200, second);
        assertThat(json.readTree(first.getResponse().getContentAsString()).path("data").size()).isEqualTo(1);
        assertThat(json.readTree(first.getResponse().getContentAsString()).path("data").path("healthy").isBoolean())
            .isTrue();
        assertThat(json.readTree(second.getResponse().getContentAsString()))
            .isEqualTo(json.readTree(first.getResponse().getContentAsString()));
        verify(mapper, times(2)).health();
        verifyNoInteractions(authentication, personnel);
    }

    @Test
    void allManagementReadsRejectMissingSessionsWithContractErrorEnvelopes() throws Exception {
        error("/api/users", "get", 401, mvc.perform(get("/api/users").param("page", "0").param("size", "1"))
            .andExpect(status().isUnauthorized()).andReturn());
        error("/api/organizations", "get", 401,
            mvc.perform(get("/api/organizations").param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized()).andReturn());
        error("/api/roles", "get", 401, mvc.perform(get("/api/roles").param("page", "0").param("size", "1"))
            .andExpect(status().isUnauthorized()).andReturn());
        error("/api/user-roles", "get", 401,
            mvc.perform(get("/api/user-roles").param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized()).andReturn());
        error("/api/menu-permissions", "get", 401,
            mvc.perform(get("/api/menu-permissions").param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized()).andReturn());
        error("/api/menu-structure", "get", 401, mvc.perform(get("/api/menu-structure"))
            .andExpect(status().isUnauthorized()).andReturn());
        error("/api/menu-information", "get", 401,
            mvc.perform(get("/api/menu-information").param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized()).andReturn());
        error("/api/code-groups", "get", 401,
            mvc.perform(get("/api/code-groups").param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized()).andReturn());
        error("/api/detail-codes", "get", 401,
            mvc.perform(get("/api/detail-codes").param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized()).andReturn());
        verifyNoInteractions(personnel);
    }

    @Test
    void allManagementReadsRejectInsufficientRoles() throws Exception {
        error("/api/users", "get", 403, mvc.perform(get("/api/users").cookie(ordinarySession())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/organizations", "get", 403, mvc.perform(get("/api/organizations").cookie(ordinarySession())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/roles", "get", 403, mvc.perform(get("/api/roles").cookie(ordinarySession())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/user-roles", "get", 403, mvc.perform(get("/api/user-roles").cookie(ordinarySession())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/menu-permissions", "get", 403,
            mvc.perform(get("/api/menu-permissions").cookie(ordinarySession())
                    .param("page", "0").param("size", "1"))
                .andExpect(status().isForbidden()).andReturn());
        error("/api/menu-structure", "get", 403, mvc.perform(get("/api/menu-structure").cookie(ordinarySession()))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/menu-information", "get", 403,
            mvc.perform(get("/api/menu-information").cookie(ordinarySession())
                    .param("page", "0").param("size", "1"))
                .andExpect(status().isForbidden()).andReturn());
        error("/api/code-groups", "get", 403, mvc.perform(get("/api/code-groups").cookie(ordinarySession())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/detail-codes", "get", 403, mvc.perform(get("/api/detail-codes").cookie(ordinarySession())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        verifyNoInteractions(personnel);
    }

    @Test
    void r09CannotBypassMissingDatabaseMenuDecisionsOnAnyManagementRead() throws Exception {
        decisions = List.of();
        error("/api/users", "get", 403, mvc.perform(get("/api/users").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/organizations", "get", 403, mvc.perform(get("/api/organizations").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/roles", "get", 403, mvc.perform(get("/api/roles").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/user-roles", "get", 403, mvc.perform(get("/api/user-roles").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/menu-permissions", "get", 403, mvc.perform(get("/api/menu-permissions").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/menu-structure", "get", 403, mvc.perform(get("/api/menu-structure").cookie(session()))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/menu-information", "get", 403, mvc.perform(get("/api/menu-information").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/code-groups", "get", 403, mvc.perform(get("/api/code-groups").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        error("/api/detail-codes", "get", 403, mvc.perform(get("/api/detail-codes").cookie(session())
                .param("page", "0").param("size", "1"))
            .andExpect(status().isForbidden()).andReturn());
        verifyNoInteractions(personnel);
    }

    @Test
    void loginIssuesHttpOnlyLocalCookieAndSameCookieReturnsSeedIdentity() throws Exception {
        var login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"login_id\":\"admin\",\"password\":\"admin\"}"))
            .andExpect(status().isOk()).andReturn();
        responseSchema("/api/auth/login", "post", 200, login);
        String cookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("CMSSESSION=valid", "Path=/", "HttpOnly", "SameSite=Lax")
            .doesNotContain("Secure");
        String token = cookie.substring("CMSSESSION=".length(), cookie.indexOf(';'));
        var me = mvc.perform(get("/api/auth/me").cookie(new Cookie("CMSSESSION", token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountId").value(1))
            .andExpect(jsonPath("$.data.loginId").value("admin"))
            .andExpect(jsonPath("$.data.roles[0]").value("R09")).andReturn();
        responseSchema("/api/auth/me", "get", 200, me);
        verify(authentication).verify("admin", "admin");
        verify(authentication).createSession(1L);
        verify(authentication, never()).terminate(any());
    }

    @Test
    void invalidLoginCredentialsNeverCreateSessionOrCookie() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"login_id\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        error("/api/auth/login", "post", 401, result);
        verify(authentication).verify("admin", "wrong");
        verify(authentication, never()).createSession(anyLong());
        verify(authentication, never()).terminate(any());
    }

    @Test
    void loginRejectsObjectCredentialAndMissingPasswordBeforeAuthentication() throws Exception {
        var object = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"login_id\":{},\"password\":\"admin\"}"))
            .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        fieldError("/api/auth/login", "post", object, "login_id");
        var missing = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"login_id\":\"admin\"}"))
            .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        fieldError("/api/auth/login", "post", missing, "password");
        verifyNoInteractions(authentication);
    }

    @Test
    void logoutTerminatesOnlyCurrentOrdinarySessionAndRejectsReuseWithoutManagementPermission() throws Exception {
        decisions = List.of();
        // The adapter double models termination, not a production storage/lifetime policy.
        doAnswer(call -> {
            when(authentication.authenticate("ordinary"))
                .thenThrow(new ApiException(401, "Authentication required", null));
            return null;
        }).when(authentication).terminate("ordinary");
        var result = mvc.perform(post("/api/auth/logout").cookie(ordinarySession()))
            .andExpect(status().isOk()).andReturn();
        responseSchema("/api/auth/logout", "post", 200, result);
        assertThat(result.getResponse().getHeader("Set-Cookie"))
            .contains("CMSSESSION=", "Max-Age=0", "Path=/", "HttpOnly", "SameSite=Lax");
        error("/api/auth/me", "get", 401, mvc.perform(get("/api/auth/me").cookie(ordinarySession()))
            .andExpect(status().isUnauthorized()).andReturn());
        error("/api/auth/logout", "post", 401, mvc.perform(post("/api/auth/logout").cookie(ordinarySession()))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie")).andReturn());
        var other = mvc.perform(get("/api/auth/me").cookie(session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.accountId").value(1)).andReturn();
        responseSchema("/api/auth/me", "get", 200, other);
        verify(authentication, times(1)).terminate("ordinary");
        verify(authentication, never()).terminate("valid");
        verify(authentication, never()).createSession(anyLong());
    }

    @Test
    void logoutRejectsMissingAndCorruptCookiesWithoutTerminatingAnySession() throws Exception {
        error("/api/auth/logout", "post", 401, mvc.perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie")).andReturn());
        error("/api/auth/logout", "post", 401,
            mvc.perform(post("/api/auth/logout").cookie(brokenSession()))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie")).andReturn());
        verify(authentication, never()).terminate(any());
        verify(authentication, never()).createSession(anyLong());
    }

    private JsonNode responseSchema(String path, String method, int code, MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(code);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        JsonNode payload = json.readTree(result.getResponse().getContentAsString());
        assertThat(payload.isObject()).isTrue();
        assertThat(payload.path("success").isBoolean()).isTrue();
        assertThat(payload.path("success").asBoolean()).isEqualTo(code == 200);
        assertThat(payload.path("meta").isObject()).isTrue();
        JsonNode schema = contract.path("paths").path(path).path(method).path("responses")
            .path(String.valueOf(code)).path("content").path("application/json").path("schema");
        assertThat(schema.isMissingNode()).as("durable response schema: %s %s %s", method, path, code).isFalse();
        requiredKeys(schema, payload);
        assertThat(result.getResponse().getContentAsString())
            .doesNotContain("\"password\"", "\"password_hash\"", "\"session_id\"");
        return payload;
    }

    private void pageSchema(String path, MvcResult result, int page, int size, int total) throws Exception {
        JsonNode payload = responseSchema(path, "get", 200, result);
        assertThat(payload.path("data").isArray()).isTrue();
        assertThat(payload.path("meta").path("page").isIntegralNumber()).isTrue();
        assertThat(payload.path("meta").path("size").isIntegralNumber()).isTrue();
        assertThat(payload.path("meta").path("total").isIntegralNumber()).isTrue();
        assertThat(payload.path("meta").path("page").asInt()).isEqualTo(page);
        assertThat(payload.path("meta").path("size").asInt()).isEqualTo(size);
        assertThat(payload.path("meta").path("total").asInt()).isEqualTo(total);
        assertThat(payload.path("data").size()).isEqualTo(Math.min(size, Math.max(0, total - page * size)));
    }

    private void error(String path, String method, int code, MvcResult result) throws Exception {
        JsonNode payload = responseSchema(path, method, code, result);
        assertThat(payload.path("message").isTextual()).isTrue();
        assertThat(payload.path("message").asText()).isNotBlank();
    }

    private void fieldError(String path, MvcResult result, String field) throws Exception {
        fieldError(path, "get", result, field);
    }

    private void fieldError(String path, String method, MvcResult result, String field) throws Exception {
        error(path, method, 400, result);
        JsonNode errors = json.readTree(result.getResponse().getContentAsString()).path("fieldErrors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors.size()).isPositive();
        assertThat(errors.get(0).path("field").asText()).isEqualTo(field);
        assertThat(errors.get(0).path("message").isTextual()).isTrue();
        assertThat(errors.get(0).path("message").asText()).isNotBlank();
    }

    private Cookie session() {
        return new Cookie("CMSSESSION", "valid");
    }

    private Cookie ordinarySession() {
        return new Cookie("CMSSESSION", "ordinary");
    }

    private Cookie brokenSession() {
        return new Cookie("CMSSESSION", "broken");
    }
}
