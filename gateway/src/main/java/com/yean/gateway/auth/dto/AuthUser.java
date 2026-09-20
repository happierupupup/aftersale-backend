package com.yean.gateway.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthUser {

    private Long id;
    private String username;
    private String role;
    private Long deptId;
    private Integer status;
}
