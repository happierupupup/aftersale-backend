package com.yean.gateway.handler;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.common.result.R;
import com.yean.gateway.auth.AuthSupport;
import com.yean.gateway.auth.PortalAuthClient;
import com.yean.gateway.auth.dto.LoginDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LoginHandler {

    private final PortalAuthClient portalAuthClient;
    private final AuthSupport authSupport;
    private final Validator validator;

    public Mono<ServerResponse> login(ServerRequest request) {
        return request.bodyToMono(LoginDTO.class)
                .switchIfEmpty(Mono.error(new BizException(ErrorEnum.PARAM_INVALID)))
                .doOnNext(this::validate)
                .flatMap(portalAuthClient::authenticate)
                .map(authSupport::issueBundle)
                .flatMap(bundle -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(bundle.refreshCookie())
                        .bodyValue(R.ok(bundle.access())));
    }

    /**
     * 用 HttpOnly Refresh Cookie 换新 Access，并轮换 Refresh（旧 Refresh jti 拉黑）。
     */
    public Mono<ServerResponse> refresh(ServerRequest request) {
        String oldRefresh = authSupport.readRefreshCookie(request.exchange());
        return authSupport.requireRefreshUser(request)
                .flatMap(user -> authSupport.blacklistRefreshToken(oldRefresh).thenReturn(user))
                .map(authSupport::issueBundle)
                .flatMap(bundle -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(bundle.refreshCookie())
                        .bodyValue(R.ok(bundle.access())));
    }

    private void validate(LoginDTO dto) {
        Set<ConstraintViolation<LoginDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), violations.iterator().next().getMessage());
        }
    }
}
