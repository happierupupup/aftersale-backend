package com.yean.portal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "登录校验请求，只校验账号密码，不签发 Token")
public class AuthenticateDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "登录用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "明文密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
