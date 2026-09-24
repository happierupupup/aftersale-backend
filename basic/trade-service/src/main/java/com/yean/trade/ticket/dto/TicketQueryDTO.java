package com.yean.trade.ticket.dto;

import com.yean.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "售后列表查询，条件均可选")
public class TicketQueryDTO extends PageQuery {

    @Schema(description = "售后状态：0待审核 1待仓库发货 2待用户收货 3待退款 4已办结 5已驳回 6已撤回")
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
