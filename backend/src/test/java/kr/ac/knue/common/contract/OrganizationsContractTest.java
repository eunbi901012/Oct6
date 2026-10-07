package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class OrganizationsContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("searchOrganizations", "createOrganizationRelationship", "updateOrganizationRelationship"); }

    @Test void relationshipGateAndOriginalFieldValidationDoNotTouchSource() throws Exception {
        mvc.perform(post("/api/organizations").cookie(new Cookie("CMSSESSION", "valid")).contentType("application/json").content("{\"organization_code\":\"ORG\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OQ-001")));
        mvc.perform(patch("/api/organizations/1").cookie(new Cookie("CMSSESSION", "valid")).contentType("application/json").content("{\"effective_start\":[],\"organization_type\":\"overwrite\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("effective_start"));
        verify(mapper, never()).insert(any(), anyMap());
        verify(mapper, never()).update(any(), any(), anyMap());
    }

    @Test void organizationFiltersUseSameDatabaseRows() throws Exception {
        mvc.perform(get("/api/organizations").param("organizationId", "1").param("organizationCode", "ORG").cookie(new Cookie("CMSSESSION", "valid")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1))
            .andExpect(jsonPath("$.data[0].organization_name").value("합성 조직"));
    }
}
