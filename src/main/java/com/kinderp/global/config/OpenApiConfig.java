package com.kinderp.global.config;

import com.kinderp.global.security.jwt.JwtTokenProvider;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
@OpenAPIDefinition(
        info = @Info(
                title = "KinderP API",
                version = "v1",
                description = "원장, 교사, 학부모를 위한 유치원 운영 관리 API 계약 문서입니다.",
                contact = @Contact(name = "KinderP")
        ),
        servers = {
                @Server(url = "/", description = "Current server")
        }
)
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi apiV1GroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("api-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public OpenAPI kindergartenErpOpenApi(JwtTokenProvider jwtTokenProvider) {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(jwtTokenProvider.getAccessTokenCookieName())
                                .description("로그인 후 발급되는 HTTP-only access token cookie")))
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("KinderP API")
                        .version("v1")
                        .description("백엔드 포트폴리오용 유치원 ERP API 문서"));
    }
}
