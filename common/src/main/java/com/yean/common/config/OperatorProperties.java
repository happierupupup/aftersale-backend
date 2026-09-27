package com.yean.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yean.operator")
public class OperatorProperties {

    /**
     * 仅本地调试：允许 body 带 userId / username / role。
     * 正式走网关时为 false，只信 X-User-* 头。
     */
    private boolean allowBody = false;
}
