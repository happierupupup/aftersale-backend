package com.yean.trade.message.service;

/**
 * 本地消息表（outbox）：业务同事务写入 NEW，Sender 扫表投递 MQ。
 */
public interface LocalMessageService {

    void insert(String bizId, String topic, String payload);

    /**
     * 扫 NEW 且到点的消息投递；成功 SENT，失败加次重试或超限 FAIL。
     *
     * @return 本轮成功投递条数；未抢到锁或未启用返回 0
     */
    int dispatchDue();
}
