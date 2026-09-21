package com.yean.aftersale.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "申请售后")
public class TicketApplyDTO {

    @NotBlank(message = "订单号不能为空")
    @Schema(description = "订单号", example = "ORD20250920001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderNo;

    @NotNull(message = "skuId 不能为空")
    @Schema(description = "SKU ID，须与订单一致", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long skuId;

    @NotNull(message = "售后类型不能为空")
    @Schema(description = "1退货 2换货 3仅退款", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer type;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    @Schema(description = "售后数量，不能超过购买数量", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer num;

    @Schema(description = "申请原因")
    private String reason;

    @Schema(description = "物流信息，可后补")
    private LogisticsInfo logisticsInfo;

    @Schema(description = "仅本地直连 8082 填写。正式经网关不要填")
    private Long userId;

    @Schema(description = "仅本地直连 8082 填写", example = "精神状态良好")
    private String username;

    @Schema(description = "仅本地直连 8082 填写", example = "USER")
    private String role;
}
