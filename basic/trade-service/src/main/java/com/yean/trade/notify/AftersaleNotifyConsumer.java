package com.yean.trade.notify;

import com.yean.trade.notify.dto.AftersaleNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 消费售后通知 → portal 站内信。Redis 按 bizId 粗幂等，避免重复消费刷多条。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = AftersaleNotifyPublisher.TOPIC,
        consumerGroup = "aftersale-notify-consumer"
)
public class AftersaleNotifyConsumer implements RocketMQListener<AftersaleNotifyMessage> {

    private static final Duration DEDUP_TTL = Duration.ofDays(7);
    private static final String DEDUP_PREFIX = "notify:dedup:";

    private final AftersaleNotifySender aftersaleNotifySender;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(AftersaleNotifyMessage message) {
        if (message == null || !StringUtils.hasText(message.getBizId())) {
            log.warn("skip empty aftersale notify message");
            return;
        }
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(DEDUP_PREFIX + message.getBizId(), "1", DEDUP_TTL);
        if (Boolean.FALSE.equals(first)) {
            log.info("aftersale notify duplicated, skip bizId={}", message.getBizId());
            return;
        }
        try {
            aftersaleNotifySender.sendToPortal(message);
            log.info("aftersale notify consumed, bizId={}", message.getBizId());
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(DEDUP_PREFIX + message.getBizId());
            throw e;
        }
    }
}
