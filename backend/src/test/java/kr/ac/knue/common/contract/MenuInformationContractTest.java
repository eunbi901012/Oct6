package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class MenuInformationContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("listMenuInformation", "createMenuInformation", "updateMenuInformation"); }

    @Test void executionInformationCannotWriteStructureAndMissingIdReturns400() throws Exception {
        mvc.perform(post("/api/menu-information").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"menu_name\":\"name\",\"parent_menu_id\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("parent_menu_id"));
        mvc.perform(patch("/api/menu-information/1").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"display_order\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("display_order"));
        mvc.perform(patch("/api/menu-information/999").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"url\":\"/fixture\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("menu_id"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested record does not exist"));
    }
}
