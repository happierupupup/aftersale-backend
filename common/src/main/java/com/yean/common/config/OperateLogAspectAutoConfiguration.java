package com.yean.common.config;

import com.yean.common.dto.operatelog.OperateLogCreateDTO;
import com.yean.common.rpc.OperateLog;
import com.yean.common.rpc.OperateLogFeignClient;
import com.yean.common.trace.TraceIds;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * Controller {@link OperateLog} 切面自动配置：FutureTask 异步 Feign 写 portal。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.aspectj.lang.ProceedingJoinPoint",
        "org.springframework.cloud.openfeign.FeignClient"
})
public class OperateLogAspectAutoConfiguration {

    @Bean
    public OperateLogAspect operateLogAspect(ObjectProvider<OperateLogFeignClient> feignClientProvider) {
        return new OperateLogAspect(feignClientProvider);
    }

    @Slf4j
    @Aspect
    public static class OperateLogAspect {

        private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "operate-log-mvc");
            t.setDaemon(true);
            return t;
        });

        private final ObjectProvider<OperateLogFeignClient> feignClientProvider;

        public OperateLogAspect(ObjectProvider<OperateLogFeignClient> feignClientProvider) {
            this.feignClientProvider = feignClientProvider;
        }

        @Around("@annotation(operateLog)")
        public Object around(ProceedingJoinPoint pjp, OperateLog operateLog) throws Throwable {
            long start = System.currentTimeMillis();
            boolean success = true;
            String error = null;
            try {
                return pjp.proceed();
            } catch (Throwable ex) {
                success = false;
                error = ex.getMessage();
                throw ex;
            } finally {
                int cost = (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start);
                submit(buildDto(pjp, operateLog, success, cost, error));
            }
        }

        private void submit(OperateLogCreateDTO dto) {
            OperateLogFeignClient client = feignClientProvider.getIfAvailable();
            if (client == null || dto == null) {
                return;
            }
            FutureTask<Void> task = new FutureTask<>(TraceIds.wrap(() -> {
                try {
                    client.create(dto);
                } catch (Exception e) {
                    log.warn("async operate log failed, module={}, action={}, err={}",
                            dto.getModule(), dto.getAction(), e.getMessage());
                }
                return null;
            }));
            EXECUTOR.execute(task);
        }

        private OperateLogCreateDTO buildDto(ProceedingJoinPoint pjp, OperateLog operateLog,
                                             boolean success, int costMs, String error) {
            OperateLogCreateDTO dto = new OperateLogCreateDTO();
            dto.setModule(operateLog.module());
            dto.setAction(operateLog.action());
            dto.setSuccess(success);
            dto.setCostMs(costMs);
            String remark = operateLog.remark();
            if (error != null) {
                remark = (remark == null || remark.isEmpty() ? "" : remark + "; ") + error;
            }
            if (remark != null && remark.length() > 500) {
                remark = remark.substring(0, 500);
            }
            dto.setRemark(remark);

            MethodSignature signature = (MethodSignature) pjp.getSignature();
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                dto.setMethod(request.getMethod());
                dto.setPath(request.getRequestURI());
                dto.setOperatorId(parseLong(request.getHeader("X-User-Id")));
                dto.setOperatorName(request.getHeader("X-Username"));
                dto.setOperatorRole(request.getHeader("X-Role"));
            } else {
                dto.setPath(signature.getDeclaringType().getSimpleName() + "#" + signature.getName());
            }
            return dto;
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
    }
}
