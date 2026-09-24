package com.yean.trade.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "售后单操作：审核 / 驳回 / 撤回 / 发货 / 收货 / 退款")
public class TicketActionDTO {

    @NotBlank(message = "售后单号不能为空")
    @Schema(description = "售后单号", example = "AS20260920000001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String aftersaleNo;

    @Schema(description = "备注")
    private String remark;

    @Valid
    @Schema(description = "发货时必填：物流信息")
    private LogisticsInfo logisticsInfo;

    @Valid
    @Schema(description = "退款时可选：退款明细，缺省用售后金额")
    private RefundDetail refundDetail;

    @Schema(description = "仅本地直连 8082 填写")
    private Long userId;

    @Schema(description = "仅本地直连 8082 填写", example = "AAA代购")
    private String username;

    @Schema(description = "仅本地直连 8082 填写", example = "MERCHANT")
    private String role;
}
