package com.yean.common.operator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OperatorProperties.class)
public class OperatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OperatorResolver operatorResolver(OperatorProperties properties) {
        return new OperatorResolver(properties);
    }
}
