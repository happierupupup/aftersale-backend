package com.yean.stock.inventory.dto;

import com.yean.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "库存流水查询，均为可选")
public class InventoryFlowQueryDTO extends PageQuery {

    @Schema(description = "SKU ID", example = "1001")
    private Long skuId;

    @Schema(description = "业务单号", example = "AS-DEMO-001")
    private String bizNo;

    @Schema(description = "DEDUCT / ROLLBACK", example = "DEDUCT")
    private String type;
}
