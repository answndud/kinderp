package com.kinderp.global.config;

import com.kinderp.global.security.RoleRedirectInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleRedirectInterceptor roleRedirectInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleRedirectInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/signup",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/kindergarten/create",
                        "/kindergarten/select",
                        "/applications/pending",
                        "/api/v1/auth/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/error",
                        "/h2-console/**"
                );
    }
}
