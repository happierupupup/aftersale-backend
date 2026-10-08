package com.yean.trade.notify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yean.notify")
public class NotifyProperties {

    /**
     * 投递方式：
     * <ul>
     *   <li>{@code outbox}（默认）：业务事务内写 local_message，Sender→MQ→portal</li>
     *   <li>{@code sync}：请求内 Feign portal（§9.4 公平对照用，测完改回 outbox）</li>
     * </ul>
     */
    private String delivery = "outbox";

    /** portal 根地址，供 NotifyFeignClient 使用 */
    private String portalUrl = "http://127.0.0.1:8081";

    /** 演示用仓库账号 userId（待发货时通知） */
    private Long warehouseUserId = 3L;

    public boolean isSyncDelivery() {
        return "sync".equalsIgnoreCase(delivery);
    }
}
