package kr.ac.knue.common.contract;

import java.util.Set;
import kr.ac.knue.common.domain.ChangeTrace;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import jakarta.servlet.http.Cookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ChangeTraceContractTest extends ContractMvcSupport {
    protected Set<String> operations() { return Set.of("updateRole", "createAccount"); }

    @Test void conceptualBeforeAfterActorTimeAndUnapprovedReasonAreExplicit() throws Exception {
        mvc.perform(patch("/api/roles/R09").cookie(new Cookie("CMSSESSION", "valid")).contentType("application/json").content("{\"role_name\":\"새 이름\"}"))
            .andExpect(status().isOk());
        var trace = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(trace.capture());
        assertThat(trace.getValue()).isInstanceOf(ChangeTrace.class);
        ChangeTrace value = (ChangeTrace) trace.getValue();
        assertThat(value.before_values()).containsEntry("role_name", "관리자");
        assertThat(value.after_values()).containsEntry("role_name", "새 이름");
        assertThat(value.actor_id()).isEqualTo(1L);
        assertThat(value.processed_at()).isNotNull();
        assertThat(value.reason()).isNull();
    }

    @Test void passwordHashNeverEntersChangeContext() throws Exception {
        mvc.perform(post("/api/accounts").cookie(new Cookie("CMSSESSION", "valid")).contentType("application/json").content(body("createAccount")))
            .andExpect(status().isOk());
        var trace = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(trace.capture());
        ChangeTrace value = (ChangeTrace) trace.getValue();
        assertThat(value.before_values()).doesNotContainKeys("password", "password_hash", "session_id");
        assertThat(value.after_values()).doesNotContainKeys("password", "password_hash", "session_id");
    }
}
