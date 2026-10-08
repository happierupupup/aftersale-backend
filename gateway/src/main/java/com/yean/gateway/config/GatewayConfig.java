package com.yean.gateway.config;

import com.yean.common.trace.TraceConstants;
import com.yean.common.trace.TraceIds;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties({GatewayProperties.class, WebProperties.class})
public class GatewayConfig {

    @Bean
    public WebClient portalWebClient(GatewayProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getPortalUrl())
                .filter((request, next) -> Mono.deferContextual(ctx -> {
                    String traceId = ctx.getOrDefault(TraceConstants.MDC_KEY, null);
                    if (traceId == null || traceId.isBlank()) {
                        traceId = TraceIds.current();
                    }
                    if (traceId == null || traceId.isBlank()) {
                        return next.exchange(request);
                    }
                    ClientRequest withTrace = ClientRequest.from(request)
                            .header(TraceConstants.HEADER, traceId)
                            .build();
                    return next.exchange(withTrace);
                }))
                .build();
    }
}
