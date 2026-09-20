package com.yean.gateway.auth;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.gateway.auth.dto.AuthUser;
import com.yean.gateway.auth.dto.TokenVO;
import com.yean.gateway.jwt.JwtService;
import com.yean.gateway.jwt.JwtUser;
import com.yean.gateway.jwt.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthSupport {

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;

    public Mono<JwtUser> requireUser(ServerRequest request) {
        return Mono.fromCallable(() -> jwtService.parse(bearer(request)))
                .flatMap(user -> tokenBlacklist.contains(user.getJti()).flatMap(blocked -> {
                    if (Boolean.TRUE.equals(blocked)) {
                        return Mono.error(new BizException(ErrorEnum.TOKEN_BLACKLISTED));
                    }
                    return Mono.just(user);
                }));
    }

    public Mono<Void> addToBlacklist(JwtUser user, ServerRequest request) {
        return tokenBlacklist.add(user.getJti(), jwtService.remainingTtl(bearer(request)));
    }

    public TokenVO issue(AuthUser user) {
        return issue(JwtUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .identityType(user.getRole())
                .deptId(user.getDeptId())
                .build());
    }

    public TokenVO issue(JwtUser user) {
        String token = jwtService.issue(user);
        return TokenVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.expireSeconds())
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    public String bearer(ServerRequest request) {
        return JwtService.bearer(request.exchange().getRequest())
                .orElseThrow(() -> new BizException(ErrorEnum.UNAUTHORIZED));
    }
}
