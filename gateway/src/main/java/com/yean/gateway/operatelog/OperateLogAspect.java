package com.yean.gateway.operatelog;

import com.yean.common.dto.operatelog.OperateLogCreateDTO;
import com.yean.common.rpc.OperateLogFeignClient;
import com.yean.common.trace.TraceConstants;
import com.yean.common.trace.TraceIds;
import com.yean.gateway.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * 网关侧操作日志「切面」：请求结束后 FutureTask + OpenFeign 异步写入 portal.operatelog。
 * <p>
 * Gateway 为 WebFlux，无法织入下游 Controller；入口统一用 GlobalFilter 等价拦截。
 * Controller 上的 {@code @OperateLog} 由各 Servlet 服务内的 MVC 切面处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperateLogAspect implements GlobalFilter, Ordered {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "operate-log-gateway");
        t.setDaemon(true);
        return t;
    });

    private final OperateLogFeignClient operateLogFeignClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || skip(request.getPath().value())) {
            return chain.filter(exchange);
        }
        long start = System.currentTimeMillis();
        String path = request.getPath().value();
        String method = request.getMethodValue();
        String traceId = request.getHeaders().getFirst(TraceConstants.HEADER);
        Long userId = parseLong(request.getHeaders().getFirst(JwtService.HEADER_USER_ID));
        String username = request.getHeaders().getFirst(JwtService.HEADER_USERNAME);
        String role = request.getHeaders().getFirst(JwtService.HEADER_ROLE);

        return chain.filter(exchange).doOnSuccess(v ->
                submit(traceId, build(path, method, userId, username, role, true, start, null))
        ).doOnError(err ->
                submit(traceId, build(path, method, userId, username, role, false, start, err.getMessage()))
        );
    }

    private void submit(String traceId, OperateLogCreateDTO dto) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            if (traceId != null && !traceId.isBlank()) {
                TraceIds.put(traceId);
            }
            try {
                operateLogFeignClient.create(dto);
            } catch (Exception e) {
                log.warn("gateway async operate log failed, path={}, err={}", dto.getPath(), e.getMessage());
            } finally {
                TraceIds.clear();
            }
            return null;
        });
        EXECUTOR.execute(task);
    }

    private static OperateLogCreateDTO build(String path, String method, Long userId, String username,
                                             String role, boolean success, long start, String error) {
        OperateLogCreateDTO dto = new OperateLogCreateDTO();
        dto.setModule(resolveModule(path));
        dto.setAction(method + " " + path);
        dto.setMethod(method);
        dto.setPath(path);
        dto.setOperatorId(userId);
        dto.setOperatorName(username);
        dto.setOperatorRole(role);
        dto.setSuccess(success);
        dto.setCostMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start));
        if (error != null) {
            dto.setRemark(error.length() > 500 ? error.substring(0, 500) : error);
        }
        return dto;
    }

    private static String resolveModule(String path) {
        if (path == null) {
            return "unknown";
        }
        if (path.startsWith("/user") || path.startsWith("/dept") || path.startsWith("/notify")) {
            return "portal";
        }
        if (path.startsWith("/stock") || path.startsWith("/sku")) {
            return "stock";
        }
        if (path.startsWith("/aftersale")) {
            return "aftersale";
        }
        if (path.startsWith("/order")) {
            return "order";
        }
        if (path.startsWith("/api/auth")) {
            return "auth";
        }
        return "gateway";
    }

    private static boolean skip(String path) {
        return path == null
                || path.startsWith("/operatelog")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars");
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int getOrder() {
        // 在鉴权之后：此时已有 X-User-*（AuthGlobalFilter order=-100）
        return -50;
    }
}
