package com.yean.stock.sku.dto;

import com.yean.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "SKU 列表查询，均为可选")
public class SkuQueryDTO extends PageQuery {

    @Schema(description = "SKU ID，精确匹配", example = "1001")
    private Long skuId;

    @Schema(description = "规格名称，模糊匹配", example = "耳机")
    private String skuName;
}
