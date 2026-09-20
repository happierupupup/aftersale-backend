package com.yean.portal.config;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RedisLock {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(10);
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final StringRedisTemplate stringRedisTemplate;

    public <T> T execute(String key, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, DEFAULT_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ErrorEnum.CONFLICT);
        }
        try {
            return action.get();
        } finally {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
            stringRedisTemplate.execute(script, Collections.singletonList(key), token);
        }
    }

    public void execute(String key, Runnable action) {
        execute(key, () -> {
            action.run();
            return null;
        });
    }
}
