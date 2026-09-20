package com.yean.aftersale.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "退款明细")
public class RefundDetail {

    @Schema(description = "退款金额")
    private BigDecimal amount;

    @Schema(description = "退款渠道", example = "原路退回")
    private String channel;

    @Schema(description = "说明")
    private String remark;
}
