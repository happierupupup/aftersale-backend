package com.yean.trade.notify.dto;

import lombok.Data;

/**
 * 售后节点流转通知消息（Topic=aftersale_notify）。
 * payload 写入 local_message，由 Sender 投递 MQ。
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
