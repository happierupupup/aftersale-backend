package com.yean.common.trace;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * TraceId 生成、校验与 MDC 辅助（异步线程池可 {@link #wrap(Runnable)} 拷贝上下文）。
 */
public final class TraceIds {

    private static final int MAX_LEN = 64;

    private TraceIds() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 合法则原样返回，否则生成新 ID（防空/过长/奇怪字符）。
     */
    public static String resolveOrCreate(String incoming) {
        if (incoming == null) {
            return newId();
        }
        String t = incoming.trim();
        if (t.isEmpty() || t.length() > MAX_LEN || !t.chars().allMatch(TraceIds::allowed)) {
            return newId();
        }
        return t;
    }

    public static void put(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TraceConstants.MDC_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(TraceConstants.MDC_KEY);
    }

    public static String current() {
        return MDC.get(TraceConstants.MDC_KEY);
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (context != null) {
                MDC.setContextMap(context);
            } else {
                MDC.clear();
            }
            try {
                task.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (context != null) {
                MDC.setContextMap(context);
            } else {
                MDC.clear();
            }
            try {
                return task.call();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    private static boolean allowed(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '-' || c == '_';
    }
}
