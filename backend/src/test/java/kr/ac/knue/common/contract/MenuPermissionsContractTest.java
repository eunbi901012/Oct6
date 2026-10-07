package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class MenuPermissionsContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("listMenuPermissions", "saveMenuPermissions"); }

    @Test void nestedBooleanTypeAndExecutionInformationAreNotCoerced() throws Exception {
        mvc.perform(put("/api/menu-permissions").cookie(new Cookie("CMSSESSION", "valid")).contentType("application/json").content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":\"true\"}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("permissions[0].access_allowed"));
        mvc.perform(put("/api/menu-permissions").cookie(new Cookie("CMSSESSION", "valid")).contentType("application/json").content("{\"permissions\":[{\"menu_id\":2,\"role_code\":\"R01\",\"access_allowed\":true,\"url\":\"/overwrite\"}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("permissions[0].url"));
    }

    @Test void menuFilterAndOpaqueCompositionAreSeparate() throws Exception {
        mvc.perform(get("/api/menu-permissions").param("menuId", "2").param("roleCode", "R09").cookie(new Cookie("CMSSESSION", "valid")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1))
            .andExpect(jsonPath("$.data[0].menu_id").value(2));
    }
}
