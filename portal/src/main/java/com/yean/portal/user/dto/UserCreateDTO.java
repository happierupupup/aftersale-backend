package com.yean.portal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Schema(description = "新增用户请求")
public class UserCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "登录用户名，全局唯一", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "明文密码，入库前会做 BCrypt 加密", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotEmpty(message = "至少分配一个角色")
    @Schema(description = "角色列表，可多选：USER / MERCHANT / ADMIN / WAREHOUSE",
            example = "[\"USER\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> roles;

    @NotNull(message = "部门不能为空")
    @Schema(description = "所属部门 ID", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long deptId;
}
