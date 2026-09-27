package com.yean.trade.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "库存扣减 / 回退请求")
public class InventoryChangeDTO {

    @NotNull(message = "skuId 不能为空")
    @Schema(description = "SKU ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long skuId;

    @NotNull(message = "变更数量不能为空")
    @Min(value = 1, message = "变更数量必须大于 0")
    @Schema(description = "变更数量，正整数", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer changeNum;

    @NotBlank(message = "业务单号不能为空")
    @Schema(description = "业务单号，与 type 组成幂等键，例如售后单号", example = "AS20260920000001",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String bizNo;

    @Schema(description = "仅本地直连 8082 调试时填写。正式经网关不要填，操作人取 X-Username", example = "00000")
    private String username;

    @Schema(description = "仅本地直连 8082 填写", example = "1")
    private Long userId;

    @Schema(description = "仅本地直连 8082 填写；须 WAREHOUSE 或 ADMIN", example = "WAREHOUSE")
    private String role;

    @Schema(description = "备注")
    private String remark;
}
