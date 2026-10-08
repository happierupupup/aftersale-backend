package com.yean.common.config;

import com.yean.common.trace.TraceIdFeignRequestInterceptor;
import com.yean.common.trace.TraceIdServletFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * TraceId：Servlet 入口 Filter + Feign 透传（gateway 用独立 GlobalFilter）。
 *
 * 依赖可选包（feign / servlet-api）的 Bean 必须放进带「类级」@ConditionalOnClass 的嵌套配置类：
 * 类级条件基于 ASM 元数据判定，不触发类加载；若只在 @Bean 方法上加条件，其他配置的
 * {@code @ConditionalOnMissingBean} 内省会加载方法返回类型，缺依赖的服务启动即
 * NoClassDefFoundError（process/portal 无 feign，gateway 无 servlet-api）。
 */
@Configuration
public class TraceIdAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "javax.servlet.Filter")
    static class ServletTraceIdConfiguration {

        @Bean
        public FilterRegistrationBean<TraceIdServletFilter> traceIdServletFilter() {
            FilterRegistrationBean<TraceIdServletFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new TraceIdServletFilter());
            bean.setName("traceIdServletFilter");
            bean.addUrlPatterns("/*");
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignTraceIdConfiguration {

        @Bean
        @ConditionalOnMissingBean(TraceIdFeignRequestInterceptor.class)
        public TraceIdFeignRequestInterceptor traceIdFeignRequestInterceptor() {
            return new TraceIdFeignRequestInterceptor();
        }
    }
}
