package com.yean.trade.message.service;

public interface LocalMessageService {

    void insert(String bizId, String topic, String payload);
}
