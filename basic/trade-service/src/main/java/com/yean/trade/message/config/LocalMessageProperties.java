package com.yean.trade.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yean.schedule.local-message")
public class LocalMessageProperties {

    private boolean enabled = true;

    /** 默认每 5 秒扫一次 */
    private String cron = "0/5 * * * * ?";

    private int scanLimit = 100;

    private int maxRetry = 16;

    /** 失败后下次重试基础间隔（秒），实际间隔 = base * (retryCount+1) */
    private int retryBaseSeconds = 5;
}
