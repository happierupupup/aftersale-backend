package com.yean.trade.notify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yean.notify")
public class NotifyProperties {

    /** portal 根地址，供 NotifyFeignClient 使用 */
    private String portalUrl = "http://127.0.0.1:8081";

    /** 演示用仓库账号 userId（待发货时通知） */
    private Long warehouseUserId = 3L;
}
