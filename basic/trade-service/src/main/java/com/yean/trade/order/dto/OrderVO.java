package com.yean.trade.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模拟订单视图（trade 内 order 域出参；售后申请校验复用）。 */
@Data
public class OrderVO {

    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Long skuId;
    private String skuName;
    private Integer num;
    private BigDecimal amount;
    /** 1待发货 2已发货 3已完成 4已关闭 */
    private Integer status;
    /** 0未支付 1已支付 */
    private Integer payStatus;
    private LocalDateTime createTime;
}
