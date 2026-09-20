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
                .map(authSupport::issue)
                .flatMap(vo -> json(R.ok(vo)));
    }

    public Mono<ServerResponse> refresh(ServerRequest request) {
        return authSupport.requireUser(request)
                .flatMap(user -> authSupport.addToBlacklist(user, request).thenReturn(authSupport.issue(user)))
                .flatMap(vo -> json(R.ok(vo)));
    }

    private void validate(LoginDTO dto) {
        Set<ConstraintViolation<LoginDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), violations.iterator().next().getMessage());
        }
    }

    static Mono<ServerResponse> json(Object body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }
}
