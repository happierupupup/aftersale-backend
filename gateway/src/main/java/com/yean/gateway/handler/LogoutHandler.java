package com.yean.gateway.handler;

import com.yean.common.result.R;
import com.yean.gateway.auth.AuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class LogoutHandler {

    private final AuthSupport authSupport;

    public Mono<ServerResponse> logout(ServerRequest request) {
        return Mono.when(
                        authSupport.blacklistAccess(request),
                        authSupport.blacklistRefresh(request)
                )
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(authSupport.clearRefreshCookie())
                        .bodyValue(R.ok()));
    }
}
