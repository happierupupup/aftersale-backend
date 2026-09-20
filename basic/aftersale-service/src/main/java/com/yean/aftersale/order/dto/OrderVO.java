package com.yean.aftersale.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "模拟订单")
public class OrderVO {

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "下单用户 ID")
    private Long userId;

    @Schema(description = "商家用户 ID")
    private Long merchantId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "商品名称快照")
    private String skuName;

    @Schema(description = "购买数量")
    private Integer num;

    @Schema(description = "订单金额")
    private BigDecimal amount;

    @Schema(description = "1待发货 2已发货 3已完成 4已关闭")
    private Integer status;

    @Schema(description = "0未支付 1已支付")
    private Integer payStatus;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;
}
