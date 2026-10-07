package kr.ac.knue.common.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies the session/menu interceptor to the complete API surface. */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final SessionInterceptor interceptor;

    /** Binds the application-wide API security interceptor. */
    public WebConfiguration(SessionInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /** Registers protected/public operation handling without excluding management routes. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
