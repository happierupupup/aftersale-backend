package com.yean.portal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户列表查询条件，均为可选")
public class UserQueryDTO {

    @Schema(description = "用户名，模糊匹配", example = "admin")
    private String username;

    @Schema(description = "角色：USER / MERCHANT / ADMIN / WAREHOUSE / FINANCE", example = "ADMIN")
    private String role;

    @Schema(description = "所属部门 ID，精确匹配", example = "1")
    private Long deptId;

    @Schema(description = "账号状态：1 启用，0 禁用", example = "1")
    private Integer status;
}
