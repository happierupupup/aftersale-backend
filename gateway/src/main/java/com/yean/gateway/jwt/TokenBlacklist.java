package com.yean.gateway.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final ReactiveStringRedisTemplate redis;

    public Mono<Void> add(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Mono.empty();
        }
        return redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl).then();
    }

    public Mono<Boolean> contains(String jti) {
        if (jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        return redis.hasKey(KEY_PREFIX + jti).defaultIfEmpty(false);
    }
}
