package com.yean.aftersale.config;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class AftersaleLock {

    private static final String PREFIX = "lock:aftersale:";

    private final RedissonClient redissonClient;

    public <T> T execute(String aftersaleNo, Supplier<T> action) {
        RLock lock = redissonClient.getLock(PREFIX + aftersaleNo);
        lock.lock(60, TimeUnit.SECONDS);
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
