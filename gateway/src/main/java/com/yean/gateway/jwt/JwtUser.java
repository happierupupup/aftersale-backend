package com.yean.gateway.jwt;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JwtUser {

    private Long userId;
    private String username;
    private String role;
    private String identityType;
    private Long deptId;
    private String jti;
}
