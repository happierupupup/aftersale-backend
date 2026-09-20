package com.yean.aftersale.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "物流信息")
public class LogisticsInfo {

    @Schema(description = "快递公司", example = "顺丰")
    private String company;

    @Schema(description = "运单号", example = "SF1234567890")
    private String trackingNo;

    @Schema(description = "联系电话")
    private String phone;
}
