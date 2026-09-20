package com.yean.aftersale.message.service;

public interface LocalMessageService {

    void insert(String bizId, String topic, String payload);
}
