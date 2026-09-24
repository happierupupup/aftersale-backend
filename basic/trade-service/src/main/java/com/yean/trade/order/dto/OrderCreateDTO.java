package com.yean.trade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Schema(description = "新增模拟订单")
public class OrderCreateDTO {

    @Schema(description = "订单号，不填则自动生成 ORD+日期+序号", example = "ORD20260924001")
    private String orderNo;

    @Schema(description = "下单用户 ID；不填则取当前登录用户", example = "5")
    private Long userId;

    @Schema(description = "商家用户 ID，不填默认 2", example = "2")
    private Long merchantId;

    @NotNull(message = "skuId 不能为空")
    @Schema(description = "SKU ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long skuId;

    @NotBlank(message = "商品名称不能为空")
    @Schema(description = "商品名称快照", example = "无线蓝牙耳机", requiredMode = Schema.RequiredMode.REQUIRED)
    private String skuName;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    @Schema(description = "购买数量", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer num;

    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额须大于 0")
    @Schema(description = "订单金额", example = "199.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @Schema(description = "1待发货 2已发货 3已完成 4已关闭；不填默认 1", example = "1")
    private Integer status;

    @Schema(description = "0未支付 1已支付；不填默认 1（便于演示售后）", example = "1")
    private Integer payStatus;
}
