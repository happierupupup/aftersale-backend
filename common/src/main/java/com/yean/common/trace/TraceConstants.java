package com.yean.common.trace;

/**
 * 全链路 TraceId：HTTP Header + MDC 键名。
 */
public final class TraceConstants {

    /** 请求/响应头 */
    public static final String HEADER = "X-Trace-Id";

    /** 日志 MDC / Reactor Context 键 */
    public static final String MDC_KEY = "traceId";

    private TraceConstants() {
    }
}
