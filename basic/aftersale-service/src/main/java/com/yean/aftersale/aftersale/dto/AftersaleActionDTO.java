package com.yean.aftersale.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "审核 / 驳回 / 撤回")
public class AftersaleActionDTO {

    @NotBlank(message = "售后单号不能为空")
    @Schema(description = "售后单号", example = "AS20260920000001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String aftersaleNo;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "仅本地直连 8082 填写")
    private Long userId;

    @Schema(description = "仅本地直连 8082 填写", example = "AAA代购")
    private String username;

    @Schema(description = "仅本地直连 8082 填写", example = "MERCHANT")
    private String role;
}
