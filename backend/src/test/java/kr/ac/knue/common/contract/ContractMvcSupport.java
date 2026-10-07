package kr.ac.knue.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import kr.ac.knue.common.adapter.mybatis.ManagementMapper;
import kr.ac.knue.common.api.*;
import kr.ac.knue.common.application.*;
import kr.ac.knue.common.domain.CurrentPrincipal;
import kr.ac.knue.common.domain.ports.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Real controllers/services/policy, with DB ports mocked only at the persistence boundary.
 * Each feature class inherits success/pending, 401, two distinct 403 and validation tests
 * for every operation it owns. Real PostgreSQL tests are separate Failsafe ITs. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ContractMvcSupport {
    protected MockMvc mvc;
    protected ManagementMapper mapper;
    protected AuthenticationPort authentication;
    protected PersonnelInformationPort personnel;
    protected ApplicationEventPublisher events;
    protected final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    protected JsonNode contract;
    protected final Map<Resource, List<Map<String, Object>>> rows = new EnumMap<>(Resource.class);
    protected CurrentPrincipal admin = new CurrentPrincipal(1L, "admin", List.of("R09"));
    protected List<Map<String, Object>> decisions;

    protected abstract Set<String> operations();

    @BeforeAll
    void loadContractBeforeArgumentDiscovery() throws Exception {
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            contract = new ObjectMapper(new YAMLFactory()).readTree(input);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(ManagementMapper.class);
        authentication = mock(AuthenticationPort.class);
        personnel = mock(PersonnelInformationPort.class);
        rows.clear();
        for (Resource resource : Resource.values()) {
            rows.put(resource, new ArrayList<>());
        }
        rows.get(Resource.ACCOUNTS).add(row("UserAccount", Map.of("account_id", 1L, "login_id", "admin", "personnel_id", 1L)));
        rows.get(Resource.ACCOUNTS).add(row("UserAccount", Map.of("account_id", 2L, "login_id", "local")));
        rows.get(Resource.ROLES).add(row("Role", Map.of("role_code", "R09", "role_name", "관리자")));
        rows.get(Resource.ROLES).add(row("Role", Map.of("role_code", "R01", "role_name", "역할")));
        rows.get(Resource.USER_ROLES).add(row("UserRole", Map.of("assignment_id", 1L, "account_id", 1L, "role_code", "R09")));
        rows.get(Resource.USER_ROLES).add(row("UserRole", Map.of("assignment_id", 2L, "account_id", 2L, "role_code", "R01", "approver_id", 1L)));
        rows.get(Resource.ORGANIZATIONS).add(row("Organization", Map.of("organization_id", 1L, "organization_code", "ORG", "organization_name", "합성 조직")));
        rows.get(Resource.PERSONNEL).add(row("KorusPersonnelSnapshot", Map.of("personnel_id", 1L, "employee_number", "EMP", "person_name", "합성 사용자", "organization_id", 1L)));
        rows.get(Resource.POSITIONS).add(row("OrganizationUserMapping", Map.of("mapping_id", 1L, "personnel_id", 1L, "organization_id", 1L, "position_name", "합성 보직")));
        rows.get(Resource.GROUPS).add(row("CodeGroup", Map.of("code_group_id", 1L, "group_id", "GROUP", "group_name", "합성 그룹")));
        rows.get(Resource.CODES).add(row("DetailCode", Map.of("detail_code_id", 1L, "code_group_id", 1L, "code_value", "ONE", "code_name", "첫 코드", "additional_attributes", "{\"external\":\"mapping\"}")));
        String[] screens = {"SCR-USERS", "SCR-ORGANIZATIONS", "SCR-ROLES", "SCR-USER-ROLES", "SCR-MENU-PERMISSIONS",
            "SCR-MENU-STRUCTURE", "SCR-MENU-INFORMATION", "SCR-CODE-GROUPS", "SCR-DETAIL-CODES"};
        rows.get(Resource.MENUS).add(row("Menu", Map.of("menu_id", 1L, "menu_name", "관리", "display_order", 0)));
        for (int i = 0; i < screens.length; i++) {
            long id = i + 2L;
            rows.get(Resource.MENUS).add(row("Menu", Map.of("menu_id", id, "parent_menu_id", 1L,
                "screen_id", screens[i], "menu_name", screens[i], "url", "/admin/fixture", "display_order", i)));
        }
        for (var menu : rows.get(Resource.MENUS)) {
            long id = ((Number) menu.get("menu_id")).longValue();
            rows.get(Resource.PERMISSIONS).add(row("MenuPermission", Map.of("permission_id", id, "menu_id", id,
                "role_code", "R09", "access_allowed", true)));
        }
        decisions = rows.get(Resource.PERMISSIONS);
        when(mapper.list(any(), anyMap())).thenAnswer(call -> select(call.getArgument(0), call.getArgument(1)));
        when(mapper.get(any(), any())).thenAnswer(call -> {
            Resource resource = call.getArgument(0);
            Object id = call.getArgument(1);
            return rows.get(resource).stream().filter(row -> Objects.equals(row.get(resource.getKey()), id))
                .findFirst().map(LinkedHashMap::new).orElse(null);
        });
        when(mapper.insert(any(), anyMap())).thenAnswer(call -> {
            Resource resource = call.getArgument(0);
            Map<String, Object> values = call.getArgument(1);
            String schema = schema(resource);
            Map<String, Object> inserted = row(schema, values);
            if (resource == Resource.ROLES) {
                if (inserted.get("role_code") == null || rows.get(resource).stream().anyMatch(row -> Objects.equals(row.get("role_code"), inserted.get("role_code")))) {
                    throw new DataIntegrityViolationException("Database internal details must not leak");
                }
            } else {
                inserted.put(resource.getKey(), 100L + rows.get(resource).size());
            }
            rows.get(resource).add(inserted);
            return new LinkedHashMap<>(inserted);
        });
        when(mapper.update(any(), any(), anyMap())).thenAnswer(call -> {
            Resource resource = call.getArgument(0);
            Object id = call.getArgument(1);
            Map<String, Object> values = call.getArgument(2);
            var matching = rows.get(resource).stream().filter(row -> Objects.equals(id, row.get(resource.getKey()))).findFirst();
            if (matching.isEmpty()) {
                return 0;
            }
            matching.get().putAll(values);
            matching.get().put("updated_at", OffsetDateTime.now());
            return 1;
        });
        when(mapper.applicablePermissions(anyLong())).thenAnswer(call -> decisions);
        when(mapper.health()).thenReturn(1);
        when(authentication.authenticate(any())).thenAnswer(call -> {
            String token = call.getArgument(0);
            if ("valid".equals(token)) return admin;
            if ("ordinary".equals(token)) return new CurrentPrincipal(2L, "local", List.of("R01"));
            throw new ApiException(401, "Authentication required", null);
        });
        when(authentication.verify(anyString(), anyString())).thenAnswer(call -> {
            if ("admin".equals(call.getArgument(0)) && "admin".equals(call.getArgument(1))) return 1L;
            throw new ApiException(401, "Invalid credentials", null);
        });
        when(authentication.createSession(1L)).thenReturn("valid");
        when(personnel.personnel(anyMap())).thenAnswer(call -> select(Resource.PERSONNEL, call.getArgument(0)));
        when(personnel.positions(anyLong())).thenAnswer(call -> select(Resource.POSITIONS, Map.of("personnel_id", call.getArgument(0))));
        events = mock(ApplicationEventPublisher.class);
        var management = new ManagementService(mapper, events, json);
        var policy = new MenuPolicy(mapper);
        var roleService = new UserRolesService(management);
        mvc = MockMvcBuilders.standaloneSetup(
            new LoginController(new LoginService(authentication, policy), false), new HealthController(mapper),
            new UsersController(new UsersService(personnel, management, roleService)),
            new OrganizationsController(new OrganizationsService(management)),
            new RolesController(new RolesService(management)), new UserRolesController(roleService),
            new MenuPermissionsController(new MenuPermissionsService(management)),
            new MenuStructureController(new MenuStructureService(management)),
            new MenuInformationController(new MenuInformationService(management)),
            new CodeGroupsController(new CodeGroupsService(management)),
            new DetailCodesController(new DetailCodesService(management)), new AccountsController(new AccountsService(management)))
            // standaloneSetup은 Spring Boot의 Jackson 설정을 쓰지 않으므로 운영과 같은 ISO 날짜 직렬화를 맞춘다.
            .setMessageConverters(new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8),
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                    org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .setControllerAdvice(new ApiErrors()).addInterceptors(new SessionInterceptor(authentication, policy)).build();
    }

    protected Stream<Operation> cases() {
        List<Operation> operations = new ArrayList<>();
        contract.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            JsonNode operation = method.getValue();
            String id = operation.path("operationId").asText();
            if (operations().contains(id)) {
                String url = path.getKey().replaceAll("\\{[^}]+}", "1");
                if (id.equals("updateRole")) url = "/api/roles/R09";
                if (id.equals("updateCodeGroup")) url = "/api/code-groups/GROUP";
                if (id.equals("changeUserRole")) url = "/api/user-roles/2";
                if (id.equals("changeMenuParent")) url = "/api/menu-structure/3/parent";
                operations.add(new Operation(id, method.getKey().toUpperCase(Locale.ROOT), url, operation));
            }
        }));
        assertThat(operations).extracting(Operation::id).containsExactlyInAnyOrderElementsOf(operations());
        return operations.stream();
    }

    @ParameterizedTest(name = "{0}: success or explicit approval gate")
    @MethodSource("cases")
    void operationSuccessOrPending(Operation operation) throws Exception {
        boolean pending = Set.of("createOrganizationRelationship", "updateOrganizationRelationship", "revokeUserRole").contains(operation.id());
        var result = mvc.perform(request(operation, body(operation.id()), "valid"))
            .andExpect(status().is(pending ? 400 : 200)).andExpect(jsonPath("$.success").value(!pending))
            .andExpect(jsonPath("$.meta").isMap()).andReturn();
        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("password_hash", "session_id", "Database internal");
        if (pending) {
            assertThat(response).contains("Pending approval", "fieldErrors");
            verify(mapper, never()).insert(any(), anyMap());
            verify(mapper, never()).update(any(), any(), anyMap());
        } else if (operation.method().equals("GET")) {
            JsonNode payload = json.readTree(response);
            JsonNode schema = operation.schema().path("responses").path("200").path("content").path("application/json").path("schema");
            requiredKeys(schema, payload);
            if (payload.path("data").isArray() && !operation.id().equals("getMenuStructure")) {
                assertThat(payload.path("meta").has("total")).isTrue();
                assertThat(payload.path("meta").has("page")).isTrue();
                assertThat(payload.path("meta").has("size")).isTrue();
            }
            verify(mapper, never()).insert(any(), anyMap());
            verify(mapper, never()).update(any(), any(), anyMap());
        }
        if (operation.id().equals("login")) {
            assertThat(result.getResponse().getHeader("Set-Cookie")).contains("CMSSESSION=valid", "HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        }
    }

    @ParameterizedTest(name = "{0}: unauthenticated")
    @MethodSource("cases")
    void operationRequiresSession(Operation operation) throws Exception {
        if (Set.of("login", "getHealth").contains(operation.id())) return;
        mvc.perform(request(operation, body(operation.id()), null)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap());
        verify(mapper, never()).insert(any(), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @ParameterizedTest(name = "{0}: insufficient role and missing DB menu decision")
    @MethodSource("cases")
    void adminOperationsRequireRoleAndMenu(Operation operation) throws Exception {
        if (!operation.schema().has("x-roles") || operation.url().startsWith("/api/auth/")) return;
        mvc.perform(request(operation, body(operation.id()), "ordinary")).andExpect(status().isForbidden());
        decisions = List.of();
        mvc.perform(request(operation, body(operation.id()), "valid")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
        verify(mapper, never()).insert(any(), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @ParameterizedTest(name = "{0}: strict validation with field errors")
    @MethodSource("cases")
    void validationDoesNotWrite(Operation operation) throws Exception {
        if (operation.schema().has("requestBody")) {
            var body = json.readTree(body(operation.id())).deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("uneditable_source_field", "not editable");
            mvc.perform(request(operation, body.toString(), "valid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("uneditable_source_field"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());
        } else if (operation.schema().path("parameters").toString().contains("\"page\"")) {
            mvc.perform(request(operation, null, "valid").queryParam("page", "bad").queryParam("size", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
        } else if (!operation.id().equals("getHealth")) {
            mvc.perform(request(operation, null, "bad")).andExpect(status().isUnauthorized());
        }
        verify(mapper, never()).insert(any(), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    protected MockHttpServletRequestBuilder request(Operation operation, String body, String cookie) {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(operation.method()), operation.url());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (cookie != null) request.cookie(new Cookie("CMSSESSION", cookie));
        return request;
    }

    protected String body(String operation) {
        return switch (operation) {
            case "login" -> "{\"login_id\":\"admin\",\"password\":\"admin\"}";
            case "createOrganizationRelationship" -> "{\"organization_code\":\"ORG\",\"parent_organization_id\":1}";
            case "updateOrganizationRelationship" -> "{\"effective_start\":\"2026-01-01\"}";
            case "updateUserUseStatus" -> "{\"use_status\":\"opaque-fixture-value\"}";
            case "updateUserRoles" -> "{\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":2}]}";
            case "createRole" -> "{\"role_code\":\"R08\",\"role_name\":\"새 이름\",\"purpose\":\"목적\",\"assignment_criteria\":\"기준\",\"default_data_scope\":\"저장만\"}";
            case "updateRole" -> "{\"role_name\":\"새 이름\",\"assignment_criteria\":\"수정 기준\",\"default_data_scope\":\"저장만\"}";
            case "grantUserRoles" -> "{\"account_id\":2,\"assignments\":[{\"role_code\":\"R09\",\"approver_id\":1,\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\",\"assignment_source\":\"opaque-fixture-value\"}]}";
            case "changeUserRole" -> "{\"role_code\":\"R09\",\"approver_id\":1,\"valid_start\":\"2026-02-01\",\"valid_end\":\"2026-10-01\",\"assignment_source\":\"opaque-fixture-value\"}";
            case "revokeUserRole" -> "{\"approver_id\":2,\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\"}";
            case "saveMenuPermissions" -> "{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":true}]}";
            case "changeMenuParent" -> "{\"parent_menu_id\":2}";
            case "reorderSiblingMenus" -> "{\"parent_menu_id\":1,\"items\":[{\"menu_id\":2,\"display_order\":9},{\"menu_id\":3,\"display_order\":8}]}";
            case "createMenuInformation" -> "{\"menu_name\":\"표시정보\",\"screen_id\":\"FIXTURE\",\"url\":\"/admin/fixture\",\"icon\":\"folder\",\"business_category\":\"관리\",\"description\":\"설명\"}";
            case "updateMenuInformation" -> "{\"menu_name\":\"새 정보\",\"url\":\"/admin/fixture\"}";
            case "createCodeGroup" -> "{\"group_id\":\"NEW\",\"group_name\":\"새 그룹\",\"managing_organization_id\":1}";
            case "updateCodeGroup" -> "{\"group_id\":\"RENAMED\",\"group_name\":\"새 이름\"}";
            case "createDetailCode" -> "{\"code_group_id\":1,\"code_value\":\"TWO\",\"code_name\":\"둘\",\"parent_detail_code_id\":1,\"display_order\":2,\"additional_attributes\":{\"external\":\"mapping\"},\"valid_start\":\"2026-01-01\",\"valid_end\":\"2026-12-31\"}";
            case "updateDetailCode" -> "{\"code_value\":\"RENAMED\",\"code_name\":\"새 이름\",\"additional_attributes\":{\"external\":\"new\"}}";
            case "createAccount" -> "{\"login_id\":\"new-local\",\"password\":\"test-secret\"}";
            case "updateAccount" -> "{\"login_id\":\"renamed\"}";
            default -> null;
        };
    }

    protected void requiredKeys(JsonNode schema, JsonNode value) {
        if (schema.has("$ref")) {
            requiredKeys(contract.at(schema.path("$ref").asText().substring(1)), value);
            return;
        }
        schema.path("allOf").forEach(part -> requiredKeys(part, value));
        schema.path("required").forEach(key -> assertThat(value.has(key.asText())).as(key.asText()).isTrue());
        if (value.isArray()) value.forEach(item -> requiredKeys(schema.path("items"), item));
        if (value.isObject()) schema.path("properties").fields().forEachRemaining(field -> {
            if (value.has(field.getKey()) && !value.get(field.getKey()).isNull()) requiredKeys(field.getValue(), value.get(field.getKey()));
        });
    }

    protected Map<String, Object> row(String schema, Map<String, Object> values) {
        Map<String, Object> row = new LinkedHashMap<>();
        contract.path("components").path("schemas").path(schema).path("properties").fieldNames().forEachRemaining(key -> row.put(key, null));
        row.put("created_at", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        row.put("updated_at", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        row.putAll(values);
        return row;
    }

    protected List<Map<String, Object>> select(Resource resource, Map<String, Object> filters) {
        return rows.get(resource).stream().filter(row -> filters.entrySet().stream().allMatch(filter -> Objects.equals(row.get(filter.getKey()), filter.getValue())))
            .map(row -> (Map<String, Object>) new LinkedHashMap<>(row)).toList();
    }

    private String schema(Resource resource) {
        return switch (resource) {
            case ACCOUNTS -> "UserAccount"; case ROLES -> "Role"; case USER_ROLES -> "UserRole";
            case PERMISSIONS -> "MenuPermission"; case MENUS -> "Menu"; case GROUPS -> "CodeGroup";
            case CODES -> "DetailCode"; case ORGANIZATIONS -> "Organization";
            case PERSONNEL -> "KorusPersonnelSnapshot"; case POSITIONS -> "OrganizationUserMapping";
        };
    }

    protected record Operation(String id, String method, String url, JsonNode schema) {
        @Override public String toString() { return id; }
    }
}
