package com.yean.aftersale.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "售后单")
public class TicketVO {

    @Schema(description = "售后单号")
    private String aftersaleNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "申请人用户 ID")
    private Long userId;

    @Schema(description = "商家用户 ID")
    private Long merchantId;

    @Schema(description = "1退货 2换货 3仅退款")
    private Integer type;

    @Schema(description = "0待审核 1审核通过 2待寄回 3待验货 4待退款 5已办结 6已驳回 7已撤回")
    private Integer status;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "商品名称快照")
    private String skuName;

    @Schema(description = "售后数量")
    private Integer num;

    @Schema(description = "涉及金额，部分售后按数量比例折算")
    private BigDecimal amount;

    @Schema(description = "申请原因")
    private String reason;

    @Schema(description = "物流信息")
    private LogisticsInfo logisticsInfo;

    @Schema(description = "退款明细")
    private RefundDetail refundDetail;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "详情接口才带操作日志")
    private List<TicketLogVO> logs = new ArrayList<>();
}
