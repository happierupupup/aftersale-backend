package com.yean.trade.sku.cache;

import com.yean.trade.config.SkuCacheProperties;
import com.yean.trade.sku.dto.SkuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkuCache {

    private static final String KEY_PREFIX = "stock:sku:";
    private static final long DELAY_DELETE_MS = 500L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SkuCacheProperties skuCacheProperties;
    private final ScheduledExecutorService delayExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sku-cache-delay-delete");
        t.setDaemon(true);
        return t;
    });

    public SkuVO get(Long skuId) {
        Object cached = redisTemplate.opsForValue().get(key(skuId));
        if (cached instanceof SkuVO vo) {
            return vo;
        }
        return null;
    }

    public void put(SkuVO vo) {
        if (vo == null || vo.getSkuId() == null) {
            return;
        }
        int ttl = skuCacheProperties.getCacheTtlSeconds() + ThreadLocalRandom.current().nextInt(300);
        redisTemplate.opsForValue().set(key(vo.getSkuId()), vo, Duration.ofSeconds(ttl));
    }

    public void evict(Long skuId) {
        redisTemplate.delete(key(skuId));
    }

    public void evictDelayed(Long skuId) {
        delayExecutor.schedule(() -> {
            try {
                evict(skuId);
            } catch (Exception e) {
                log.warn("delay delete sku cache failed, skuId={}", skuId, e);
            }
        }, DELAY_DELETE_MS, TimeUnit.MILLISECONDS);
    }

    private static String key(Long skuId) {
        return KEY_PREFIX + skuId;
    }

    @PreDestroy
    public void shutdown() {
        delayExecutor.shutdownNow();
    }
}
