package kr.ac.knue.common.contract;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;

public class LoginContractTest extends ContractMvcSupport {
    @Test void tlsCookieHasSecureFlagWhenConfigured() throws Exception {
        var tls = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new kr.ac.knue.common.api.LoginController(new kr.ac.knue.common.application.LoginService(authentication,
                new kr.ac.knue.common.application.MenuPolicy(mapper)), true)).setControllerAdvice(new kr.ac.knue.common.api.ApiErrors()).build();
        var result = tls.perform(post("/api/auth/login")
            .contentType("application/json")
            .content(body("login")))
            .andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getHeader("Set-Cookie"))
            .contains("Secure", "HttpOnly", "SameSite=Lax");
    }
    protected Set<String> operations() { return Set.of("login", "logout", "getCurrentUser"); }

    @Test void invalidCredentialsNeverIssueCookieOrSession() throws Exception {
        mvc.perform(post("/api/auth/login")
            .contentType("application/json")
            .content("{\"login_id\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist("Set-Cookie"));
        verify(authentication, never()).createSession(anyLong());
    }

    @Test void objectCredentialAndMissingPasswordReportCorrectField() throws Exception {
        mvc.perform(post("/api/auth/login")
            .contentType("application/json")
            .content("{\"login_id\":{},\"password\":\"admin\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("login_id"));
        mvc.perform(post("/api/auth/login")
            .contentType("application/json")
            .content("{\"login_id\":\"admin\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        verify(authentication, never()).createSession(anyLong());
    }

    @Test void meAndLogoutDoNotRequireAnyMenuPermission() throws Exception {
        decisions = java.util.List.of();
        mvc.perform(get("/api/auth/me").cookie(new Cookie("CMSSESSION", "ordinary")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[0]").value("R01"))
            .andExpect(jsonPath("$.data.allowedMenus").isEmpty());
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("CMSSESSION", "ordinary")))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(authentication).terminate("ordinary");
    }
}
