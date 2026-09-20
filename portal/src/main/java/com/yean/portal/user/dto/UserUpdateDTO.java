package com.yean.portal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "修改用户请求，空字段表示不修改")
public class UserUpdateDTO {

    @Schema(description = "新密码，为空则不修改；传入时会重新 BCrypt 加密", example = "123456")
    private String password;

    @Schema(description = "角色列表（全量覆盖）。为空则不改角色", example = "[\"MERCHANT\",\"USER\"]")
    private List<String> roles;

    @Schema(description = "所属部门 ID", example = "3")
    private Long deptId;

    @Schema(description = "账号状态：1 启用，0 禁用", example = "1")
    private Integer status;
}
