package com.yean.gateway.filter;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.gateway.config.GatewayProperties;
import com.yean.gateway.jwt.JwtService;
import com.yean.gateway.jwt.JwtUser;
import com.yean.gateway.jwt.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final GatewayProperties properties;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || whitelist(request.getPath())) {
            return chain.filter(exchange);
        }
        String token = JwtService.bearer(request).orElse(null);
        if (token == null) {
            return Mono.error(new BizException(ErrorEnum.UNAUTHORIZED));
        }
        JwtUser user;
        try {
            user = jwtService.parseAccess(token);
        } catch (BizException e) {
            return Mono.error(e);
        }
        return tokenBlacklist.contains(user.getJti()).flatMap(blocked -> {
            if (Boolean.TRUE.equals(blocked)) {
                return Mono.error(new BizException(ErrorEnum.TOKEN_BLACKLISTED));
            }
            ServerHttpRequest mutated = request.mutate()
                    .headers(headers -> {
                        headers.remove(HttpHeaders.AUTHORIZATION);
                        headers.remove(JwtService.HEADER_USER_ID);
                        headers.remove(JwtService.HEADER_USERNAME);
                        headers.remove(JwtService.HEADER_ROLE);
                        headers.remove(JwtService.HEADER_IDENTITY_TYPE);
                        headers.remove(JwtService.HEADER_DEPT_ID);
                        put(headers, JwtService.HEADER_USER_ID, user.getUserId());
                        put(headers, JwtService.HEADER_USERNAME, user.getUsername());
                        put(headers, JwtService.HEADER_ROLE, user.getRole());
                        put(headers, JwtService.HEADER_IDENTITY_TYPE,
                                user.getIdentityType() == null ? user.getRole() : user.getIdentityType());
                        put(headers, JwtService.HEADER_DEPT_ID, user.getDeptId());
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        });
    }

    private boolean whitelist(PathContainer path) {
        String value = path.value();
        List<String> patterns = properties.getAuth().getWhitelist();
        if (patterns == null) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> MATCHER.match(pattern, value));
    }

    private static void put(HttpHeaders headers, String name, Object value) {
        if (value != null) {
            headers.set(name, String.valueOf(value));
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
