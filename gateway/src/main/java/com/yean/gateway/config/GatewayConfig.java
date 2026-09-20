package com.yean.gateway.config;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({GatewayProperties.class, WebProperties.class})
public class GatewayConfig {

    @Bean
    public WebClient portalWebClient(GatewayProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getPortalUrl())
                .build();
    }
}
