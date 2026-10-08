package com.yean.common.config;

import com.yean.common.trace.TraceIdFeignRequestInterceptor;
import com.yean.common.trace.TraceIdServletFilter;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * TraceId：Servlet 入口 Filter + Feign 透传（gateway 用独立 GlobalFilter）。
 */
@Configuration
public class TraceIdAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "javax.servlet.Filter")
    public FilterRegistrationBean<TraceIdServletFilter> traceIdServletFilter() {
        FilterRegistrationBean<TraceIdServletFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TraceIdServletFilter());
        bean.setName("traceIdServletFilter");
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    @ConditionalOnClass(RequestInterceptor.class)
    @ConditionalOnMissingBean(TraceIdFeignRequestInterceptor.class)
    public TraceIdFeignRequestInterceptor traceIdFeignRequestInterceptor() {
        return new TraceIdFeignRequestInterceptor();
    }
}
