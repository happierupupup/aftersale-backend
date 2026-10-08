package com.yean.gateway.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenVO {

    /** Access JWT，放 Authorization: Bearer；勿长期存 localStorage */
    private String token;

    private String tokenType;

    /** Access 有效期秒数 */
    private long expiresIn;

    private Long userId;

    private String username;

    private String role;
}
