package com.kinderp.global.config;

import lombok.RequiredArgsConstructor;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Tomcat native request limits를 애플리케이션 정책과 일치시킨다. */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RequestLimitProperties.class)
public class RequestLimitConfig {

    private final RequestLimitProperties properties;

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> requestLimitCustomizer() {
        return factory -> factory.addConnectorCustomizers(this::customizeConnector);
    }

    private void customizeConnector(Connector connector) {
        String maxBytes = Long.toString(properties.getMaxRequestSize().toBytes());
        connector.setProperty("maxHeaderCount", Integer.toString(properties.getMaxHeaderCount()));
        connector.setProperty("maxParameterCount", Integer.toString(properties.getMaxParameterCount()));
        connector.setProperty("maxPostSize", maxBytes);
        connector.setProperty("maxSwallowSize", maxBytes);
    }
}
