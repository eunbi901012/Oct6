package kr.ac.knue.common.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ac.knue.common.application.MenuPolicy;
import kr.ac.knue.common.domain.CurrentPrincipal;
import kr.ac.knue.common.domain.ports.AuthenticationPort;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Authenticates protected operations before shape validation and enforces persisted menu/role requirements. */
@Component
public class SessionInterceptor implements HandlerInterceptor {
    public static final String PRINCIPAL = "cmsPrincipal";
    private final AuthenticationPort authentication;
    private final MenuPolicy policy;

    /** Binds session verification and the shared navigation/API authorization decision. */
    public SessionInterceptor(AuthenticationPort authentication, MenuPolicy policy) {
        this.authentication = authentication;
        this.policy = policy;
    }

    /** Allows only declared public operations; management routes require both identity and menu policy. */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if ((path.equals("/api/auth/login") && request.getMethod().equals("POST"))
                || (path.equals("/api/health") && request.getMethod().equals("GET"))) {
            requestShape(request, handler);
            return true;
        }
        CurrentPrincipal principal = authentication.authenticate(cookie(request));
        request.setAttribute(PRINCIPAL, principal);
        if (handler instanceof HandlerMethod method) {
            MenuAccess access = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), MenuAccess.class);
            if (access == null) {
                access = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), MenuAccess.class);
            }
            if (access != null) {
                policy.require(principal, access.value(), access.roles());
            }
        }
        requestShape(request, handler);
        return true;
    }

    private void requestShape(HttpServletRequest request, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        boolean queryAllowed = java.util.Arrays.stream(method.getMethodParameters())
            .anyMatch(parameter -> parameter.hasParameterAnnotation(RequestParam.class));
        for (var parameter : request.getParameterMap().entrySet()) {
            if (!queryAllowed || parameter.getValue().length != 1) {
                throw new ApiException(400, "Unknown or repeated request parameter", parameter.getKey());
            }
        }
        boolean bodyAllowed = java.util.Arrays.stream(method.getMethodParameters())
            .anyMatch(parameter -> parameter.hasParameterAnnotation(RequestBody.class));
        if (!bodyAllowed && (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null)) {
            throw new ApiException(400, "Body is not permitted by this operation", "request");
        }
    }

    /** Reads the CMS credential only from the dedicated cookie, never from query parameters. */
    public static String cookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("CMSSESSION")) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
