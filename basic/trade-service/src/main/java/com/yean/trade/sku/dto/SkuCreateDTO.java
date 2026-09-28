package com.yean.trade.sku.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "新增 SKU 档案")
public class SkuCreateDTO {

    @NotNull(message = "skuId 不能为空")
    @Schema(description = "业务 SKU ID，全局唯一。SKU=库存最小售卖单元，同一 SPU 下不同颜色/规格各一个 skuId",
            example = "2001", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long skuId;

    @NotBlank(message = "商品名称不能为空")
    @Schema(description = "规格名称", example = "演示商品-黑色", requiredMode = Schema.RequiredMode.REQUIRED)
    private String skuName;

    @NotNull(message = "商家不能为空")
    @Schema(description = "归属商家用户 ID（sys_user.id）", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long merchantId;

    @NotNull(message = "初始库存不能为空")
    @Min(value = 0, message = "初始库存不能为负")
    @Schema(description = "初始可用库存", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stockNum;

    @Min(value = 0, message = "预警阈值不能为负")
    @Schema(description = "预警阈值，不填默认 10", example = "10")
    private Integer warnThreshold;

    @Schema(description = "仅本地直连 8082 调试时填写。正式经网关不要填", example = "00000")
    private String username;
}
