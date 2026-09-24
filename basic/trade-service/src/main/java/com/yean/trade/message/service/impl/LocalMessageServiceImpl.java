package com.yean.trade.message.service.impl;

import com.yean.trade.message.dto.LocalMessage;
import com.yean.trade.message.mapper.LocalMessageMapper;
import com.yean.trade.message.service.LocalMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LocalMessageServiceImpl implements LocalMessageService {

    public static final String STATUS_NEW = "NEW";
    public static final String TOPIC_NOTIFY = "aftersale_notify";

    private final LocalMessageMapper localMessageMapper;

    @Override
    public void insert(String bizId, String topic, String payload) {
        LocalMessage message = new LocalMessage();
        message.setBizId(bizId);
        message.setTopic(topic);
        message.setPayload(payload);
        message.setStatus(STATUS_NEW);
        message.setRetryCount(0);
        message.setNextRetryTime(LocalDateTime.now());
        localMessageMapper.insert(message);
    }
}
