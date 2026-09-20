package com.yean.portal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "权限校验结果")
public class PermissionCheckVO {

    @Schema(description = "被校验用户 ID", example = "2")
    private Long userId;

    @Schema(description = "权限码", example = "aftersale:merchant:approve")
    private String permission;

    @Schema(description = "是否具备该权限", example = "true")
    private Boolean allowed;

    @Schema(description = "用户当前角色", example = "[\"MERCHANT\"]")
    private List<String> roles;
}
