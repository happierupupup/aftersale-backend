package com.yean.trade.sku.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "SKU 档案（含当前可用库存）")
public class SkuVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "SKU ID，同一商品不同规格的业务标识，例如 1001=黑色耳机")
    private Long skuId;

    @Schema(description = "规格名称")
    private String skuName;

    @Schema(description = "可用库存")
    private Integer stockNum;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "预警阈值")
    private Integer warnThreshold;

    @Schema(description = "最后维护人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
