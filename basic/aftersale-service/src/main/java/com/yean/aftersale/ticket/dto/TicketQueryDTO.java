package com.yean.aftersale.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Schema(description = "售后列表查询，条件均可选")
public class TicketQueryDTO {

    @Schema(description = "售后状态：0待审核 1审核通过 2待寄回 3待验货 4待退款 5已办结 6已驳回 7已撤回")
    private Integer status;

    @Schema(description = "1退货 2换货 3仅退款")
    private Integer type;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "售后单号")
    private String aftersaleNo;

    @Schema(description = "申请人用户 ID")
    private Long userId;

    @Schema(description = "商家用户 ID")
    private Long merchantId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "创建时间起")
    private LocalDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "创建时间止")
    private LocalDateTime endTime;
}
