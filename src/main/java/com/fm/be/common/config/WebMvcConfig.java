package com.fm.be.common.config;

import com.fm.be.common.interceptor.LoggingInterceptor;
import com.fm.be.common.interceptor.RateLimitInterceptor;
import com.fm.be.common.interceptor.TenantInterceptor;
import com.fm.be.common.security.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC configuration that registers custom interceptors
 * and argument resolvers.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoggingInterceptor loggingInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final TenantInterceptor tenantInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/health/**", "/v1/auth/**");

        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/health/**", "/v1/auth/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
