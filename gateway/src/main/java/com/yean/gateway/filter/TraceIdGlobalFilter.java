package com.yean.gateway.filter;

import com.yean.common.trace.TraceConstants;
import com.yean.common.trace.TraceIds;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * 网关入口最早挂 TraceId：透传或生成，写入下游请求头、响应头、MDC 与 Reactor Context。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TraceConstants.HEADER);
        String traceId = TraceIds.resolveOrCreate(incoming);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(TraceConstants.HEADER);
                    h.add(TraceConstants.HEADER, traceId);
                })
                .build();

        exchange.getResponse().getHeaders().set(TraceConstants.HEADER, traceId);

        ServerWebExchange mutated = exchange.mutate().request(request).build();
        TraceIds.put(traceId);
        return chain.filter(mutated)
                .contextWrite(Context.of(TraceConstants.MDC_KEY, traceId))
                .doFinally(signal -> TraceIds.clear());
    }

    @Override
    public int getOrder() {
        // 早于鉴权(-100) / Sentinel，保证登录与白名单也有 TraceId
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
