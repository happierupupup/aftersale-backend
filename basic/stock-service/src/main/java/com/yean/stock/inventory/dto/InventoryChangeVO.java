package com.yean.stock.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "库存扣减 / 回退结果")
public class InventoryChangeVO {

    @Schema(description = "流水号")
    private String flowNo;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "本次变更数量")
    private Integer changeNum;

    @Schema(description = "DEDUCT / ROLLBACK")
    private String type;

    @Schema(description = "业务单号")
    private String bizNo;

    @Schema(description = "操作人")
    private String operator;

    @Schema(description = "变更后可用库存")
    private Integer stockNum;

    @Schema(description = "预警阈值")
    private Integer warnThreshold;

    @Schema(description = "true 表示命中幂等，库存未再次变更")
    private Boolean idempotent;
}
