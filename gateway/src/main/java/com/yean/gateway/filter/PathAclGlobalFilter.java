package com.yean.gateway.filter;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.enums.RoleEnum;
import com.yean.common.exception.BizException;
import com.yean.gateway.config.GatewayProperties;
import com.yean.gateway.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 路径级 ACL：在 {@link AuthGlobalFilter} 注入角色头之后校验。
 * 配置见 {@code gateway.auth.acl.rules}；未命中规则放行，命中则角色须匹配（ADMIN 恒通）。
 */
@Component
@RequiredArgsConstructor
public class PathAclGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final GatewayProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayProperties.Acl acl = properties.getAuth().getAcl();
        if (acl == null || !acl.isEnabled() || CollectionUtils.isEmpty(acl.getRules())) {
            return chain.filter(exchange);
        }
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod()) || whitelist(exchange.getRequest().getPath())) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod() == null
                ? null : exchange.getRequest().getMethod().name();
        GatewayProperties.AclRule hit = match(acl.getRules(), path, method);
        if (hit == null) {
            return chain.filter(exchange);
        }
        String role = exchange.getRequest().getHeaders().getFirst(JwtService.HEADER_ROLE);
        if (allowed(hit, role)) {
            return chain.filter(exchange);
        }
        return Mono.error(new BizException(ErrorEnum.FORBIDDEN));
    }

    private GatewayProperties.AclRule match(List<GatewayProperties.AclRule> rules, String path, String method) {
        for (GatewayProperties.AclRule rule : rules) {
            if (!StringUtils.hasText(rule.getPath()) || !MATCHER.match(rule.getPath(), path)) {
                continue;
            }
            List<String> methods = rule.getMethods();
            if (CollectionUtils.isEmpty(methods)) {
                return rule;
            }
            if (method != null && methods.stream().anyMatch(m -> m.equalsIgnoreCase(method))) {
                return rule;
            }
        }
        return null;
    }

    private static boolean allowed(GatewayProperties.AclRule rule, String role) {
        if (!StringUtils.hasText(role)) {
            return false;
        }
        if (RoleEnum.ADMIN.name().equalsIgnoreCase(role)) {
            return true;
        }
        List<String> roles = rule.getRoles();
        if (CollectionUtils.isEmpty(roles)) {
            return false;
        }
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }

    private boolean whitelist(PathContainer path) {
        String value = path.value();
        List<String> patterns = properties.getAuth().getWhitelist();
        if (patterns == null) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> MATCHER.match(pattern, value));
    }

    @Override
    public int getOrder() {
        // 紧跟 AuthGlobalFilter(-100) 之后
        return -90;
    }
}
