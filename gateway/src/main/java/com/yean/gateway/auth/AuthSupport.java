package com.yean.gateway.auth;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.gateway.auth.dto.AuthUser;
import com.yean.gateway.auth.dto.TokenVO;
import com.yean.gateway.config.GatewayProperties;
import com.yean.gateway.jwt.JwtService;
import com.yean.gateway.jwt.JwtUser;
import com.yean.gateway.jwt.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthSupport {

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final GatewayProperties properties;

    /** 业务接口：Bearer Access */
    public Mono<JwtUser> requireAccessUser(ServerRequest request) {
        return Mono.fromCallable(() -> jwtService.parseAccess(bearer(request)))
                .flatMap(this::rejectIfBlacklisted);
    }

    /** 续期：HttpOnly Refresh Cookie */
    public Mono<JwtUser> requireRefreshUser(ServerRequest request) {
        String refresh = readRefreshCookie(request.exchange());
        if (refresh == null || refresh.isBlank()) {
            return Mono.error(new BizException(ErrorEnum.UNAUTHORIZED));
        }
        return Mono.fromCallable(() -> jwtService.parseRefresh(refresh))
                .flatMap(this::rejectIfBlacklisted);
    }

    private Mono<JwtUser> rejectIfBlacklisted(JwtUser user) {
        return tokenBlacklist.contains(user.getJti()).flatMap(blocked -> {
            if (Boolean.TRUE.equals(blocked)) {
                return Mono.error(new BizException(ErrorEnum.TOKEN_BLACKLISTED));
            }
            return Mono.just(user);
        });
    }

    public Mono<Void> blacklistAccess(ServerRequest request) {
        return JwtService.bearer(request.exchange().getRequest())
                .map(token -> {
                    try {
                        JwtUser user = jwtService.parseIgnoreExpiry(token);
                        Duration ttl = jwtService.remainingTtl(token);
                        if (ttl.isZero() || ttl.isNegative()) {
                            ttl = Duration.ofSeconds(jwtService.accessExpireSeconds());
                        }
                        return tokenBlacklist.add(user.getJti(), ttl);
                    } catch (BizException e) {
                        return Mono.<Void>empty();
                    }
                })
                .orElse(Mono.empty());
    }

    public Mono<Void> blacklistRefresh(ServerRequest request) {
        String refresh = readRefreshCookie(request.exchange());
        if (refresh == null || refresh.isBlank()) {
            return Mono.empty();
        }
        try {
            JwtUser user = jwtService.parseIgnoreExpiry(refresh);
            if (!JwtService.TYPE_REFRESH.equalsIgnoreCase(user.getTokenType())) {
                return Mono.empty();
            }
            Duration ttl = jwtService.remainingTtl(refresh);
            if (ttl.isZero() || ttl.isNegative()) {
                ttl = Duration.ofSeconds(jwtService.refreshExpireSeconds());
            }
            return tokenBlacklist.add(user.getJti(), ttl);
        } catch (BizException e) {
            return Mono.empty();
        }
    }

    /** 续期成功：拉黑旧 Refresh（轮换） */
    public Mono<Void> blacklistRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.empty();
        }
        try {
            JwtUser user = jwtService.parseIgnoreExpiry(refreshToken);
            if (!JwtService.TYPE_REFRESH.equalsIgnoreCase(user.getTokenType())) {
                return Mono.empty();
            }
            Duration ttl = jwtService.remainingTtl(refreshToken);
            if (ttl.isZero() || ttl.isNegative()) {
                ttl = Duration.ofSeconds(jwtService.refreshExpireSeconds());
            }
            return tokenBlacklist.add(user.getJti(), ttl);
        } catch (BizException e) {
            return Mono.empty();
        }
    }

    public TokenBundle issueBundle(AuthUser user) {
        return issueBundle(JwtUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .identityType(user.getRole())
                .deptId(user.getDeptId())
                .build());
    }

    public TokenBundle issueBundle(JwtUser user) {
        JwtUser base = JwtUser.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole())
                .identityType(user.getIdentityType() == null ? user.getRole() : user.getIdentityType())
                .deptId(user.getDeptId())
                .build();
        String access = jwtService.issueAccess(base);
        String refresh = jwtService.issueRefresh(base);
        TokenVO vo = TokenVO.builder()
                .token(access)
                .tokenType("Bearer")
                .expiresIn(jwtService.accessExpireSeconds())
                .userId(base.getUserId())
                .username(base.getUsername())
                .role(base.getRole())
                .build();
        return new TokenBundle(vo, refresh, buildRefreshCookie(refresh));
    }

    public ResponseCookie buildRefreshCookie(String refreshToken) {
        GatewayProperties.Jwt jwt = properties.getJwt();
        return ResponseCookie.from(jwt.getRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(jwt.isRefreshCookieSecure())
                .sameSite(jwt.getRefreshCookieSameSite())
                .path(jwt.getRefreshCookiePath())
                .maxAge(Duration.ofSeconds(jwt.getRefreshExpireSeconds()))
                .build();
    }

    public ResponseCookie clearRefreshCookie() {
        GatewayProperties.Jwt jwt = properties.getJwt();
        return ResponseCookie.from(jwt.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(jwt.isRefreshCookieSecure())
                .sameSite(jwt.getRefreshCookieSameSite())
                .path(jwt.getRefreshCookiePath())
                .maxAge(Duration.ZERO)
                .build();
    }

    public String readRefreshCookie(ServerWebExchange exchange) {
        String name = properties.getJwt().getRefreshCookieName();
        if (exchange.getRequest().getCookies().getFirst(name) == null) {
            return null;
        }
        return exchange.getRequest().getCookies().getFirst(name).getValue();
    }

    public String bearer(ServerRequest request) {
        return JwtService.bearer(request.exchange().getRequest())
                .orElseThrow(() -> new BizException(ErrorEnum.UNAUTHORIZED));
    }

    public record TokenBundle(TokenVO access, String refreshToken, ResponseCookie refreshCookie) {
    }
}
