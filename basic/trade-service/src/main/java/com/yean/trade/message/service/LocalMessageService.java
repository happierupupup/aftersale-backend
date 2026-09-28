package com.yean.trade.message.service;

/**
 * 本地消息表（outbox）写入。阶段 8 基础版暂不走 outbox，由业务成功后直发 MQ；
 * 下一步再接 Sender 扫表投递以保证与业务同事务。
 */
public interface LocalMessageService {

    void insert(String bizId, String topic, String payload);
}
