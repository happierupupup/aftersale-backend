package com.yean.trade.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 保存草稿：允许只填部分字段；校验弱于正式申请。
 * 前端「保存」走本 DTO；「提交」走 {@link TicketApplyDTO}（会先删同单草稿再新增正式单）。
 */
@Data
@Schema(description = "保存售后草稿（部分字段可空）")
public class TicketDraftDTO {

    @NotBlank(message = "订单号不能为空")
    @Schema(description = "订单号", example = "ORD20250920001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderNo;

    @NotNull(message = "skuId 不能为空")
    @Schema(description = "SKU ID，须与订单一致", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long skuId;

    @Schema(description = "1退货 2换货 3仅退款；未填默认 1", example = "1")
    private Integer type;

    @Schema(description = "换货目标 SKU；草稿阶段可不填")
    private Long exchangeSkuId;

    @Min(value = 1, message = "数量必须大于 0")
    @Schema(description = "售后数量；未填默认 1", example = "1")
    private Integer num;

    @Schema(description = "申请原因，可空")
    private String reason;

    @Schema(description = "仅本地直连 8082 填写。正式经网关不要填")
    private Long userId;

    @Schema(description = "仅本地直连 8082 填写", example = "精神状态良好")
    private String username;

    @Schema(description = "仅本地直连 8082 填写", example = "USER")
    private String role;
}
