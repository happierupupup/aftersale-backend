package com.yean.gateway.exception;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.net.ConnectException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 按异常类型填 status / code / msg，供 {@link GatewayErrorHandler} 写成统一 R。
 */
@Slf4j
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    public static final String STATUS = "status";
    public static final String CODE = "code";
    public static final String MSG = "msg";

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Mapped mapped = classify(getError(request));
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(STATUS, mapped.status.value());
        attrs.put(CODE, mapped.code);
        attrs.put(MSG, mapped.msg);
        return attrs;
    }

    private Mapped classify(Throwable ex) {
        BizException biz = find(ex, BizException.class);
        if (biz != null) {
            return new Mapped(statusOf(biz.getCode()), biz.getCode(), biz.getMessage());
        }
        if (ex instanceof WebExchangeBindException bind) {
            return new Mapped(HttpStatus.BAD_REQUEST, ErrorEnum.PARAM_INVALID.getCode(), fieldMessage(bind));
        }
        if (ex instanceof MethodArgumentNotValidException valid) {
            String msg = ErrorEnum.PARAM_INVALID.getMsg();
            if (valid.getBindingResult().getFieldError() != null) {
                msg = valid.getBindingResult().getFieldError().getDefaultMessage();
            }
            return new Mapped(HttpStatus.BAD_REQUEST, ErrorEnum.PARAM_INVALID.getCode(), msg);
        }
        if (ex instanceof BindException bind) {
            String msg = ErrorEnum.PARAM_INVALID.getMsg();
            if (bind.getFieldError() != null) {
                msg = bind.getFieldError().getDefaultMessage();
            }
            return new Mapped(HttpStatus.BAD_REQUEST, ErrorEnum.PARAM_INVALID.getCode(), msg);
        }
        if (ex instanceof ServerWebInputException || find(ex, DecodingException.class) != null) {
            return new Mapped(HttpStatus.BAD_REQUEST, ErrorEnum.PARAM_INVALID.getCode(), ErrorEnum.PARAM_INVALID.getMsg());
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = rse.getStatus();
            if (status == HttpStatus.NOT_FOUND) {
                return of(ErrorEnum.NOT_FOUND, status);
            }
            if (status == HttpStatus.UNAUTHORIZED) {
                return of(ErrorEnum.UNAUTHORIZED, status);
            }
            if (status.value() >= 500) {
                return of(ErrorEnum.GATEWAY_DOWN, HttpStatus.BAD_GATEWAY);
            }
            return new Mapped(status, status.value(),
                    rse.getReason() == null ? status.getReasonPhrase() : rse.getReason());
        }
        if (find(ex, TimeoutException.class) != null || find(ex, ConnectException.class) != null) {
            return of(ErrorEnum.GATEWAY_DOWN, HttpStatus.BAD_GATEWAY);
        }
        log.error("unhandled error", ex);
        return of(ErrorEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static Mapped of(ErrorEnum error, HttpStatus status) {
        return new Mapped(status, error.getCode(), error.getMsg());
    }

    private static String fieldMessage(WebExchangeBindException bind) {
        if (bind.getFieldError() != null && bind.getFieldError().getDefaultMessage() != null) {
            return bind.getFieldError().getDefaultMessage();
        }
        return ErrorEnum.PARAM_INVALID.getMsg();
    }

    private static HttpStatus statusOf(int code) {
        if (code == 401) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == 403) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == 502) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (code == 400) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.OK;
    }

    private static <T extends Throwable> T find(Throwable ex, Class<T> type) {
        Throwable current = ex;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    record Mapped(HttpStatus status, int code, String msg) {
    }
}
