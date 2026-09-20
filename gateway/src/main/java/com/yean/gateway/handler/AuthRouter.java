package com.yean.gateway.handler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class AuthRouter {

    @Bean
    public RouterFunction<ServerResponse> authRoutes(LoginHandler loginHandler, LogoutHandler logoutHandler) {
        return route(POST("/api/auth/login"), loginHandler::login)
                .andRoute(POST("/api/auth/refresh"), loginHandler::refresh)
                .andRoute(POST("/api/auth/logout"), logoutHandler::logout);
    }
}
