package com.yean.gateway.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalAuthR {

    private int code;
    private String msg;
    private AuthUser data;
}
