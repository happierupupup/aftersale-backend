package com.yean.trade.message.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yean.trade.message.config.LocalMessageProperties;
import com.yean.trade.message.dto.LocalMessage;
import com.yean.trade.message.mapper.LocalMessageMapper;
import com.yean.trade.message.service.LocalMessageService;
import com.yean.trade.notify.dto.AftersaleNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalMessageServiceImpl implements LocalMessageService {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAIL = "FAIL";
    public static final String LOCK_KEY = "lock:schedule:local-message";

    private final LocalMessageMapper localMessageMapper;
    private final LocalMessageProperties properties;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    @Override
    public void insert(String bizId, String topic, String payload) {
        LocalMessage message = new LocalMessage();
        message.setBizId(bizId);
        message.setTopic(topic);
        message.setPayload(payload);
        message.setStatus(STATUS_NEW);
        message.setRetryCount(0);
        message.setNextRetryTime(LocalDateTime.now());
        try {
            localMessageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            log.info("local_message already exists, bizId={}", bizId);
        }
    }

    @Override
    public int dispatchDue() {
        if (!properties.isEnabled()) {
            log.info("local-message dispatch disabled");
            return 0;
        }
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.info("local-message dispatch skipped, lock held");
                return 0;
            }
            return doDispatch();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("local-message dispatch interrupted");
            return 0;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private int doDispatch() {
        LocalDateTime now = LocalDateTime.now();
        List<LocalMessage> rows = localMessageMapper.selectList(
                Wrappers.<LocalMessage>lambdaQuery()
                        .eq(LocalMessage::getStatus, STATUS_NEW)
                        .le(LocalMessage::getNextRetryTime, now)
                        .orderByAsc(LocalMessage::getId)
                        .last("LIMIT " + Math.max(1, properties.getScanLimit()))
        );
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (LocalMessage row : rows) {
            if (trySend(row)) {
                sent++;
            }
        }
        log.info("local-message dispatch finished, scanned={}, sent={}", rows.size(), sent);
        return sent;
    }

    private boolean trySend(LocalMessage row) {
        try {
            AftersaleNotifyMessage msg = objectMapper.readValue(row.getPayload(), AftersaleNotifyMessage.class);
            rocketMQTemplate.convertAndSend(row.getTopic(), msg);
            row.setStatus(STATUS_SENT);
            row.setUpdateTime(LocalDateTime.now());
            localMessageMapper.updateById(row);
            log.info("local_message sent, id={}, bizId={}", row.getId(), row.getBizId());
            return true;
        } catch (Exception e) {
            int nextRetry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
            row.setRetryCount(nextRetry);
            row.setUpdateTime(LocalDateTime.now());
            if (nextRetry >= properties.getMaxRetry()) {
                row.setStatus(STATUS_FAIL);
                log.error("local_message give up, id={}, bizId={}, err={}",
                        row.getId(), row.getBizId(), e.getMessage());
            } else {
                int delaySec = properties.getRetryBaseSeconds() * nextRetry;
                row.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySec));
                log.warn("local_message send failed, id={}, bizId={}, retry={}, next={}s, err={}",
                        row.getId(), row.getBizId(), nextRetry, delaySec, e.getMessage());
            }
            localMessageMapper.updateById(row);
            return false;
        }
    }
}
