package com.yean.gateway.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登录/续期返回")
public class TokenVO {

    @Schema(description = "JWT")
    private String token;

    @Schema(description = "类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "有效期秒数")
    private long expiresIn;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "主角色")
    private String role;
}
