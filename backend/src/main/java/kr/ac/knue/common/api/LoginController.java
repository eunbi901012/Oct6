package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import kr.ac.knue.common.application.LoginService;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Exposes session authentication without management-menu requirements on identity and logout operations. */
@RestController

public class LoginController {
    private final LoginService service;
    private final boolean secure;

    /** Binds authentication and environment-specific Secure cookie configuration. */
    public LoginController(LoginService service, @Value("${cms.cookie-secure:true}") boolean secure) {
        this.service = service;
        this.secure = secure;
    }

    /** Verifies exact credential input and emits the token only as an HttpOnly SameSite cookie. */
    @PostMapping("/api/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody JsonNode body) {
        var values = Input.object(body, "login_id:string password:string", "login_id", "password");
        var input = kr.ac.knue.common.api.dto.LoginInput.from(values);
        String token = service.login(input.login_id(), input.password());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(token).build().toString())
            .body(Responses.success(Map.of()));
    }

    /** Returns the authenticated identity and permitted navigation without credential data. */
    @GetMapping("/api/auth/me")
    public Map<String, Object> me(@RequestAttribute(SessionInterceptor.PRINCIPAL) CurrentPrincipal actor) {
        return Responses.success(service.me(actor));
    }

    /** Terminates the current session and expires its cookie without business account or role changes. */
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        service.logout(SessionInterceptor.cookie(request));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("").maxAge(0).build().toString())
            .body(Responses.success(Map.of()));
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String token) {
        return ResponseCookie.from("CMSSESSION", token).httpOnly(true).sameSite("Lax").secure(secure).path("/");
    }
}
