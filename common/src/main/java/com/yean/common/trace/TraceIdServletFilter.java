package com.yean.common.trace;

import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet 服务入口：读取或生成 {@link TraceConstants#HEADER}，写入 MDC，并回写响应头。
 */
public class TraceIdServletFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIds.resolveOrCreate(request.getHeader(TraceConstants.HEADER));
        TraceIds.put(traceId);
        response.setHeader(TraceConstants.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIds.clear();
        }
    }
}
