package com.yean.portal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "权限校验请求")
public class PermissionCheckDTO {

    @NotNull(message = "用户 ID 不能为空")
    @Schema(description = "被校验的用户 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @NotBlank(message = "权限码不能为空")
    @Schema(description = "权限码，如 role:grant / aftersale:apply / aftersale:merchant:approve / aftersale:warehouse / aftersale:refund / user:manage / dept:manage / stock:manage",
            example = "aftersale:merchant:approve", requiredMode = Schema.RequiredMode.REQUIRED)
    private String permission;
}
