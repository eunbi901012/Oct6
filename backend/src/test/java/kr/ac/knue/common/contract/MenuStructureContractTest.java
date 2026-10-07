package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class MenuStructureContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("getMenuStructure", "changeMenuParent", "reorderSiblingMenus"); }

    @Test void structureCannotEditExecutionFieldsOrRetargetParentDuringOrdering() throws Exception {
        mvc.perform(patch("/api/menu-structure/3/parent").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"parent_menu_id\":2,\"menu_name\":\"overwrite\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("menu_name"));
        mvc.perform(put("/api/menu-structure/order").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"items\":[{\"menu_id\":2,\"display_order\":1,\"parent_menu_id\":3}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].parent_menu_id"));
    }

    @Test void unknownMenuReturns400AndInvalidSiblingSetAreRejected() throws Exception {
        mvc.perform(patch("/api/menu-structure/999/parent").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"parent_menu_id\":2}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("menu_id"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Requested record does not exist"));
        mvc.perform(put("/api/menu-structure/order").cookie(new Cookie("CMSSESSION", "valid"))
            .contentType("application/json")
            .content("{\"parent_menu_id\":2,\"items\":[{\"menu_id\":3,\"display_order\":1}]}"))
            .andExpect(status().isBadRequest());
    }
}
