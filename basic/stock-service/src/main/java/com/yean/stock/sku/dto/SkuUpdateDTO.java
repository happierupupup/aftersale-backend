package com.yean.stock.sku.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;

@Data
@Schema(description = "修改 SKU 档案，不改可用库存（库存变动走扣减/回退）")
public class SkuUpdateDTO {

    @Schema(description = "规格名称")
    private String skuName;

    @Min(value = 0, message = "预警阈值不能为负")
    @Schema(description = "预警阈值")
    private Integer warnThreshold;

    @Schema(description = "仅本地直连 8083 调试时填写。正式经网关不要填", example = "00000")
    private String username;
}
