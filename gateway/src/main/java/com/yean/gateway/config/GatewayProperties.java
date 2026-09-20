package com.yean.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private String portalUrl = "http://127.0.0.1:8081";
    private Jwt jwt = new Jwt();
    private Auth auth = new Auth();

    @Data
    public static class Jwt {
        private String secret = "yean-aftersale-gateway-jwt-hs256-secret-key";
        private long expireSeconds = 7200;
    }

    @Data
    public static class Auth {
        private List<String> whitelist = new ArrayList<>();
    }
}
