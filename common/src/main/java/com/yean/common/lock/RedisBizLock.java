package com.yean.common.lock;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 业务锁。key 由调用方拼完整前缀，如 {@code lock:stock:1001}。
 */
@RequiredArgsConstructor
public class RedisBizLock {

    private final RedissonClient redissonClient;

    public <T> T execute(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(60, TimeUnit.SECONDS);
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public <T> T execute(String prefix, Object id, Supplier<T> action) {
        return execute(prefix + id, action);
    }
}
