package com.yean.stock.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "库存流水")
public class InventoryFlowVO {

    @Schema(description = "流水号")
    private String flowNo;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "变更数量")
    private Integer changeNum;

    @Schema(description = "DEDUCT / ROLLBACK")
    private String type;

    @Schema(description = "业务单号")
    private String bizNo;

    @Schema(description = "SUCCESS / FAIL")
    private String status;

    @Schema(description = "操作人")
    private String operator;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
