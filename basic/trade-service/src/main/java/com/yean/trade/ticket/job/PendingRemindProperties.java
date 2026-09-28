package com.yean.trade.ticket.job;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yean.schedule.pending-remind")
public class PendingRemindProperties {

    /** 关闭后定时与手动接口都不再发信（手动接口仍返回 skipped） */
    private boolean enabled = true;

    /** 默认每天 10:00（Asia/Shanghai） */
    private String cron = "0 0 10 * * ?";

    /** 单次扫描上限，避免一次扫全表 */
    private int scanLimit = 1000;

    /** 站内信正文最多列出几笔单号 */
    private int previewLimit = 5;
}
