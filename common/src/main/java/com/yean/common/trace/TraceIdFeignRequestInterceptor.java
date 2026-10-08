package com.yean.common.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Feign 出站携带当前 TraceId（优先 MDC，其次当前 HTTP 请求头）。
 */
public class TraceIdFeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceIds.current();
        if (traceId == null || traceId.isBlank()) {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                if (request != null) {
                    traceId = request.getHeader(TraceConstants.HEADER);
                }
            }
        }
        if (traceId != null && !traceId.isBlank()) {
            template.header(TraceConstants.HEADER, traceId);
        }
    }
}
