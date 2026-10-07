package kr.ac.knue.common.contract;

import java.util.Map;
import java.util.Set;
import kr.ac.knue.common.application.Resource;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class CodeGroupsContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("listCodeGroups", "createCodeGroup", "updateCodeGroup"); }

    @Test void businessIdentifierRenamePreservesSurrogateAndCodeReferences() throws Exception {
        mvc.perform(patch("/api/code-groups/GROUP").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content(body("updateCodeGroup")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code_group_id").value(1))
            .andExpect(jsonPath("$.data.group_id").value("RENAMED"));
        mvc.perform(get("/api/detail-codes").param("groupId", "RENAMED").cookie(new Cookie("CMSSESSION", "valid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code_group_id").value(1));
    }

    @Test void ambiguousBusinessKeyNeverPicksAnArbitraryRow() throws Exception {
        rows.get(Resource.GROUPS).add(row("CodeGroup", Map.of("code_group_id", 2L, "group_id", "GROUP")));
        mvc.perform(patch("/api/code-groups/GROUP").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"group_name\":\"ambiguous\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ambiguous")));
        mvc.perform(patch("/api/code-groups/ABSENT").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"group_name\":\"missing\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("groupId"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested code group does not exist"));
    }
}
