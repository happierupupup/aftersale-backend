package com.yean.portal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息（不含密码）")
public class UserVO {

    @Schema(description = "用户 ID", example = "1")
    private Long id;

    @Schema(description = "登录用户名", example = "admin")
    private String username;

    @Schema(description = "主角色，取 roles 第一项，便于 JWT 透传", example = "ADMIN")
    private String role;

    @Schema(description = "全部角色", example = "[\"ADMIN\"]")
    private List<String> roles = new ArrayList<>();

    @Schema(description = "所属部门 ID", example = "1")
    private Long deptId;

    @Schema(description = "账号状态：1 启用，0 禁用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
