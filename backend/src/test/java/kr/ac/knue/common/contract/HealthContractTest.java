package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class HealthContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("getHealth"); }

    @Test void databaseFailureNeverReportsHealthyOrLeaksSQLException() throws Exception {
        when(mapper.health()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("jdbc://secret-password"));
        mvc.perform(get("/api/health")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-password"))));
    }
}
