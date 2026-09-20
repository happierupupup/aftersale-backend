package com.yean.gateway.auth;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.gateway.auth.dto.AuthUser;
import com.yean.gateway.auth.dto.LoginDTO;
import com.yean.gateway.auth.dto.PortalAuthR;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class PortalAuthClient {

    private final WebClient portalWebClient;

    public Mono<AuthUser> authenticate(LoginDTO dto) {
        return portalWebClient.post()
                .uri("/api/user/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(PortalAuthR.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorMap(ex -> new BizException(ErrorEnum.GATEWAY_DOWN))
                .flatMap(resp -> {
                    if (resp.getCode() != 0 || resp.getData() == null) {
                        int code = resp.getCode() == 0 ? ErrorEnum.USER_OR_PASSWORD_ERROR.getCode() : resp.getCode();
                        String msg = resp.getMsg() == null ? ErrorEnum.USER_OR_PASSWORD_ERROR.getMsg() : resp.getMsg();
                        return Mono.error(new BizException(code, msg));
                    }
                    return Mono.just(resp.getData());
                });
    }
}
