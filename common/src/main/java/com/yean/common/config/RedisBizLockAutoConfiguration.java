package com.yean.common.config;

import com.yean.common.lock.RedisBizLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 须在 Redisson 自动配置之后执行。
 * 不要对 RedissonClient 使用 {@code @ConditionalOnBean}：auto-config 阶段该 Bean 往往尚未注册，条件会误判为 false。
 */
@Configuration
@ConditionalOnClass(RedissonClient.class)
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfiguration")
public class RedisBizLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisBizLock redisBizLock(RedissonClient redissonClient) {
        return new RedisBizLock(redissonClient);
    }
}
