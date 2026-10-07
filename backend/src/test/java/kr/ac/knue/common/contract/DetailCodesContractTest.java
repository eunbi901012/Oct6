package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class DetailCodesContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("listDetailCodes", "createDetailCode", "updateDetailCode"); }

    @Test void jsonAttributesAreObjectAndCodeRenamePreservesKey() throws Exception {
        mvc.perform(patch("/api/detail-codes/1").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content(body("updateDetailCode")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.detail_code_id").value(1))
            .andExpect(jsonPath("$.data.code_value").value("RENAMED"))
            .andExpect(jsonPath("$.data.additional_attributes.external").value("new"));
        mvc.perform(post("/api/detail-codes").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"additional_attributes\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("additional_attributes"));
        mvc.perform(patch("/api/detail-codes/999").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"code_name\":\"missing\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("detail_code_id"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested record does not exist"));
    }
}
