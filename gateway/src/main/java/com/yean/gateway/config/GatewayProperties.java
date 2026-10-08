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
        private String secret = "gateway:jwt:secret-key";
        private long expireSeconds = 7200;
    }

    @Data
    public static class Auth {
        private List<String> whitelist = new ArrayList<>();
        /** 路径级角色 ACL；未命中规则默认放行，命中则角色须在列表中（ADMIN 恒通） */
        private Acl acl = new Acl();
    }

    @Data
    public static class Acl {
        private boolean enabled = true;
        private List<AclRule> rules = new ArrayList<>();
    }

    @Data
    public static class AclRule {
        /** Ant 路径，如 /stock/deduct、/aftersale/** */
        private String path;
        /** 空 = 所有 HTTP 方法 */
        private List<String> methods = new ArrayList<>();
        /** 允许的角色；ADMIN 在 Filter 内额外放行 */
        private List<String> roles = new ArrayList<>();
    }
}
