package com.yean.trade.notify.dto;

import lombok.Data;

/**
 * 售后节点流转通知消息（Topic=aftersale_notify）。
 * 阶段 8 基础版：业务成功后直接发 MQ，尚未走 local_message outbox。
 */
@Data
public class AftersaleNotifyMessage {

    /** 幂等键：aftersaleNo:EVENT */
    private String bizId;
    private String aftersaleNo;
    /** APPLY / APPROVE / REJECT / ... */
    private String event;
    private Long userId;
    private Long merchantId;
    private Integer status;
    private Integer type;
    private String skuName;
}
