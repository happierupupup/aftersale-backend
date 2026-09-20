package com.yean.portal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Schema(description = "管理员给用户授权（全量覆盖角色）")
public class GrantRoleDTO {

    @NotNull(message = "操作人不能为空")
    @Schema(description = "操作人用户 ID，必须具备 role:grant 权限（ADMIN）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long operatorId;

    @NotEmpty(message = "至少保留一个角色")
    @Schema(description = "授权后的完整角色列表", example = "[\"MERCHANT\",\"USER\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> roles;
}
