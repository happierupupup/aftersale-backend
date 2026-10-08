package com.yean.gateway.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 登录走 Gateway {@code RouterFunction}，不经 Sentinel 网关过滤器，故单独用资源限流。
 */
@Component
public class LoginRateLimitFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!HttpMethod.POST.equals(request.getMethod())
                || !"/api/auth/login".equals(request.getURI().getPath())) {
            return chain.filter(exchange);
        }
        return Mono.defer(() -> {
            Entry entry = null;
            try {
                entry = SphU.entry(SentinelRulesConfig.RES_LOGIN);
                Entry captured = entry;
                return chain.filter(exchange).doFinally(signal -> {
                    if (captured != null) {
                        captured.exit();
                    }
                });
            } catch (BlockException e) {
                if (entry != null) {
                    entry.exit();
                }
                return Mono.error(new BizException(ErrorEnum.TOO_MANY_REQUESTS));
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
