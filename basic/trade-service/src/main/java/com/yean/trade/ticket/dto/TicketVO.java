package com.yean.trade.ticket.dto;

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

    @Schema(description = "0待审核 1待仓库发货 2待用户收货 3待退款 4已办结 5已驳回 6已撤回")
    private Integer status;

    @Schema(description = "SKU ID（原订单商品）")
    private Long skuId;

    @Schema(description = "商品名称快照")
    private String skuName;

    @Schema(description = "换货目标 SKU；仅换货单有值")
    private Long exchangeSkuId;

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

    @Schema(description = "Activiti 流程实例 ID")
    private String processInstanceId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "详情接口才带操作日志")
    private List<TicketLogVO> logs = new ArrayList<>();
}
