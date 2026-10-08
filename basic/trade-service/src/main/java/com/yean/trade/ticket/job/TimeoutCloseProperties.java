package com.yean.trade.ticket.job;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yean.schedule.timeout-close")
public class TimeoutCloseProperties {

    private boolean enabled = true;

    /** 默认每天 2:30（Asia/Shanghai） */
    private String cron = "0 30 2 * * ?";

    /** 工单产生超过该小时数未办结则自动关闭（默认 48h） */
    private int timeoutHours = 48;

    private int scanLimit = 200;
}
