package com.yean.gateway.exception;

import com.yean.common.result.R;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 只负责写出 JSON；错误分类在 {@link GatewayErrorAttributes}。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayErrorHandler extends AbstractErrorWebExceptionHandler {

    public GatewayErrorHandler(ErrorAttributes errorAttributes,
                               WebProperties webProperties,
                               ApplicationContext applicationContext,
                               ServerCodecConfigurer codecConfigurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageWriters(codecConfigurer.getWriters());
        setMessageReaders(codecConfigurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::render);
    }

    private Mono<ServerResponse> render(ServerRequest request) {
        Map<String, Object> attrs = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int status = (Integer) attrs.getOrDefault(GatewayErrorAttributes.STATUS, 500);
        int code = (Integer) attrs.getOrDefault(GatewayErrorAttributes.CODE, 500);
        String msg = String.valueOf(attrs.getOrDefault(GatewayErrorAttributes.MSG, "系统异常"));
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(R.fail(code, msg));
    }
}
