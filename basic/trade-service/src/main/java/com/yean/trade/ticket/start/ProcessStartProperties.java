package com.yean.trade.ticket.start;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yean.schedule.process-start")
public class ProcessStartProperties {

    private boolean enabled = true;

    /** 默认每 2 秒扫一次 */
    private String cron = "0/2 * * * * ?";

    private int scanLimit = 50;

    private int maxRetry = 32;

    /** 失败后下次重试基础间隔（秒），实际 = base * retryCount */
    private int retryBaseSeconds = 2;
}
