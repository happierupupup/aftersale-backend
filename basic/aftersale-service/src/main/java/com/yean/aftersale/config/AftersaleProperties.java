package com.yean.aftersale.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aftersale")
public class AftersaleProperties {

    /**
     * 仅本地调试：允许 body 带 userId / username / role。
     * 正式走网关时为 false，只信 X-User-* 头。
     */
    private boolean allowBodyOperator = false;
}
